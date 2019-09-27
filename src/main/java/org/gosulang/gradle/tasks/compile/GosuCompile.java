package org.gosulang.gradle.tasks.compile;

import groovy.lang.Closure;
import org.gosulang.gradle.tasks.InfersGosuRuntime;
import org.gosulang.gradle.tasks.compile.incremental.IncrementalCompilerFactory;
import org.gosulang.gradle.tasks.compile.incremental.cache.DefaultGosuCompileCaches;
import org.gosulang.gradle.tasks.compile.incremental.cache.GosuCompileCaches;
import org.gosulang.gradle.tasks.compile.incremental.recomp.GosuRecompilationSpecProvider;
import org.gosulang.gradle.tasks.compile.incremental.recomp.RecompilationSpecProvider;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.FileCollectionAdapter;
import org.gradle.api.internal.file.collections.ListBackedFileSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.incremental.cache.UserHomeScopedCompileCaches;
import org.gradle.api.internal.tasks.compile.incremental.recomp.CompilationSourceDirs;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.snapshot.FileSystemSnapshotter;
import org.gradle.internal.snapshot.WellKnownFileLocations;
import org.gradle.language.base.internal.compile.Compiler;

import javax.inject.Inject;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.api.tasks.PathSensitivity.NAME_ONLY;

@CacheableTask
public class GosuCompile extends AbstractCompile implements InfersGosuRuntime {

  private Compiler<GosuCompileSpec> _compiler;
  private Closure<FileCollection> _gosuClasspath;
  private Closure<FileCollection> _orderClasspath;
  private FileCollection _filesToCompile;

  private final CompileOptions _compileOptions;
  private final GosuCompileOptions _gosuCompileOptions = new GosuCompileOptions();

  private final FileCollection stableSources = getProject().files((Callable<FileTree>) this::getSource);

  @Inject
  public GosuCompile() {
    ObjectFactory objectFactory = getServices().get(ObjectFactory.class);
    CompileOptions compileOptions = objectFactory.newInstance(CompileOptions.class);
    compileOptions.setIncremental(false);
    this._compileOptions = compileOptions;
  }

  /**
   * The sources for incremental change detection.
   * Copied from GradleCompile
   * @since 5.6
   */
  @SkipWhenEmpty
  @PathSensitive(PathSensitivity.RELATIVE) // Java source files are supported, too. Therefore we should care about the relative path.
  @InputFiles
  protected FileCollection getStableSources() {
    return stableSources;
  }

  @Override
  protected void compile() {
    throw new UnsupportedOperationException("no-args compile() method should never be called.");
  }

  @TaskAction
  protected void compile(IncrementalTaskInputs inputs) {
    _filesToCompile = inputs.isIncremental() ? getOutOfDateSource(inputs) : getSource();

    GosuCompileSpec spec = createSpec();

    _compiler = getCompiler(spec, inputs);
    _compiler.execute(spec);
  }

 protected FileCollection getOutOfDateSource(final IncrementalTaskInputs inputs) {
   final List<File> files = new ArrayList<>();

   inputs.outOfDate(inputFileDetails -> files.add(inputFileDetails.getFile()));

   return new FileCollectionAdapter(new ListBackedFileSet(files));
 }

