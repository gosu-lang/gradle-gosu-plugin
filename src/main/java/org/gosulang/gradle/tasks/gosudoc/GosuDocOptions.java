package org.gosulang.gradle.tasks.gosudoc;

import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.compile.AbstractOptions;
import org.gradle.api.tasks.compile.BaseForkOptions;

public class GosuDocOptions extends AbstractOptions {

  //for some reason related to Java reflection, we need to name these private fields exactly like their getters/setters (no leading '_')
  private String title;
  private BaseForkOptions forkOptions = new BaseForkOptions();
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
   * @return Options for running the gosudoc generator in a separate process.
   */
  @Nested
  public BaseForkOptions getForkOptions() {
    return forkOptions;
  }

  /**
   * @param forkOptions Options for running the gosudoc generator in a separate process.
   */
  public void setForkOptions(BaseForkOptions forkOptions) {
    this.forkOptions = forkOptions;
  }

  @Console
  public boolean isVerbose() {
    return _verbose;
  }

  public void setVerbose( boolean verbose ) {
    _verbose = verbose;
  }  
  
}
