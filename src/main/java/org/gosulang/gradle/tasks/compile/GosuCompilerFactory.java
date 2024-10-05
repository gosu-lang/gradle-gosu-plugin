package org.gosulang.gradle.tasks.compile;

import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;

public abstract class GosuCompilerFactory implements IGosuCompilerFactory<GosuCompileSpec> {

  private final Directory _projectDir;
  private final String _taskPath;

  @Inject
  public abstract ObjectFactory getObjectFactory();

  @Inject
  public GosuCompilerFactory(Directory projectDir, String forTask) {
    _projectDir = projectDir;
    _taskPath = forTask;
  }

  @Override
  public GosuCompiler<GosuCompileSpec> newCompiler( GosuCompileSpec spec ) {
    GosuCompileOptions gosuOptions = spec.getGosuCompileOptions();
    GosuCompiler<GosuCompileSpec> gosuCompiler;
    if(gosuOptions.isFork()) {
      gosuCompiler = getObjectFactory().newInstance(CommandLineGosuCompiler.class, _projectDir, spec, _taskPath);
    } else {
      gosuCompiler = new InProcessGosuCompiler();
    }
    return gosuCompiler;
  }
}
