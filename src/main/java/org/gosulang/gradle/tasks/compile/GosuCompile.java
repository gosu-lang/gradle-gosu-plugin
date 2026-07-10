package org.gosulang.gradle.tasks.compile;

import groovy.lang.Closure;
import org.gosulang.gradle.tasks.InfersGosuRuntime;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.api.tasks.PathSensitivity.NAME_ONLY;

@CacheableTask
public abstract class GosuCompile extends AbstractCompile implements InfersGosuRuntime {

  private GosuCompiler<GosuCompileSpec> _compiler;
  private ConfigurableFileCollection gosuClasspath;
  private ConfigurableFileCollection _sourceRoots;
  private CompileOptions _compileOptions;
  private final GosuCompileOptions _gosuCompileOptions = new GosuCompileOptions();
  private Closure<FileCollection> _orderClasspath;

  @Inject
  protected abstract ObjectFactory getObjectFactory();

  @Inject
  protected abstract ExecOperations getExecOperations();

  @Inject
  protected abstract ProjectLayout getLayout();

  @TaskAction
  protected void compile() {
    DefaultGosuCompileSpec spec = createSpec();
    _compiler = getCompiler(spec);
    _compiler.execute(spec);
  }

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
    return getSource();
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

  private DefaultGosuCompileSpec createSpec() {
    DefaultGosuCompileSpec spec = new DefaultGosuCompileSpec();
    spec.setSource(getSource());
    spec.setSourceRoots(getSourceRoots());
    spec.setDestinationDir(getDestinationDirectory().get().getAsFile());
    spec.setTempDir(getTemporaryDir());
    spec.setGosuClasspath(getGosuClasspath());
    spec.setCompileOptions(getOptions());
    spec.setGosuCompileOptions(_gosuCompileOptions);

    if (_orderClasspath == null) {
      spec.setClasspath(asList(getClasspath()));
    } else {
      // TODO: orderClasspath closure receives a Project reference — eliminate when orderClasspath is modernized
      Project project = getProject();
      spec.setClasspath(asList(_orderClasspath.call(project, project.getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME))));
    }

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
