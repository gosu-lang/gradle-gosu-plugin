package org.gosulang.gradle.tasks.compile;

import groovy.lang.Closure;
import org.gosulang.gradle.tasks.InfersGosuRuntime;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.language.base.internal.compile.Compiler;

import javax.inject.Inject;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class GosuCompile extends AbstractCompile implements InfersGosuRuntime {

  private Compiler<GosuCompileSpec> _compiler;
  private FileCollection _gosuClasspath;
  private Closure<FileCollection> _orderClasspath;

  private final CompileOptions _compileOptions = new CompileOptions();
  private final GosuCompileOptions _gosuCompileOptions = new GosuCompileOptions(); //TODO move to constructor??
  
  @Override
  @TaskAction
  protected void compile() {
    DefaultGosuCompileSpec spec = createSpec();
    _compiler = getCompiler(spec);
    WorkResult result = _compiler.execute(spec);
  }

  @Inject
  public GosuCompile() {}
  
  /**
   * @return Gosu-specific compilation options.
   */
  @Nested
  public GosuCompileOptions getGosuCompileOptions() {
    return _gosuCompileOptions;
  }
  
  @Nested
  public CompileOptions getOptions() {
    return _compileOptions;
  }

  /**
   * @return the classpath to use to load the Gosu compiler.
   */
  @Override
  @InputFiles
  public FileCollection getGosuClasspath() {
    return _gosuClasspath;
  }

  @Override
  public void setGosuClasspath(FileCollection gosuClasspath) {
    _gosuClasspath = gosuClasspath;
  }

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

  public Set<File> getSourceRoots() {
    Set<File> returnValues = new HashSet<>();
    for(Object obj : this.source) {
      if(obj instanceof DefaultSourceDirectorySet) {
        returnValues.addAll(((DefaultSourceDirectorySet) obj).getSrcDirs());
      }
    }
    return returnValues;
  }

  private DefaultGosuCompileSpec createSpec() {
    DefaultGosuCompileSpec spec = new DefaultGosuCompileSpec();
    spec.setCompileOptions(_compileOptions);
    Project project = getProject();
    spec.setSource(getSource());
    spec.setSourceRoots(getSourceRoots());
    spec.setDestinationDir(getDestinationDir());
    spec.setClasspath(getClasspath());
    spec.setGosuClasspath(getGosuClasspath());
    spec.setCompileOptions(_compileOptions);
    spec.setGosuCompileOptions(_gosuCompileOptions);

    if (_orderClasspath == null) {
      spec.setClasspath(getClasspath());
    } else {
      spec.setClasspath(_orderClasspath.call(project, project.getConfigurations().getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)));
    }

    Logger logger = project.getLogger();

    if(logger.isInfoEnabled()) {
      logger.info("Gosu Compiler Spec classpath is:");
      if(!spec.getClasspath().iterator().hasNext()) {
        logger.info("<empty>");
      } else {
        for(File file : spec.getClasspath()) {
          logger.info(file.getAbsolutePath());
        }
      }

      logger.info("Gosu Compile Spec gosuClasspath is:");
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

  private Compiler<GosuCompileSpec> getCompiler(GosuCompileSpec spec) {
    assertGosuClasspathIsNonEmpty();
    if(_compiler == null) {
      ProjectInternal projectInternal = (ProjectInternal) getProject();
      IsolatedAntBuilder antBuilder = getServices().get(IsolatedAntBuilder.class);
      CompilerDaemonManager compilerDaemonManager = getServices().get(CompilerDaemonManager.class);
      GosuCompilerFactory gosuCompilerFactory = new GosuCompilerFactory(this.getPath(), projectInternal.getRootProject().getProjectDir(), antBuilder, compilerDaemonManager, getGosuClasspath(), getProject().getGradle().getGradleUserHomeDir());
      _compiler = gosuCompilerFactory.newCompiler(spec);
    }
    return _compiler;
  }

    private void assertGosuClasspathIsNonEmpty() {
        if (getGosuClasspath().isEmpty()) {
            throw new InvalidUserDataException("'" + getName() + ".gosuClasspath' must not be empty. If a Gosu compile dependency is provided, "
                + "the 'gosu-base' plugin will attempt to configure 'gosuClasspath' automatically. Alternatively, you may configure 'gosuClasspath' explicitly.");
        }
    }
}
