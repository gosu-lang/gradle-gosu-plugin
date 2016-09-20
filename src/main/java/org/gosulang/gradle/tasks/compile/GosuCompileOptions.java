package org.gosulang.gradle.tasks.compile;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.compile.AbstractOptions;
import org.gradle.api.tasks.compile.ForkOptions;

public class GosuCompileOptions extends AbstractOptions {

  //for some reason related to Java reflection, we need to name these private fields exactly like their getters/setters (no leading '_')
  private boolean checkedArithmetic = false;
  private boolean failOnError = true;
  private boolean fork = true;
  private ForkOptions forkOptions = new ForkOptions();
  private boolean verbose = false;

  /**
   * Tells whether the compilation task should fail if compile errors occurred. Defaults to {@code true}.
   * @return the failOnError property
   */
  @Input
  public boolean isFailOnError() {
    return failOnError;
  }

  /**
   * Sets whether the compilation task should fail if compile errors occurred. Defaults to {@code true}.
   * @param failOnError Fail the compilation task if compile errors occur
   */
  public void setFailOnError(boolean failOnError) {
    this.failOnError = failOnError;
  }

  /**
   * Tells whether to run the Gosu compiler in a separate process. Defaults to {@code true}.
   */
  public boolean isFork() {
    return fork;
  }

  /**
   * Sets whether to run the Gosu compiler in a separate process. Defaults to {@code true}.
   */
  public void setFork(boolean fork) {
    this.fork = fork;
  }

  /**
   * Returns options for running the Gosu compiler in a separate process. These options only take effect
   * if {@code fork} is set to {@code true}.
   */
  public ForkOptions getForkOptions() {
    return forkOptions;
  }

  /**
   * Sets options for running the Gosu compiler in a separate process. These options only take effect
   * if {@code fork} is set to {@code true}.
   */
  public void setForkOptions(ForkOptions forkOptions) {
    this.forkOptions = forkOptions;
  }  
  
  public boolean isCheckedArithmetic() {
    return checkedArithmetic;
  }

  /**
   * If true, Gosu classes will be compiled with {@code -DcheckedArithmetic=true}.  Defaults to {@code false}.
   * @param checkedArithmetic Whether to compile with checked arithmetic
   */
  public void setCheckedArithmetic(boolean checkedArithmetic) {
    this.checkedArithmetic = checkedArithmetic;
  }
  
  /**
   * Sets whether the compilation task should use verbose logging. Defaults to {@code false}.
   * @param verbose Use verbose logging
   */
  public void setVerbose(boolean verbose) {
    this.verbose = verbose;
  }

  /**
   * Tells whether to use verbose logging. Defaults to {@code false}.
   */
  public boolean isVerbose() {
    return verbose;
  }
  
}
