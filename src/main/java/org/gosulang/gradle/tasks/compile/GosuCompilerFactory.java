package org.gosulang.gradle.tasks.compile;

import org.gosulang.gradle.tasks.GosuRuntime;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerFactory;
import org.gradle.util.VersionNumber;
import org.gradle.workers.WorkerExecutor;

import java.io.File;

public class GosuCompilerFactory implements CompilerFactory<DefaultGosuCompileSpec> {

  private final ProjectInternal _project;
  private final String _taskPath;
  private final VersionNumber _gradleVersion;
  private final WorkerExecutor _workerExecutor;

  public GosuCompilerFactory( ProjectInternal project, String forTask, VersionNumber gradleVersion, WorkerExecutor workerExecutor) {
    _project = project;
    _taskPath = forTask;
    _gradleVersion = gradleVersion;
    _workerExecutor = workerExecutor;
  }

  @Override
  public Compiler<DefaultGosuCompileSpec> newCompiler( DefaultGosuCompileSpec spec ) {
    GosuCompileOptions gosuOptions = spec.getGosuCompileOptions();
    Compiler<DefaultGosuCompileSpec> gosuCompiler;// = null;
    VersionNumber gosuVersion = getGosuVersion(spec);
    
    if(gosuOptions.isFork()) {
      if(!isWorkerApiCapable(_gradleVersion, gosuVersion) || isGosucExecutable(gosuOptions.getForkOptions())) {
        gosuCompiler = new CommandLineGosuCompiler(_project, spec, _taskPath);
      } else {
        gosuCompiler = new DaemonGosuCompiler(_project, _taskPath, _workerExecutor);
      }
    } else if(isWorkerApiCapable(_gradleVersion, gosuVersion)) {
      throw new IllegalStateException("Not implemented yet.");
      //gosuCompiler = new DaemonGosuCompiler(_project, _taskPath, _workerExecutor); //TODO IsolationMode.CLASSLOADER version
    } else {
      gosuCompiler = new InProcessGosuCompiler();
    }
    
    return gosuCompiler;
  }
  
  private VersionNumber getGosuVersion(DefaultGosuCompileSpec spec) {
    GosuRuntime gosuRuntime = _project.getExtensions().getByType(GosuRuntime.class);
    File gosuCoreApiJar = gosuRuntime.findGosuJar(spec.getGosuClasspath().call(), "core-api");
    String gosuCoreApiRawVersion = gosuRuntime.getGosuVersion(gosuCoreApiJar);
    return VersionNumber.parse(gosuCoreApiRawVersion).getBaseVersion();
  }

  /**
   * Worker API requires Gradle 3.5+ and Gosu 1.14.7+
   */
  private boolean isWorkerApiCapable(VersionNumber gradleVersion, VersionNumber gosuVersion) {
    return gradleVersion.compareTo(VersionNumber.parse("3.5")) >= 0 &&
        gosuVersion.compareTo(VersionNumber.parse("1.14.7")) >= 0;
  } 

  private boolean isGosucExecutable(ForkOptions forkOptions) {
    String executable = forkOptions.getExecutable();
    return executable != null && !executable.isEmpty() && executable.equals("gosuc"); 
  }
  
}
