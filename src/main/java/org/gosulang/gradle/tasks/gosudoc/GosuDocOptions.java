package org.gosulang.gradle.tasks.gosudoc;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.compile.AbstractOptions;

public class GosuDocOptions extends AbstractOptions {

  //for some reason related to Java reflection, we need to name these private fields exactly like their getters/setters (no leading '_')
  private String title;

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
}
