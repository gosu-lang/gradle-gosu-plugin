package org.gosulang.gradle.tasks.gosudoc;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.compile.AbstractOptions;
import org.gradle.api.tasks.compile.ForkOptions;

public class GosuDocOptions extends AbstractOptions {

  //for some reason related to Java reflection, we need to name these private fields exactly like their getters/setters (no leading '_')
  private String title;
  private ForkOptions forkOptions = new ForkOptions();
  private boolean _verbose;

  /**
   * Returns the HTML text to appear in the main frame title.
   * @return the HTML text to appear in the main frame title.
   */
  @Input
  @Optional
  public String getTitle() {
    return title;
  }

  /**
   * Sets the HTML text to appear in the main frame title.
   * @param title the HTML text to appear in the main frame title.
   */
  public void setTitle(String title) {
    this.title = title;
  }

  /**
   * Returns options for running the gosudoc generator in a separate process.
   */
  @Nested
  public ForkOptions getForkOptions() {
    return forkOptions;
  }

  /**
   * Sets options for running the gosudoc generator in a separate process.
   */
  public void setForkOptions(ForkOptions forkOptions) {
    this.forkOptions = forkOptions;
  }

  @Input
  @Optional
  public boolean isVerbose() {
    return _verbose;
  }

  public void setVerbose( boolean verbose ) {
    _verbose = verbose;
  }  
  
}
