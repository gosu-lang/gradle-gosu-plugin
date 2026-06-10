package org.gosulang.gradle.tasks.compile;

import groovy.lang.Closure;
import org.gosulang.gradle.tasks.GosuSourceExtensions;
import org.gosulang.gradle.tasks.InfersGosuRuntime;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.tasks.compile.CompilationSourceDirs;
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

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.api.tasks.PathSensitivity.NAME_ONLY;

@CacheableTask
public class GosuCompile extends AbstractCompile implements InfersGosuRuntime {

  private GosuCompiler<GosuCompileSpec> _compiler;
  private Closure<FileCollection> _gosuClasspath;
  private Closure<FileCollection> _orderClasspath;

  private final CompileOptions _compileOptions;
  private final GosuCompileOptions _gosuCompileOptions = new GosuCompileOptions();
  private final FileCollection stableSources = getProject().files(new Callable<FileTree>() {
    @Override
    public FileTree call() {
      return getSource();
    }
  });

  // Track compileJava output directory for fine-grained Java class change detection
  // This will be configured by GosuBasePlugin using the SourceSet's Java output directory
  private FileCollection javaClassesDir;

  @Inject
  public GosuCompile() {
      _compileOptions = getServices().get(ObjectFactory.class).newInstance(CompileOptions.class);
  }

