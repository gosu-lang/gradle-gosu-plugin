package org.gosulang.gradle.tasks;

import groovy.lang.Closure;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.SourceDirectorySetFactory;
import org.gradle.util.ConfigureUtil;

public class DefaultGosuSourceSet implements GosuSourceSet {

  private final SourceDirectorySet _gosu;

  public DefaultGosuSourceSet( String displayName, SourceDirectorySetFactory sourceDirectorySetFactory ) {
    _gosu = sourceDirectorySetFactory.create(displayName + " Gosu source");
    _gosu.getFilter().include("**/*.gs", "**/*.gsx", "**/*.gst", "**/*.gsp");
  }

  @Override
  public SourceDirectorySet getGosu() {
    return _gosu;
  }

  @Override
  public GosuSourceSet gosu( Closure configureClosure ) {
    ConfigureUtil.configure(configureClosure, getGosu());
    return this;
  }
  
}
