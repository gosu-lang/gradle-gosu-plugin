package org.gosulang.gradle.tasks.compile;

import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.tasks.SimpleStaleClassCleaner;
import org.gradle.language.base.internal.tasks.StaleClassCleaner;

public class CleaningGosuCompiler extends CleaningGosuCompilerSupport<GosuCompileSpec> implements GosuCompiler<GosuCompileSpec> {
  private final Compiler<GosuCompileSpec> compiler;
  private final TaskOutputsInternal taskOutputs;

  public CleaningGosuCompiler(Compiler<GosuCompileSpec> compiler,
                              TaskOutputsInternal taskOutputs) {
    this.compiler = compiler;
    this.taskOutputs = taskOutputs;
  }

  @Override
  public Compiler<GosuCompileSpec> getCompiler() {
    return compiler;
  }

  @Override
  protected StaleClassCleaner createCleaner(final GosuCompileSpec spec) {
    return new SimpleStaleClassCleaner(taskOutputs);
  }
}
