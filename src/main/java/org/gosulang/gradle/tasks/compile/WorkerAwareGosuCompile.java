package org.gosulang.gradle.tasks.compile;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;

@CacheableTask
public class WorkerAwareGosuCompile extends GosuCompile {
  protected WorkerExecutor _workerExecutor;

  /**
   * Only used by Gradle 3.5+
   * @param workerExecutor
   */
  @Inject
  public WorkerAwareGosuCompile(WorkerExecutor workerExecutor) {
    _workerExecutor = workerExecutor;
  }

  @Override
  protected Compiler<DefaultGosuCompileSpec> getCompiler(DefaultGosuCompileSpec spec) {
    if(_compiler == null) {
      ProjectInternal projectInternal = (ProjectInternal) getProject();
      GosuCompilerFactory gosuCompilerFactory = new WorkerAwareGosuCompilerFactory(projectInternal, this.getPath(), _gradleVersion, _workerExecutor);
      _compiler = gosuCompilerFactory.newCompiler(spec);
    }
    return _compiler;
  }
}
