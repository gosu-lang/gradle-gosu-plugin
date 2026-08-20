package org.gosulang.gradle.tasks.compile;

import groovy.lang.Closure;
import org.gosulang.gradle.tasks.GosuSourceExtensions;
import org.gosulang.gradle.tasks.InfersGosuRuntime;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.work.Incremental;
import org.gradle.work.InputChanges;
import org.gradle.work.FileChange;
import org.gradle.work.ChangeType;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.api.tasks.PathSensitivity.RELATIVE;

@CacheableTask
public abstract class GosuCompile extends AbstractCompile implements InfersGosuRuntime {

  private GosuCompiler<GosuCompileSpec> _compiler;
  private ConfigurableFileCollection gosuClasspath;
  private ConfigurableFileCollection _sourceRoots;
  private FileCollection _stableSources;
  private CompileOptions _compileOptions;
  private final GosuCompileOptions _gosuCompileOptions = new GosuCompileOptions();
  private Closure<FileCollection> _orderClasspath;

  @Inject
  protected abstract ObjectFactory getObjectFactory();

  @Inject
  protected abstract ExecOperations getExecOperations();

  // Track compileJava output directory for fine-grained Java class change detection
  // This will be configured by GosuBasePlugin using the SourceSet's Java output directory
  private FileCollection javaClassesDir;

  @Inject
  protected abstract ProjectLayout getLayout();

  @Inject
  protected abstract ProviderFactory getProviderFactory();

  @TaskAction
  protected void compile(InputChanges inputChanges) {
    DefaultGosuCompileSpec spec = createSpec();

    // Everything below only feeds gosuc's -changed-types/-removed-types/-local-java-types,
    // which CommandLineGosuCompiler emits solely when incrementalCompilation is on.
    if (getGosuOptions().isIncrementalCompilation()) {
      if (!getGosuOptions().isFork()) {
        throw new GradleException("gosuOptions.incrementalCompilation requires gosuOptions.fork = true,"
                                  + " but fork is false for " + getPath() + ". The in-process Gosu compiler has no incremental"
                                  + " support");
      }

      // Nothing to tell gosuc in this branch. Gradle deletes a task's declared outputs whenever it
      // cannot supply per-file changes -- exactly the case here -- so the dep file is already gone
      // by the time gosuc runs, and its absence is what gosuc reads as "compile everything".
      if (!inputChanges.isIncremental()) {
        getLogger().info("Gosu full recompilation is required");
      } else {
        getLogger().info("Gosu incremental compilation started");
        Set<String> changedTypes = new HashSet<>();
        Set<String> removedTypes = new HashSet<>();

        collectFQCNs( inputChanges, changedTypes, removedTypes);
        spec.setChangedTypes(changedTypes);
        spec.setRemovedTypes(removedTypes);
      }
      // Extract local Java type FQCNs for selective dependency tracking
      // This allows gosuc to distinguish same-module Java types from JRE/JAR types
      Set<String> localJavaTypes = extractLocalJavaTypeFQCNs();
      spec.setLocalJavaTypes(localJavaTypes);
    }

    _compiler = getCompiler(spec);
    _compiler.execute(spec);
  }

