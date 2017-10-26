package org.gosulang.gradle.tasks.compile;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerFactory;
import org.gradle.workers.WorkerExecutor;

public class GosuCompilerFactory implements CompilerFactory<DefaultGosuCompileSpec> {

  private final ProjectInternal _project;
  private final String _taskPath;
  private final WorkerExecutor _workerExecutor;

  public GosuCompilerFactory(ProjectInternal project, String forTask, WorkerExecutor workerExecutor) {
    _project = project;
    _taskPath = forTask;
    _workerExecutor = workerExecutor;
  }

  @Override
  public Compiler<DefaultGosuCompileSpec> newCompiler( DefaultGosuCompileSpec spec ) {
    GosuCompileOptions gosuOptions = spec.getGosuCompileOptions();
    Compiler<DefaultGosuCompileSpec> gosuCompiler;
    if(gosuOptions.isFork()) {
      //TODO if Gradle 3.5+ and Gosu 1.14.7+ (accessibility of CommandLineOptions)
      gosuCompiler = new DaemonGosuCompiler(_project, _taskPath, _workerExecutor);
      //else
//      gosuCompiler = new CommandLineGosuCompiler(_project, spec, _taskPath);
    } else {
      gosuCompiler = new InProcessGosuCompiler();
    }
    return gosuCompiler;
  }
}
