package org.gosulang.gradle.tasks.compile;

import org.gradle.api.model.ObjectFactory;
import org.gradle.process.ExecOperations;

import java.io.File;

public class GosuCompilerFactory implements IGosuCompilerFactory<GosuCompileSpec> {

  private final ExecOperations _execOperations;
  private final ObjectFactory _objectFactory;
  private final File _projectDir;
  private final String _taskPath;

  public GosuCompilerFactory(ExecOperations execOperations, ObjectFactory objectFactory, File projectDir, String taskPath) {
    _execOperations = execOperations;
    _objectFactory = objectFactory;
    _projectDir = projectDir;
    _taskPath = taskPath;
  }

  @Override
  public GosuCompiler<GosuCompileSpec> newCompiler( GosuCompileSpec spec ) {
    GosuCompileOptions gosuOptions = spec.getGosuCompileOptions();
    GosuCompiler<GosuCompileSpec> gosuCompiler;
    if(gosuOptions.isFork()) {
      gosuCompiler = new CommandLineGosuCompiler(_execOperations, _objectFactory, spec, _taskPath, _projectDir);
    } else {
      gosuCompiler = new InProcessGosuCompiler();
    }
    return gosuCompiler;
  }
}