  /**
   * Collects the FQCNs of modified types into {@code changedTypes} and
   * {@code removedTypes}, covering both Gosu sources and the
   * {@code compileJava} output directory.
   *
   * <p>Fails the build as soon as a change cannot be mapped to an FQCN. Dropping it is unsafe:
   * any other change would still drive a narrow incremental compile, and the unnamed type's
   * stale output would survive.
   *
   * @param inputChanges the task's change set
   * @param changedTypes out-param collecting FQCNs of added/modified types
   * @param removedTypes out-param collecting FQCNs of removed types
   * @throws GradleException if any change cannot be mapped to an FQCN
   */
  private void collectFQCNs( InputChanges inputChanges, Set<String> changedTypes, Set<String> removedTypes) {
    // Extract FQCNs from changed Gosu source files
    for (FileChange change : inputChanges.getFileChanges(getStableSources())) {
      File file = change.getFile();
      if (GosuSourceExtensions.isGosuSourceFile(file.getName())) {
        String fqcn = extractFQCNFromSourceFile(file);
        if (fqcn == null) {
          throw new GradleException("Cannot determine the FQCN of changed Gosu source "
            + file.getAbsolutePath() + ": it lives under none of the task's source roots "
            + getSourceRoots().getFiles() + ", so it cannot be named in -changed-types/-removed-types.");
        }
        if (change.getChangeType() == ChangeType.REMOVED) {
          removedTypes.add(fqcn);
          getLogger().info("Gosu type removed: {}", fqcn);
        } else {
          changedTypes.add(fqcn);
          getLogger().info("Gosu type changed: {}", fqcn);
        }
      }
    }

    // Extract FQCNs from changed Java class files (if configured).
    // This provides fine-grained tracking for project Java sources (not JARs).
    //
    // Note: editing a single Java source can produce multiple FileChange
    // events. Modifying a nested class inside Outer.java rewrites both
    // Outer.class and Outer$Inner.class, each surfaced as its own change
    // here. Each maps to a distinct FQCN ("com.example.Outer" and
    // "com.example.Outer$Inner") and ends up in changedTypes.
    if (getJavaClassesDir() != null && !getJavaClassesDir().isEmpty()) {
      File javaClassesRoot = getJavaClassesDir().getSingleFile();
      for (FileChange change : inputChanges.getFileChanges(getJavaClassesDir())) {
        File classFile = change.getFile();
        if (classFile.getName().endsWith(".class")) {
          String fqcn = extractFQCNFromClassFile(classFile, javaClassesRoot);
          if (fqcn == null) {
            throw new GradleException("Cannot determine the FQCN of changed Java class "
              + classFile.getAbsolutePath() + ": it does not live under "
              + javaClassesRoot.getAbsolutePath() + ".");
          }
          if (change.getChangeType() == ChangeType.REMOVED) {
            removedTypes.add(fqcn);
            getLogger().info("Java type removed: {}", fqcn);
          } else {
            changedTypes.add(fqcn);
            getLogger().info("Java type changed: {}", fqcn);
          }
        }
      }
    }
  }

  /**
   * Extracts the fully-qualified class name from a Gosu source file by finding its source root.
   *
   * <p>Uses {@link Path#startsWith(Path)} on normalised absolute paths so the under-root check
   * is path-component-aware (not a brittle string-prefix match -- a sibling directory like
   * {@code .../main2/} would not be misidentified as living under {@code .../main/}).
   *
   * @param sourceFile a Gosu source file (any extension in {@link GosuSourceExtensions#ALL_EXTS})
   * @return the FQCN (e.g., "com.example.MyClass") or null if extraction fails
   */
  private String extractFQCNFromSourceFile(File sourceFile) {
    Path sourcePath = sourceFile.toPath().toAbsolutePath().normalize();
    FileCollection sourceRoots = getSourceRoots();

    for (File sourceRoot : sourceRoots.getFiles()) {
      Path rootPath = sourceRoot.toPath().toAbsolutePath().normalize();
      if (sourcePath.startsWith(rootPath)) {
        // Get relative path from root and convert separators to dots
        // e.g. com/example/MyClass.gs -> com.example.MyClass
        // or rules/EventMessage/MyRule.gr -> rules.EventMessage.MyRule.gr
        String fqcn = rootPath.relativize(sourcePath).toString()
          .replace(File.separator, ".");

        // Strip Gosu extension to get the bare FQCN
        return GosuSourceExtensions.stripExtension(fqcn);
      }
    }

    // Could not find source root
    return null;
  }

  /**
   * Extracts the fully-qualified class name from a Java {@code .class} file by computing
   * its path relative to the Java classes directory.
   *
   * @param classFile the .class file
   * @param javaClassesRoot the root directory (e.g., build/classes/java/main)
   * @return the FQCN (e.g., "com.example.MyClass" or "com.example.Outer$Inner") or null
   *         if extraction fails
   */
  private String extractFQCNFromClassFile(File classFile, File javaClassesRoot) {
    Path rootPath = javaClassesRoot.toPath().toAbsolutePath().normalize();
    Path classFilePath = classFile.toPath().toAbsolutePath().normalize();
    if (!classFilePath.startsWith(rootPath)) {
      return null;
    }

    String relativePath = rootPath.relativize(classFilePath).toString();
    if (!relativePath.endsWith(".class")) {
      return null;
    }
    // Strip the .class suffix before replacing separators so a path
    // component like "foo.class" (unusual but legal) can't be mistaken
    // for the file suffix.
    String fqcn = relativePath.substring(0, relativePath.length() - ".class".length())
            .replace(File.separator, ".");
    return fqcn.isEmpty() ? null : fqcn;
  }

