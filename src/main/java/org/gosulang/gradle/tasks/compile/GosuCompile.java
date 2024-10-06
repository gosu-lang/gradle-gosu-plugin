package org.gosulang.gradle.tasks.compile;

import org.gosulang.gradle.tasks.InfersGosuRuntime;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.tasks.compile.CompilationSourceDirs;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;

import static org.gradle.api.tasks.PathSensitivity.NAME_ONLY;

@CacheableTask
public abstract class GosuCompile extends AbstractCompile implements InfersGosuRuntime {

  private GosuCompiler<GosuCompileSpec> _compiler;
  private FileCollection _gosuClasspath;
//  private Closure<FileCollection> _orderClasspath;
  private BiFunction<Project, Configuration, FileCollection> _orderClasspathFunction;

  private final CompileOptions _compileOptions;
  private final GosuCompileOptions _gosuCompileOptions = new GosuCompileOptions();
  private final FileCollection stableSources = getProject().files(new Callable<FileTree>() {
    @Override
    public FileTree call() {
      return getSource();
    }
  });

  @Inject
  public abstract ObjectFactory getObjectFactory();

  @Inject
  public GosuCompile() {
      _compileOptions = getObjectFactory().newInstance(CompileOptions.class);
  }

  @TaskAction
  protected void compile() {
    DefaultGosuCompileSpec spec = createSpec();
    _compiler = getCompiler(spec);
    _compiler.execute(spec);
  }

  @Internal
  public abstract DirectoryProperty getProjectDir();

  @Internal
  public abstract Property<String> getProjectName();

  /**
   * {@inheritDoc}
   */
  @Override
  @PathSensitive(NAME_ONLY)
  public FileTree getSource() {
    return super.getSource();
  }

  @SkipWhenEmpty
  @PathSensitive(NAME_ONLY)
  @InputFiles
  public FileCollection getStableSources() {
    return stableSources;
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
  public FileCollection getGosuClasspath() {
    return _gosuClasspath;
  }

  @Override
  public void setGosuClasspath(FileCollection gosuClasspath) {
    _gosuClasspath = gosuClasspath;
  }

//  /**
//   * Annotating as @Input or @InputFiles causes errors in Guidewire applications, even when paired with @Optional.
//   * Marking as @Internal instead to skip warning thrown by :validateTaskProperties (org.gradle.plugin.devel.tasks.ValidateTaskProperties)
//   * @return a Closure returning a classpath to be passed to the GosuCompile task
//   */
//  @Internal
//  public Closure<FileCollection> getOrderClasspath() {
//    return _orderClasspath;
//  }

//  /**
//   * Normally setting this value is not required.
//   * Certain projects relying on depth-first resolution of module dependencies can use this
//   * Closure to reorder the classpath as needed.
//   *
//   * @param orderClasspath a Closure returning a classpath to be passed to the GosuCompile task
//   */
//  public void setOrderClasspath(Closure<FileCollection> orderClasspath) {
//    _orderClasspath = orderClasspath;
//  }

  @Internal
  public BiFunction<Project, Configuration, FileCollection> getOrderClasspathFunction() {
    return _orderClasspathFunction;
  }

  public void setOrderClasspathFunction(BiFunction<Project, Configuration, FileCollection> orderClasspathFunction) {
    _orderClasspathFunction = orderClasspathFunction;
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
  return getObjectFactory().fileCollection().from(sourceRoots); // TODO FIXME ObjectFactory.fileCollection().from(sourceRoots)
}



//  //!! todo: find a better way to iterate the FileTree
//  private Iterable getSourceReflectively() {
//    try {
//     // Field field = SourceTask.class.getDeclaredField("source");
//      Field field = SourceTask.class.getDeclaredField("sourceFiles");
//      field.setAccessible(true);
//      return (Iterable)field.get(this);
//    } catch (Exception e) {
//      throw new RuntimeException(e);
//    }
//  }

  private DefaultGosuCompileSpec createSpec() {
    DefaultGosuCompileSpec spec = new DefaultGosuCompileSpec();
//    Project project = getProject(); // FIXME
    spec.setSource(getSource());
    spec.setSourceRoots(getSourceRoots());
    spec.setDestinationDir(getDestinationDirectory().get().getAsFile());
    spec.setTempDir(getTemporaryDir());
    spec.setGosuClasspath(getGosuClasspath());
    spec.setCompileOptions(_compileOptions);
    spec.setGosuCompileOptions(_gosuCompileOptions);

//    if (_orderClasspath == null) {
//      spec.setClasspath(asList(getClasspath()));
//    } else {
//      spec.setClasspath(asList(_orderClasspath.call(project, project.getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME)))); // FIXME
//      //spec.setClasspath(asList(_orderClasspath.call(project, project.getConfigurations().getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME))));
//    }

    if (_orderClasspathFunction == null) {
      spec.setClasspath(asList(getClasspath()));
    } else {
//      spec.setClasspath(asList(_orderClasspathFunction.apply(project, project.getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME))));
    }

    Logger logger = getLogger();
    String projectName = getProjectName().get();

    if(logger.isInfoEnabled()) {
      logger.info("Gosu Compiler source roots for {} are:", projectName);
      if(spec.getSourceRoots().isEmpty()) {
        logger.info("<empty>");
      } else {
        for(File file : spec.getSourceRoots()) {
          logger.info(file.getAbsolutePath());
        }
      }

      logger.info("Gosu Compiler Spec classpath for {} is:", projectName);
      if(!spec.getClasspath().iterator().hasNext()) {
        logger.info("<empty>");
      } else {
        for(File file : spec.getClasspath()) {
          logger.info(file.getAbsolutePath());
        }
      }

      logger.info("Gosu Compile Spec gosuClasspath for {} is:", projectName);
      FileCollection gosuClasspath = getObjectFactory().fileCollection().from(spec.getGosuClasspath());
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
      GosuCompilerFactory gosuCompilerFactory = getServices().get(ObjectFactory.class).newInstance(GosuCompilerFactory.class, getProjectDir().get(), this.getPath()); // FIXME don't call getProject()
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