 // TODO: figure out whether we need to call this and remove the corresponding class files
  @SuppressWarnings("unused")
  protected FileCollection getRemovedSource(final IncrementalTaskInputs inputs) {
    final List<File> removed = new ArrayList<>();

    inputs.removed(inputFileDetails -> removed.add(inputFileDetails.getFile()));

    return new FileCollectionAdapter(new ListBackedFileSet(removed));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PathSensitive(NAME_ONLY)
  public FileTree getSource() {
    return super.getSource();
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

  @Internal
  @Optional
  public FileCollection getSourceRoots() {
    Set<File> returnValues = new HashSet<>();
    //noinspection Convert2streamapi
    for(Object obj : getSourceReflectively()) {
      if(obj instanceof SourceDirectorySet) {
        returnValues.addAll(((SourceDirectorySet) obj).getSrcDirs());
      }
    }
    return getProject().files(returnValues);
  }

  //!! todo: find a better way to iterate the FileTree
  private Iterable getSourceReflectively() {
    try {
      Field field = SourceTask.class.getDeclaredField("source");
      field.setAccessible(true);
      return (Iterable)field.get(this);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private DefaultGosuCompileSpec createSpec() {
    // TODO: add new fields
    DefaultGosuCompileSpec spec = new DefaultGosuCompileSpec();
    spec.setCompileOptions(_compileOptions);
    Project project = getProject();
    spec.setSource(_filesToCompile);
    spec.setSourceRoots(getSourceRoots());
    spec.setDestinationDir(getDestinationDir());
    spec.setTempDir(getTemporaryDir());
    spec.setGosuClasspath(getGosuClasspath());
    spec.setCompileOptions(_compileOptions);
    spec.setGosuCompileOptions(_gosuCompileOptions);

    if (_orderClasspath == null) {
      spec.setClasspath(asList(getClasspath()));
    } else {
      spec.setClasspath(asList(_orderClasspath.call(project, project.getConfigurations().getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME))));
    }

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

  protected IncrementalCompilerFactory getIncrementalCompilerFactory() {
    return new IncrementalCompilerFactory(
        getFileOperations(),
        getStreamHasher(),
        getGosuCompileCaches(),
        getBuildOperationExecutor(),
        getStringInterner(),
        getFileSystemSnapshotter(),
        getFileHasher());
  }

  @Inject
  protected FileOperations getFileOperations() {
    return null;
  }

  @Inject
  protected BuildOperationExecutor getBuildOperationExecutor() {
    return null;
  }

  @Inject
  protected FileHasher getFileHasher() {
    return null;
  }

  @Inject
  protected FileSystemSnapshotter getFileSystemSnapshotter() {
    return null;
  }

  @Inject
  protected StringInterner getStringInterner() {
    return null;
  }

  protected GosuCompileCaches getGosuCompileCaches() {
    return new DefaultGosuCompileCaches(
        getFileSystemSnapshotter(),
        getUserHomeScopedCompileCaches(),
        getCacheRepository(),
        getGradle(),
        getInMemoryCacheDecoratorFactory(),
        getWellKnownFileLocations(),
        getStringInterner()
        );
  }

  @Inject
  protected Gradle getGradle() {
    return null;
  }

  @Inject
  protected WellKnownFileLocations getWellKnownFileLocations() {
    return null;
  }

  @Inject
  protected InMemoryCacheDecoratorFactory getInMemoryCacheDecoratorFactory() {
    return null;
  }

  @Inject
  protected CacheRepository getCacheRepository() {
    return null;
  }

  @Inject
  protected UserHomeScopedCompileCaches getUserHomeScopedCompileCaches() {
    return null;
  }

  @Inject
  protected StreamHasher getStreamHasher() {
    return null;
  }

  private Compiler<GosuCompileSpec> getCompiler(GosuCompileSpec spec, IncrementalTaskInputs inputChanges) {
    if (_compiler == null) {
      GosuCompilerFactory gosuCompilerFactory = new GosuCompilerFactory(getProject(), this.getPath());
      _compiler = gosuCompilerFactory.newCompiler(spec);
    }

    CleaningGosuCompiler cleaningGosuCompiler = new CleaningGosuCompiler(_compiler, getOutputs());

    if (spec.incrementalCompilationEnabled()) {
      IncrementalCompilerFactory factory = getIncrementalCompilerFactory();

      return factory.makeIncremental(
          cleaningGosuCompiler,
          getPath(),
          getStableSources().getAsFileTree(),
          createRecompilationSpecProvider(inputChanges, new CompilationSourceDirs(new ArrayList<>(spec.getSourceRoots().getFiles())))
      );
    } else {
      return cleaningGosuCompiler;
    }
  }

  private RecompilationSpecProvider createRecompilationSpecProvider(IncrementalTaskInputs inputChanges, CompilationSourceDirs sourceDirs) {
    return new GosuRecompilationSpecProvider(
        ((ProjectInternal) getProject()).getFileOperations(),
        (FileTreeInternal) getSource(),
        inputChanges,
        sourceDirs
    );
  }

  private List<File> asList(final FileCollection files) {
    List<File> list = new ArrayList<>();
    files.forEach(list::add);
    return list;
  }

}