  @TaskAction
  protected void compile(InputChanges inputChanges) {
    DefaultGosuCompileSpec spec = createSpec();
    
    if (!inputChanges.isIncremental()) {
      getLogger().info("Full recompilation is required");
      spec.setFullRebuildRequired(true);
    } else {
      Set<String> changedTypes = new HashSet<>();
      Set<String> removedTypes = new HashSet<>();

      // Extract FQCNs from changed Gosu source files
      for (FileChange change : inputChanges.getFileChanges(getStableSources())) {
        File file = change.getFile();
        if (GosuSourceExtensions.isGosuSourceFile(file.getName())) {
          String fqcn = extractFQCNFromSourceFile(file);
          if (fqcn != null) {
            if (change.getChangeType() == ChangeType.REMOVED) {
              removedTypes.add(fqcn);
              getLogger().info("Gosu type removed: {}", fqcn);
            } else {
              changedTypes.add(fqcn);
              getLogger().info("Gosu type changed: {}", fqcn);
            }
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
            if (fqcn != null) {
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

      spec.setChangedTypes(changedTypes);
      spec.setRemovedTypes(removedTypes);
      spec.setIncremental(true);
    }

    // Extract local Java type FQCNs for selective dependency tracking
    // This allows gosuc to distinguish same-module Java types from JRE/JAR types
    Set<String> localJavaTypes = extractLocalJavaTypeFQCNs();
    spec.setLocalJavaTypes(localJavaTypes);

    _compiler = getCompiler(spec);
    _compiler.execute(spec);
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
    getLogger().debug("Could not determine FQCN for source file: {}", sourceFile.getAbsolutePath());
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
   * @return Set of FQCNs for all Java classes in the javaClassesDir
   */
  private Set<String> extractLocalJavaTypeFQCNs() {
    Set<String> localTypes = new HashSet<>();

    if (getJavaClassesDir() == null || getJavaClassesDir().isEmpty()) {
      return localTypes;
    }

    File javaOutputDir = getJavaClassesDir().getSingleFile();
    if (!javaOutputDir.exists() || !javaOutputDir.isDirectory()) {
      return localTypes;
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
   */
  private void scanForClassFiles(File dir, File rootDir, Set<String> fqcns) {
    File[] files = dir.listFiles();
    if (files == null) {
      getLogger().warn(
        "Could not list contents of {} while extracting local Java types " +
        "(not a directory, or I/O / permission error); skipping subtree.", dir);
      return;
    }

    for (File file : files) {
      if (file.isDirectory()) {
        scanForClassFiles(file, rootDir, fqcns);
      } else if (file.getName().endsWith(".class")) {
        String fqcn = extractFQCNFromClassFile(file, rootDir);
        if (fqcn != null) {
          fqcns.add(fqcn);
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PathSensitive(NAME_ONLY)
  public FileTree getSource() {
    return super.getSource();
  }

  @PathSensitive(NAME_ONLY)
  @InputFiles
  @Incremental
  public FileCollection getStableSources() {
    return stableSources;
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
    return _compileOptions;
  }

  /**
   * We override in order to apply the {@link org.gradle.api.tasks.CompileClasspath}, in order to ignore changes in JAR'd resources.
   * Also filters out javaClassesDir to prevent local .class file changes from triggering full rebuilds.
   * <p>
   * This filtering ONLY applies when incremental compilation is configured (i.e., when javaClassesDir is set).
   * When javaClassesDir is not set, the classpath is returned unmodified for backwards compatibility.
   */
  @CompileClasspath
  public FileCollection getClasspath() {
    FileCollection classpath = super.getClasspath();
    // Defensively filter out javaClassesDir to ensure local .class files are only tracked
    // via the @Incremental javaClassesDir input, not via this classpath input.
    // This only happens when incremental compilation is configured (javaClassesDir != null).
    if (classpath != null && getJavaClassesDir() != null && !getJavaClassesDir().isEmpty()) {
      classpath = classpath.minus(getJavaClassesDir());
    }
    return classpath;
  }

  /**
   * Override setClasspath to defensively filter out javaClassesDir whenever the classpath is set.
   * This ensures that even if gradle-plugins or other code explicitly sets a classpath that includes
   * local .class files, they will be filtered out for Gradle's input tracking purposes.
   * <p>
   * This filtering ONLY applies when incremental compilation is configured (i.e., when javaClassesDir is set).
   * When javaClassesDir is not set, the classpath is stored unmodified for backwards compatibility.
   */
  @Override
  public void setClasspath(FileCollection configuration) {
    // Filter out javaClassesDir before storing the classpath.
    // This only happens when incremental compilation is configured (javaClassesDir != null).
    FileCollection filteredClasspath = configuration;
    if (configuration != null && getJavaClassesDir() != null && !getJavaClassesDir().isEmpty()) {
      filteredClasspath = configuration.minus(getJavaClassesDir());
    }
    super.setClasspath(filteredClasspath);
  }

  /**
   * @return the classpath to use to load the Gosu compiler.
   */
  @Override
  @Classpath
  @InputFiles
  public Closure<FileCollection> getGosuClasspath() {
    return _gosuClasspath;
  }

  @Override
  public void setGosuClasspath(Closure<FileCollection> gosuClasspathClosure) {
    _gosuClasspath = gosuClasspathClosure;
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


@Internal
public FileCollection getSourceRoots() {
  FileTreeInternal stableSourcesAsFileTree = (FileTreeInternal) getStableSources().getAsFileTree();
  List<File> sourceRoots = CompilationSourceDirs.inferSourceRoots(stableSourcesAsFileTree);
  return getProject().getLayout().files(sourceRoots);
}

  /**
   * The gosuc dependency-tracking file. Declared @OutputFile so Gradle caches
   * it alongside the .class files - on a FROM_CACHE restore the dep file
   * returns to disk and the next incremental compileGosu can use it as a
   * baseline graph instead of falling back to a full rebuild.
   *
   * Path is derived from the task name; not user-configurable.
   */
  @OutputFile
  public File getDependencyFile() {
    return getProject().getLayout().getBuildDirectory()
        .file("tmp/gosuc-deps-" + getName() + ".json")
        .get().getAsFile();
  }

  private DefaultGosuCompileSpec createSpec() {
    DefaultGosuCompileSpec spec = new DefaultGosuCompileSpec();
    Project project = getProject();
    spec.setSource(getSource());
    spec.setSourceRoots(getSourceRoots());
    spec.setDestinationDir(getDestinationDirectory().get().getAsFile());
    spec.setTempDir(getTemporaryDir());
    spec.setGosuClasspath(getGosuClasspath());
    spec.setCompileOptions(_compileOptions);
    spec.setGosuCompileOptions(_gosuCompileOptions);
    spec.setDependencyFile(getDependencyFile());

    // Build the classpath for gosuc: combine regular classpath + Java classes directory
    // Note: javaClassesDir is tracked separately as an @Incremental input to enable selective recompilation,
    // but must be included in the actual classpath passed to the compiler
    FileCollection effectiveClasspath;
    if (_orderClasspath == null) {
      effectiveClasspath = getClasspath();
    } else {
      effectiveClasspath = _orderClasspath.call(project, project.getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME));
    }

    // Defensively subtract javaClassesDir from the classpath before adding it back
    // This ensures local .class files are only tracked via the @Incremental javaClassesDir input,
    // not via the classpath input (which would trigger full rebuilds on any change)
    if (getJavaClassesDir() != null && !getJavaClassesDir().isEmpty()) {
      effectiveClasspath = effectiveClasspath.minus(getJavaClassesDir());
    }

    // Add Java classes directory to the BEGINNING of the classpath for compiler execution
    // Project Java classes should take precedence over classes from JARs
    if (getJavaClassesDir() != null && !getJavaClassesDir().isEmpty()) {
      effectiveClasspath = getJavaClassesDir().plus(effectiveClasspath);
    }

    spec.setClasspath(asList(effectiveClasspath));

    Logger logger = project.getLogger();

    if(logger.isInfoEnabled()) {
      logger.info("Gosu Compiler source roots for {} are:", project.getName());
      if(spec.getSourceRoots().isEmpty()) {
        logger.info("<empty>");
      } else {
        for(File file : spec.getSourceRoots()) {
          logger.info(file.getAbsolutePath());
        }
      }

      logger.info("Gosu Compiler Spec classpath for {} is:", project.getName());
      if(!spec.getClasspath().iterator().hasNext()) {
        logger.info("<empty>");
      } else {
        for(File file : spec.getClasspath()) {
          logger.info(file.getAbsolutePath());
        }
      }

      logger.info("Gosu Compile Spec gosuClasspath for {} is:", project.getName());
      FileCollection gosuClasspath = spec.getGosuClasspath().call();
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
      GosuCompilerFactory gosuCompilerFactory = new GosuCompilerFactory(getProject(), this.getPath());
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
