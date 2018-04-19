package org.gosulang.gradle.tasks.compile;

import org.gradle.api.Project;

public class GosuCompilerFactory implements IGosuCompilerFactory<GosuCompileSpec> {

  private final Project _project;
  private final String _taskPath;

  public GosuCompilerFactory(Project project, String forTask) {
    _project = project;
    _taskPath = forTask;
  }

  @Override
  public GosuCompiler<GosuCompileSpec> newCompiler( GosuCompileSpec spec ) {
    GosuCompileOptions gosuOptions = spec.getGosuCompileOptions();
    GosuCompiler<GosuCompileSpec> gosuCompiler;
    if(gosuOptions.isFork()) {
      gosuCompiler = new CommandLineGosuCompiler(_project, spec, _taskPath);
    } else {
      gosuCompiler = new InProcessGosuCompiler();
    }
    return gosuCompiler;
  }
}
