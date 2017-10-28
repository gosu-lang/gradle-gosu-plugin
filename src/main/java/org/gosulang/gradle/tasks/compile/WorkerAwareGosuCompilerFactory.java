package org.gosulang.gradle.tasks.compile;

import org.gosulang.gradle.tasks.GosuRuntime;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.util.VersionNumber;
import org.gradle.workers.WorkerExecutor;

import java.io.File;

public class WorkerAwareGosuCompilerFactory extends GosuCompilerFactory {
  private final VersionNumber _gradleVersion;
  private final WorkerExecutor _workerExecutor;

  public WorkerAwareGosuCompilerFactory(ProjectInternal project, String forTask, VersionNumber gradleVersion, WorkerExecutor workerExecutor) {
    super(project, forTask);
    _gradleVersion = gradleVersion;
    _workerExecutor = workerExecutor;
  }

  @Override
  public Compiler<DefaultGosuCompileSpec> newCompiler(DefaultGosuCompileSpec spec ) {
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

  /**
   * Worker API requires Gradle 4.0+ and Gosu 1.14.7+
   */
  private boolean isWorkerApiCapable(VersionNumber gradleVersion, VersionNumber gosuVersion) {
    return gradleVersion.compareTo(VersionNumber.parse("4.0")) >= 0 &&
        gosuVersion.compareTo(VersionNumber.parse("1.14.7")) >= 0;
  }

  private VersionNumber getGosuVersion(DefaultGosuCompileSpec spec) {
    GosuRuntime gosuRuntime = _project.getExtensions().getByType(GosuRuntime.class);
    File gosuCoreApiJar = gosuRuntime.findGosuJar(spec.getGosuClasspath().call(), "core-api");
    String gosuCoreApiRawVersion = gosuRuntime.getGosuVersion(gosuCoreApiJar);
    return VersionNumber.parse(gosuCoreApiRawVersion).getBaseVersion();
  }

  private boolean isGosucExecutable(ForkOptions forkOptions) {
    String executable = forkOptions.getExecutable();
    return executable != null && !executable.isEmpty() && executable.equals("gosuc");
  }
}
