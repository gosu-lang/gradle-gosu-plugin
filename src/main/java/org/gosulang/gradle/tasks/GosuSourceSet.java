package org.gosulang.gradle.tasks;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.file.SourceDirectorySet;

/**
 * @deprecated Using convention to contribute to source sets is deprecated. You can configure the groovy sources via the {@code GosuSourceDirectorySet} extension (e.g.
 * {@code sourceSet.getExtensions().getByType(GosuSourceDirectorySet.class).setSrcDirs(...)}).
 */
@Deprecated
public interface GosuSourceSet {

  /**
   * Returns the source to be compiled by the Gosu compiler for this source set. This may contain both Java and Gosu source files.
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

  /**
   * Configures the Gosu source for this set.
   *
   * <p>The given action is used to configure the {@link SourceDirectorySet} which contains the Gosu source.
   *
   * @param configureAction The action to use to configure the Gosu source.
   * @return this
   */
  GosuSourceSet gosu( Action<? super SourceDirectorySet> configureAction);  
  
  /**
   * All Gosu source for this source set.
   *
   * @return the Gosu source. Never returns null.
   */
  SourceDirectorySet getAllGosu();
}
