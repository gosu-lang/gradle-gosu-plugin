package org.gosulang.gradle.tasks;

import groovy.lang.Closure;
import org.gradle.api.file.SourceDirectorySet;

public interface GosuSourceSet {

  /**
   * Returns the source to be compiled by the Gosu compiler for this source set.
   *
   * @return The Gosu source. Never returns null.
   */
  SourceDirectorySet getGosu();

  /**
   * Configures the Gosu source for this set.
   *
   * <p>The given closure is used to configure the {@link SourceDirectorySet} which contains the Gosu source.
   *
   * @param configureClosure The closure to use to configure the Gosu source.
   * @return this
   */
  GosuSourceSet gosu(Closure configureClosure);
}
