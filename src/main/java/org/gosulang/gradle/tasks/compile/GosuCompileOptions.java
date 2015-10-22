package org.gosulang.gradle.tasks.compile;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.compile.AbstractOptions;

public class GosuCompileOptions extends AbstractOptions {

  //for some reason related to Java reflection, we need to name these private fields exactly like their getters/setters (no leading '_')
  private boolean failOnError = true;
  private boolean checkedArithmetic = false;
  private boolean useAnt = true;

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

//  /**
//   * Tells whether to run the Gosu compiler in a separate process. Defaults to {@code true}.
//   */
//  public boolean isFork() {
//    return fork;
//  }
//
//  /**
//   * Sets whether to run the Gosu compiler in a separate process. Defaults to {@code true}.
//   */
//  public void setFork(boolean fork) {
//    this.fork = fork;
//  }

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
   * Tells whether to use Ant for compilation. If {@code true}, the standard Ant gosuc task will be used for
   * Gosu compilation. {@code false} is currently not supported.
   * <p>
   * Defaults to {@code true}.
   * @return true if the Ant-based compiler should be used, false otherwise
   */
  public boolean isUseAnt() {
    return useAnt;
  }

  /**
   * @param useAnt true if the Ant-based compiler should be used, false otherwise
   */
  public void setUseAnt(boolean useAnt) {
    this.useAnt = useAnt;
//    if (!useAnt) {
//      setFork(true);
//    }
  }

  /**
   * Some compiler options are not recognizable by the gosuc ant task; 
   * this method prevents incompatible values from being passed to the ant configuration closure
   * @param fieldName name of field to exclude from the dynamically generated ant script 
   * @return true if the given fieldName should be excluded
   */
  @Override
  protected boolean excludeFromAntProperties(String fieldName) {
    return fieldName.equals("useAnt");
  }

}
