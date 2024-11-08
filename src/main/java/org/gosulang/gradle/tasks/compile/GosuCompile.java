package org.gosulang.gradle.tasks.compile;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.tasks.compile.CompilationSourceDirs;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
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
public abstract class GosuCompile extends AbstractCompile {

  private GosuCompiler<GosuCompileSpec> _compiler;
  private BiFunction<Project, Configuration, FileCollection> _orderClasspathFunction;

  private final CompileOptions _compileOptions;
  private final GosuCompileOptions _gosuCompileOptions = new GosuCompileOptions();
  private final FileCollection stableSources = getProject().files((Callable<FileTree>) this::getSource);

  @Inject
  public abstract ObjectFactory getObjectFactory();

  @Inject
  public GosuCompile() {
      _compileOptions = getObjectFactory().newInstance(CompileOptions.class);
  }

  @TaskAction
  protected void compile() {
    GosuCompileSpec spec = createSpec();
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
   * <p>
   * This value is set by default in {@link org.gosulang.gradle.GosuBasePlugin} by inferring the gosu-core-api jar from the compile-time classpath
   */
  @Classpath
  public abstract ConfigurableFileCollection getGosuClasspath(); // {

  @Internal
  public BiFunction<Project, Configuration, FileCollection> getOrderClasspathFunction() {
    return _orderClasspathFunction;
  }

  public void setOrderClasspathFunction(BiFunction<Project, Configuration, FileCollection> orderClasspathFunction) {
    _orderClasspathFunction = orderClasspathFunction;
  }

  @Internal
  public FileCollection getSourceRoots() {
    FileTreeInternal stableSourcesAsFileTree = (FileTreeInternal) getStableSources().getAsFileTree();
    List<File> sourceRoots = CompilationSourceDirs.inferSourceRoots(stableSourcesAsFileTree);
    return getObjectFactory().fileCollection().from(sourceRoots);
  }

  private GosuCompileSpec createSpec() {
    DefaultGosuCompileSpec spec = new DefaultGosuCompileSpec();
    spec.setSource(getSource());
    spec.setSourceRoots(getSourceRoots());
    spec.setDestinationDir(getDestinationDirectory().get().getAsFile());
    spec.setTempDir(getTemporaryDir());
    spec.setGosuClasspath(getGosuClasspath());
    spec.setCompileOptions(_compileOptions);
    spec.setGosuCompileOptions(_gosuCompileOptions);

    if (_orderClasspathFunction == null) {
      spec.setClasspath(asList(getClasspath()));
    } else {
      spec.setClasspath(asList(_orderClasspathFunction.apply(getProject(), getProject().getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME))));
      // FIXME getProject() may break configuration cache
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
    assertGosuClasspathIsNotEmpty();
    if(_compiler == null) {
      GosuCompilerFactory gosuCompilerFactory = getServices().get(ObjectFactory.class).newInstance(GosuCompilerFactory.class, getProjectDir().get(), this.getPath()); // FIXME don't call getProject()
      _compiler = gosuCompilerFactory.newCompiler(spec);
    }
    return _compiler;
  }

  protected void assertGosuClasspathIsNotEmpty() {
    if (getGosuClasspath().isEmpty()) {
      throw new InvalidUserDataException("Cannot infer Gosu classpath because the Gosu Core API Jar was not found.");
    }
  }

  private List<File> asList(final FileCollection files) {
    List<File> list = new ArrayList<>();
    files.forEach(list::add);
    return list;
  }

}
