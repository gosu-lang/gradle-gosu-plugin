package org.gosulang.gradle.tasks.compile;

import org.gradle.api.tasks.compile.AbstractOptions;

public class GosuCompileOptions extends AbstractOptions {
  private boolean _failOnError = true;
  private boolean _fork = true;
  private boolean _useAnt = true;

  /**
   * Tells whether the compilation task should fail if compile errors occurred. Defaults to {@code true}.
   */
  public boolean isFailOnError() {
    return _failOnError;
  }

  /**
   * Sets whether the compilation task should fail if compile errors occurred. Defaults to {@code true}.
   */
  public void setFailOnError(boolean failOnError) {
    this._failOnError = failOnError;
  }

  /**
   * Tells whether to run the Gosu compiler in a separate process. Defaults to {@code true}.
   */
  public boolean isFork() {
    return _fork;
  }

  /**
   * Sets whether to run the Gosu compiler in a separate process. Defaults to {@code true}.
   */
  public void setFork(boolean fork) {
    this._fork = fork;
  }

  /**
   * Tells whether to use Ant for compilation. If {@code true}, the standard Ant gosuc task will be used for
   * Gosu compilation. {@code false} is currently not supported.
   * <p>
   * Defaults to {@code true}.
   */
  public boolean isUseAnt() {
    return _useAnt;
  }

  public void setUseAnt(boolean useAnt) {
    this._useAnt = useAnt;
//    if (!useAnt) {
//      setFork(true);
//    }
  }



}