  /**
   * Extract all FQCNs from javaClassesDir for local Java type tracking.
   * gosuc needs this to distinguish same-module Java types (track) from JRE/JAR types (skip).
   *
   * <p>An empty set is returned only when the source set genuinely has no Java output. An
   * output directory that exists but cannot be read is an error, not an empty set.
   *
   * @return Set of FQCNs for all Java classes in the javaClassesDir
   * @throws GradleException if javaClassesDir exists but is not a readable directory tree
   */
  private Set<String> extractLocalJavaTypeFQCNs() {
    Set<String> localTypes = new HashSet<>();

    if (getJavaClassesDir() == null || getJavaClassesDir().isEmpty()) {
      return localTypes;
    }

    File javaOutputDir = getJavaClassesDir().getSingleFile();
    if (!javaOutputDir.exists()) {
      // compileJava produced no output, Gosu-only source set.
      return localTypes;
    }
    if (!javaOutputDir.isDirectory()) {
      throw new GradleException("The Java classes directory " + javaOutputDir.getAbsolutePath()
        + " is not a directory; cannot determine the same-module Java types.");
    }

    // Recursively scan for all .class files
    scanForClassFiles(javaOutputDir, javaOutputDir, localTypes);

    return localTypes;
  }

  /**
   * Recursive helper to scan a directory tree for {@code .class} files and add
   * each one's FQCN (via {@link #extractFQCNFromClassFile}) to {@code fqcns}.
   *
   * @param dir current directory to scan
   * @param rootDir root directory for FQCN calculation
   * @param fqcns set to add discovered FQCNs to
   * @throws GradleException if a directory cannot be listed, or a {@code .class} file's FQCN
   *         cannot be determined -- an incomplete set is silently wrong, see the body
   */
  private void scanForClassFiles(File dir, File rootDir, Set<String> fqcns) {
    File[] files = dir.listFiles();
    if (files == null) {
      throw new GradleException("Could not list contents of " + dir.getAbsolutePath()
        + " while extracting local Java types (not a directory, or an I/O / permission error).");
    }

    for (File file : files) {
      if (file.isDirectory()) {
        scanForClassFiles(file, rootDir, fqcns);
      } else if (file.getName().endsWith(".class")) {
        String fqcn = extractFQCNFromClassFile(file, rootDir);
        if (fqcn == null) {
          throw new GradleException("Cannot determine the FQCN of " + file.getAbsolutePath()
            + " while extracting local Java types: it does not live under "
            + rootDir.getAbsolutePath() + ".");
        }
        fqcns.add(fqcn);
      }
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Not tracked as an input in its own right -- {@link #getStableSources()} covers the same
   * files and is the property the task action queries for changes.  Tracking both would
   * fingerprint the source set twice and, because Gradle keys its incremental-input registry by
   * the value object a getter returns, would reintroduce the aliasing hazard described on
   * {@link #getStableSources()}.  This mirrors {@code JavaCompile}, which annotates its
   * {@code getSource()} override the same way.
   *
   * <p>{@code @PathSensitive} is deliberately absent: Gradle reports a validation error when a
   * property carries {@code @Internal} alongside a declared input modifier.  Declaring
   * {@code @Internal} also discards every annotation category inherited from
   * {@link SourceTask#getSource()} -- including {@code @SkipWhenEmpty} and
   * {@code @IgnoreEmptyDirectories} -- which is why both now sit on {@code getStableSources()}.
   */
  @Override
  @Internal("tracked via stableSources")
  public FileTree getSource() {
    return super.getSource();
  }

  /**
   * Overrides {@link SourceTask#source} to also register non-{@link SourceDirectorySet},
   * non-{@link FileTree} arguments in {@link #getSourceRoots()} so that directories added by
   * external plugins (e.g. via a {@code Provider<Directory>} or {@code Provider<File>}) are
   * treated as Gosu type-system source roots.  {@code SourceDirectorySet} is excluded because
   * {@code GosuBasePlugin} already wires its {@code getSourceDirectories()} into
   * {@code sourceRoots}.  {@code FileTree} is excluded because resolving one yields individual
   * files rather than root directories.
   */
  @Override
  public SourceTask source(Object... sources) {
    for (Object s : sources) {
      if (!(s instanceof SourceDirectorySet) && !(s instanceof FileTree)) {
        getSourceRoots().from(s);
      }
    }
    return super.source(sources);
  }

  /**
   * The Gosu sources, and the sole tracked view of them: {@link #getSource()} is {@code @Internal}
   * so the set is fingerprinted once, here.
   *
   * <p>{@code @SkipWhenEmpty} rather than {@code @Incremental} -- the two are interchangeable for
   * this task's purpose but cannot be combined, because Gradle files them under one annotation
   * category and rejects a property carrying both.  {@code @SkipWhenEmpty} is the stronger of the
   * pair: it yields {@code InputBehavior.PRIMARY}, which tracks per-file changes for
   * {@link InputChanges} exactly as {@code @Incremental} does <em>and</em> skips the task with its
   * previous outputs removed when no Gosu source remains.  {@code @IgnoreEmptyDirectories} keeps a
   * tree of empty package directories counting as "no source" for that check.  Both annotations
   * used to reach the task by inheritance from {@link SourceTask#getSource()}; declaring
   * {@code @Internal} there discards inherited categories, so they are restated here.  This is the
   * same arrangement {@code JavaCompile} uses.
   *
   * <p>{@code @PathSensitive(RELATIVE)}, not {@code NAME_ONLY}: a Gosu type's FQCN <em>is</em> its
   * path relative to the source root -- {@link #extractFQCNFromSourceFile} derives it that way, and
   * gosuc keys its dependency graph on the same mapping.  Normalising to the bare filename would
   * discard exactly that information, so moving {@code com/example/Foo.gs} to
   * {@code com/other/Foo.gs} would leave the fingerprint unchanged and the task wrongly
   * {@code UP-TO-DATE}.  {@code RELATIVE} is still relocatable, so {@code @CacheableTask} and
   * build-cache portability are unaffected.  Matches {@code GosuDoc.getSource()} and
   * {@code JavaCompile.getStableSources()}.
   */
  @SkipWhenEmpty
  @IgnoreEmptyDirectories
  @PathSensitive(RELATIVE)
  @InputFiles
  public FileCollection getStableSources() {
    // Must be a dedicated, stable FileCollection instance -- distinct from getSource().
    // Gradle keys its incremental-input BiMap by the value object returned here, and
    // InputChanges.getFileChanges(getStableSources()) looks the property up by that value, so an
    // instance shared with another tracked property would corrupt the BiMap and make
    // getFileChanges() fail to resolve siblings (e.g. javaClassesDir). Built via the injected
    // ObjectFactory (not getProject()) to stay configuration-cache safe. Same construction as
    // JavaCompile's stableSources.
    if (_stableSources == null) {
      _stableSources = getObjectFactory().fileCollection().from((Callable<FileTree>) this::getSource);
    }
    return _stableSources;
  }

  /**
   * Returns the Java classes output directory for fine-grained change tracking.
   * This tracks the compileJava task's output directory to detect which specific
   * Java class files have changed (ABI changes only, not implementation changes).
   * Configured by GosuBasePlugin using the SourceSet's Java output directory.
   *
   * @return FileCollection pointing to the Java classes output directory
   */
  @CompileClasspath  // ABI-sensitivity: only API changes trigger re-execution; provides built-in normalization
  @Incremental       // Enables querying which specific .class files changed
  @Optional          // Optional because not all projects may have Java sources
  public FileCollection getJavaClassesDir() {
    return javaClassesDir;
  }

  /**
   * Sets the Java classes output directory for fine-grained change tracking.
   * This should be called by GosuBasePlugin during task configuration.
   *
   * @param javaClassesDir FileCollection pointing to the Java classes output directory
   */
  public void setJavaClassesDir(FileCollection javaClassesDir) {
    this.javaClassesDir = javaClassesDir;
  }

  /**
   * @return Gosu-specific compilation options.
   */
  @Nested
  public GosuCompileOptions getGosuOptions() {
    return _gosuCompileOptions;
  }

  @Nested
  public CompileOptions getOptions() {
    if (_compileOptions == null) {
      _compileOptions = getObjectFactory().newInstance(CompileOptions.class);
    }
    return _compileOptions;
  }

  /**
   * We override in order to apply the {@link org.gradle.api.tasks.CompileClasspath}, in order to ignore changes in JAR'd resources.
   * Also filters out javaClassesDir to prevent local .class file changes from triggering full rebuilds.
   * <p>
   * This is the single place javaClassesDir is kept out of Gradle's tracked classpath input.
   * Filtering on read covers every way a value can arrive -- the convention mapping in
   * {@code GosuBasePlugin}, an explicit {@code setClasspath} from another plugin or build
   * script, anything else -- so no filtering is needed at those sites. Local {@code .class}
   * files are tracked instead through the {@code @Incremental} {@link #getJavaClassesDir()}
   * input; {@link #createSpec()} re-adds them to the classpath actually handed to gosuc.
   * <p>
   * This filtering ONLY applies when incremental compilation is configured (i.e., when javaClassesDir is set).
   * When javaClassesDir is not set, the classpath is returned unmodified for backwards compatibility.
   */
  @CompileClasspath
  public FileCollection getClasspath() {
    FileCollection classpath = super.getClasspath();
    if (classpath != null && getJavaClassesDir() != null && !getJavaClassesDir().isEmpty()) {
      classpath = classpath.minus(getJavaClassesDir());
    }
    return classpath;
  }

  /**
   * @return the classpath to use to load the Gosu compiler.
   */
  @Override
  @Classpath
  @InputFiles
  public ConfigurableFileCollection getGosuClasspath() {
    // Lazily initialized so that no explicit @Inject constructor is needed; the abstract
    // getObjectFactory() service getter is available as soon as the task is decorated.
    // Field named 'gosuClasspath' (not '_gosuClasspath') so Gradle's CC BeanPropertyWriter can
    // locate the convention mapping by field name and serialize the inferred classpath at CC store
    // time. An underscore-prefixed name would mismatch the convention key and the injected
    // __gosuClasspath__ explicit-flag field, breaking CC (gosuClasspath always empty on reuse).
    if (gosuClasspath == null) {
      gosuClasspath = getObjectFactory().fileCollection();
    }
    return gosuClasspath;
  }

  @Override
  public void setGosuClasspath(FileCollection gosuClasspath) {
    getGosuClasspath().setFrom(gosuClasspath);
  }

  /**
   * Annotating as @Input or @InputFiles causes errors in Guidewire applications, even when paired with @Optional.
   * Marking as @Internal instead to skip warning thrown by :validateTaskProperties (org.gradle.plugin.devel.tasks.ValidateTaskProperties)
   * @return a Closure returning a classpath to be passed to the GosuCompile task
   */
  @Internal
  public Closure<FileCollection> getOrderClasspath() {
    return _orderClasspath;
  }

  /**
   * Normally setting this value is not required.
   * Certain projects relying on depth-first resolution of module dependencies can use this
   * Closure to reorder the classpath as needed.
   *
   * @param orderClasspath a Closure returning a classpath to be passed to the GosuCompile task
   */
  public void setOrderClasspath(Closure<FileCollection> orderClasspath) {
    _orderClasspath = orderClasspath;
  }

  /**
   * Returns the Gosu source roots (directories). Wired at configuration time by {@code GosuBasePlugin}
   * using the {@link org.gradle.api.file.SourceDirectorySet#getSourceDirectories()} public API.
   */
  @Internal
  public ConfigurableFileCollection getSourceRoots() {
    if (_sourceRoots == null) {
      _sourceRoots = getObjectFactory().fileCollection();
    }
    return _sourceRoots;
  }

  /**
   * The gosuc dependency-tracking file. Declared {@code @OutputFile} so Gradle caches it alongside
   * the {@code .class} files -- on a {@code FROM_CACHE} restore the dep file returns to disk and
   * the next incremental compileGosu can use it as a baseline graph instead of falling back to a
   * full rebuild.
   *
   * <p>Returned as a {@link Provider}, not a {@link org.gradle.api.file.RegularFileProperty}:
   * lazily evaluated, but deliberately with no setter.  The path is derived from the task name so
   * that {@code compileGosu} and {@code compileTestGosu} cannot be aimed at one another's graph;
   * a settable property would give that guarantee away for no benefit.
   *
   * <p>The provider is **absent unless {@code gosuOptions.incrementalCompilation} is on**, which is
   * what {@code @Optional} now means here: not "a declared output that may fail to appear on disk",
   * but "on a non-incremental build this task declares no such output at all".  That matches
   * reality -- gosuc is passed {@code -dependency-file} solely in incremental mode, so nothing
   * would ever write it.  Evaluated lazily, so a build script toggling the flag after task
   * creation is still seen.
   */
  @OutputFile
  @Optional
  public Provider<RegularFile> getDependencyFile() {
    return getProviderFactory().provider(() ->
        getGosuOptions().isIncrementalCompilation()
            ? getLayout().getBuildDirectory().file("tmp/gosuc-deps-" + getName() + ".json").get()
            : null);
  }

  private DefaultGosuCompileSpec createSpec() {
    DefaultGosuCompileSpec spec = new DefaultGosuCompileSpec();
    spec.setSource(getSource());
    spec.setSourceRoots(getSourceRoots());
    spec.setDestinationDir(getDestinationDirectory().get().getAsFile());
    spec.setTempDir(getTemporaryDir());
    spec.setGosuClasspath(getGosuClasspath());
    spec.setCompileOptions(getOptions());
    spec.setGosuCompileOptions(_gosuCompileOptions);
    // null on a non-incremental build, where the provider has no value. Safe: the only reader,
    // CommandLineGosuCompiler, dereferences it solely inside its incrementalCompilation branch.
    spec.setDependencyFile(getDependencyFile().map(RegularFile::getAsFile).getOrNull());

    // Build the classpath for gosuc: combine regular classpath + Java classes directory
    // Note: javaClassesDir is tracked separately as an @Incremental input to enable selective recompilation,
    // but must be included in the actual classpath passed to the compiler
    FileCollection effectiveClasspath;
    if (_orderClasspath == null) {
      effectiveClasspath = getClasspath();
    } else {
      // TODO: orderClasspath closure receives a Project reference — eliminate when orderClasspath is modernized
      Project project = getProject();
      effectiveClasspath = _orderClasspath.call(project, project.getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME));
    }

    // Subtract javaClassesDir before adding it back at the front. Load-bearing on the
    // orderClasspath path above, which resolves the compileClasspath configuration directly and
    // so never passes through getClasspath()'s filtering; a no-op otherwise, since
    // getClasspath() has already removed it.
    if (getJavaClassesDir() != null && !getJavaClassesDir().isEmpty()) {
      effectiveClasspath = effectiveClasspath.minus(getJavaClassesDir());
    }

    // Add Java classes directory to the BEGINNING of the classpath for compiler execution
    // Project Java classes should take precedence over classes from JARs
    if (getJavaClassesDir() != null && !getJavaClassesDir().isEmpty()) {
      effectiveClasspath = getJavaClassesDir().plus(effectiveClasspath);
    }

    spec.setClasspath(asList(effectiveClasspath));

    Logger logger = getLogger();

    if(logger.isInfoEnabled()) {
      logger.info("Gosu Compiler source roots for {} are:", getPath());
      if(spec.getSourceRoots().isEmpty()) {
        logger.info("<empty>");
      } else {
        for(File file : spec.getSourceRoots()) {
          logger.info(file.getAbsolutePath());
        }
      }

      logger.info("Gosu Compiler Spec classpath for {} is:", getPath());
      if(!spec.getClasspath().iterator().hasNext()) {
        logger.info("<empty>");
      } else {
        for(File file : spec.getClasspath()) {
          logger.info(file.getAbsolutePath());
        }
      }

      logger.info("Gosu Compile Spec gosuClasspath for {} is:", getPath());
      FileCollection gosuClasspath = spec.getGosuClasspath();
      if(gosuClasspath.isEmpty()) {
        logger.info("<empty>");
      } else {
        for(File file : gosuClasspath) {
          logger.info(file.getAbsolutePath());
        }
      }
    }

    return spec;
  }

  private GosuCompiler<GosuCompileSpec> getCompiler(GosuCompileSpec spec) {
    if(_compiler == null) {
      File projectDir = getLayout().getProjectDirectory().getAsFile();
      GosuCompilerFactory gosuCompilerFactory = new GosuCompilerFactory(getExecOperations(), getObjectFactory(), projectDir, getPath());
      _compiler = gosuCompilerFactory.newCompiler(spec);
    }
    return _compiler;
  }

  private List<File> asList(final FileCollection files) {
    List<File> list = new ArrayList<>();
    files.forEach(list::add);
    return list;
  }

}
