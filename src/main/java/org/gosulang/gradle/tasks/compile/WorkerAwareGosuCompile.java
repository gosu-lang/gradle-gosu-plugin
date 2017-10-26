package org.gosulang.gradle.tasks.compile;

import org.gradle.api.tasks.CacheableTask;
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
}
