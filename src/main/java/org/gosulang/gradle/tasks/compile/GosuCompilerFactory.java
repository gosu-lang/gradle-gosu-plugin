package org.gosulang.gradle.tasks.compile;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerFactory;

public class GosuCompilerFactory implements CompilerFactory<GosuCompileSpec> {

  private final ProjectInternal _project;
  private final String _taskPath;

  public GosuCompilerFactory(ProjectInternal project, String forTask) {
    _project = project;
    _taskPath = forTask;
  }

  @Override
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
