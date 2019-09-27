package org.gosulang.gradle.tasks.compile;

import org.gradle.api.Project;
import org.gradle.language.base.internal.compile.Compiler;

public class GosuCompilerFactory /* implements IGosuCompilerFactory<GosuCompileSpec> */ {

  private final Project _project;
  private final String _taskPath;

  public GosuCompilerFactory(Project project, String forTask) {
    _project = project;
    _taskPath = forTask;
  }

//  @Override
  public Compiler<GosuCompileSpec> newCompiler( GosuCompileSpec spec ) {
    GosuCompileOptions gosuOptions = spec.getGosuCompileOptions();
    Compiler<GosuCompileSpec> gosuCompiler;
    if(gosuOptions.isFork()) {
      gosuCompiler = new CommandLineGosuCompiler(_project, spec, _taskPath);
    } else {
      gosuCompiler = new InProcessGosuCompiler();
    }
    return gosuCompiler;
  }
}
