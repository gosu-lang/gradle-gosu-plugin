package org.gosulang.gradle.tasks.compile;

import groovy.lang.Closure;
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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
        if (file.getName().endsWith(".gs") || file.getName().endsWith(".gsx")) {
          String fqcn = extractFQCNFromSourceFile(file);
          if (fqcn != null) {
            if (change.getChangeType() == ChangeType.REMOVED) {
              removedTypes.add(fqcn);
              getLogger().info("Gosu type removed: {}", fqcn);
              // Delete stale .class file(s) to prevent them from lingering in the output directory
              deleteClassFiles(fqcn, getDestinationDirectory().get().getAsFile());
            } else {
              changedTypes.add(fqcn);
              getLogger().info("Gosu type changed: {}", fqcn);
            }
          }
        }
      }

      // Extract FQCNs from changed Java class files (if configured)
      // This provides fine-grained tracking for project Java sources (not JARs)
      if (getJavaClassesDir() != null && !getJavaClassesDir().isEmpty()) {
        for (FileChange change : inputChanges.getFileChanges(getJavaClassesDir())) {
          File classFile = change.getFile();
          if (classFile.getName().endsWith(".class")) {
            String fqcn = extractFQCNFromClassFile(classFile, getJavaClassesDir().getSingleFile());
            if (fqcn != null && !fqcn.contains("$")) { // Skip inner classes
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



    _compiler = getCompiler(spec);
    _compiler.execute(spec);
  }

  /**
   * Extracts the fully-qualified class name from a Gosu source file by finding its source root.
   *
   * @param sourceFile the .gs or .gsx file
   * @return the FQCN (e.g., "com.example.MyClass") or null if extraction fails
   */
  private String extractFQCNFromSourceFile(File sourceFile) {
    FileCollection sourceRoots = getSourceRoots();

    for (File sourceRoot : sourceRoots.getFiles()) {
      if (sourceFile.getAbsolutePath().startsWith(sourceRoot.getAbsolutePath())) {
        java.nio.file.Path rootPath = sourceRoot.toPath();
        java.nio.file.Path filePath = sourceFile.toPath();

        // Get relative path from root
        java.nio.file.Path relativePath = rootPath.relativize(filePath);

        // Convert: com/example/MyClass.gs -> com.example.MyClass
        String fqcn = relativePath.toString()
          .replace(File.separator, ".");

        // Remove extension (.gs or .gsx)
        if (fqcn.endsWith(".gs")) {
          fqcn = fqcn.substring(0, fqcn.length() - 3);
        } else if (fqcn.endsWith(".gsx")) {
          fqcn = fqcn.substring(0, fqcn.length() - 4);
        }

        return fqcn;
      }
    }

    // Could not find source root
    getLogger().debug("Could not determine FQCN for source file: {}", sourceFile.getAbsolutePath());
    return null;
  }

  /**
   * Extracts the fully-qualified class name from a Java .class file by computing
   * its path relative to the Java classes directory.
   *
   * @param classFile the .class file
   * @param javaClassesRoot the root directory (e.g., build/classes/java/main)
   * @return the FQCN (e.g., "com.example.MyClass") or null if extraction fails
   */
  private String extractFQCNFromClassFile(File classFile, File javaClassesRoot) {
    if (!classFile.getAbsolutePath().startsWith(javaClassesRoot.getAbsolutePath())) {
      return null;
    }

    java.nio.file.Path rootPath = javaClassesRoot.toPath();
    java.nio.file.Path filePath = classFile.toPath();
    java.nio.file.Path relativePath = rootPath.relativize(filePath);

    // Convert: com/example/MyClass.class -> com.example.MyClass
    String fqcn = relativePath.toString()
      .replace(File.separator, ".")
      .replace(".class", "");

    return fqcn.isEmpty() ? null : fqcn;
  }

  /**
   * Deletes the .class file(s) for a removed Gosu type, including any inner/anonymous classes.
   * This ensures stale class files don't remain in the output directory when source files are deleted.
   *
   * @param fqcn the fully-qualified class name (e.g., "com.example.MyClass")
   * @param outputDir the Gosu output directory (e.g., build/classes/gosu/main)
   */
  private void deleteClassFiles(String fqcn, File outputDir) {
    // Convert FQCN to file path: com.example.Foo -> com/example/Foo.class
    String relativePath = fqcn.replace('.', File.separatorChar);
    File mainClassFile = new File(outputDir, relativePath + ".class");

    // Delete main class file
    if (mainClassFile.exists()) {
      if (mainClassFile.delete()) {
        getLogger().info("Deleted stale class file: {}", mainClassFile);
      } else {
        getLogger().warn("Failed to delete class file: {}", mainClassFile);
      }
    }

    // Delete inner/anonymous classes (Foo$*.class)
    File parentDir = mainClassFile.getParentFile();
    if (parentDir != null && parentDir.exists()) {
      String className = mainClassFile.getName().replace(".class", "");
      File[] innerClasses = parentDir.listFiles((dir, name) ->
        name.startsWith(className + "$") && name.endsWith(".class")
      );
      if (innerClasses != null) {
        for (File innerClass : innerClasses) {
          if (innerClass.delete()) {
            getLogger().info("Deleted stale inner class file: {}", innerClass);
          } else {
            getLogger().warn("Failed to delete inner class file: {}", innerClass);
          }
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
   */
  @CompileClasspath
  public FileCollection getClasspath() {
    return super.getClasspath();
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

/*  @Internal
  public FileCollection getSourceRoots() {
    Set<File> returnValues = new HashSet<>();
    //noinspection Convert2streamapi
   //  for(Object obj : getSourceReflectively()) {
    for(Object obj : getSource()) {
      if(obj instanceof SourceDirectorySet) {
        returnValues.addAll(((SourceDirectorySet) obj).getSrcDirs());
      }
    }
    return getProject().files(returnValues);
  }*/


@Internal
public FileCollection getSourceRoots() {
  FileTreeInternal stableSourcesAsFileTree = (FileTreeInternal) getStableSources().getAsFileTree();
  List<File> sourceRoots = CompilationSourceDirs.inferSourceRoots(stableSourcesAsFileTree);
  return getProject().getLayout().files(sourceRoots);
}



  //!! todo: find a better way to iterate the FileTree
  private Iterable getSourceReflectively() {
    try {
     // Field field = SourceTask.class.getDeclaredField("source");
      Field field = SourceTask.class.getDeclaredField("sourceFiles");
      field.setAccessible(true);
      return (Iterable)field.get(this);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
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

    // Build the classpath for gosuc: combine regular classpath + Java classes directory
    // Note: javaClassesDir is tracked separately as an @Incremental input to enable selective recompilation,
    // but must be included in the actual classpath passed to the compiler
    FileCollection effectiveClasspath;
    if (_orderClasspath == null) {
      effectiveClasspath = getClasspath();
    } else {
      effectiveClasspath = _orderClasspath.call(project, project.getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME));
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
