package org.gosulang.gradle.tasks;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.SourceDirectorySetFactory;
import org.gradle.util.ConfigureUtil;

import java.util.Arrays;
import java.util.List;

public class DefaultGosuSourceSet implements GosuSourceSet {

  private final SourceDirectorySet _gosu;
  private final SourceDirectorySet _allGosu;

  private static final List<String> _gosuAndJavaExtensions = Arrays.asList("**/*.java", "**/*.gs", "**/*.gsx", "**/*.gst", "**/*.gsp");
  private static final List<String> _gosuExtensionsOnly = _gosuAndJavaExtensions.subList(1, _gosuAndJavaExtensions.size());

  public DefaultGosuSourceSet( String name, SourceDirectorySetFactory sourceDirectorySetFactory ) {
    _gosu = sourceDirectorySetFactory.create("gosu", name + " Gosu source");
    _gosu.getFilter().include(_gosuAndJavaExtensions);
    _allGosu = sourceDirectorySetFactory.create("gosu", name + " Gosu source");
    _allGosu.getFilter().include(_gosuExtensionsOnly);
    _allGosu.source(_gosu);
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

  @Override
  public GosuSourceSet gosu( Action<? super SourceDirectorySet> configureAction) {
    configureAction.execute(getGosu());
    return this;
  }
  
  @Override
  public SourceDirectorySet getAllGosu() {
    return _allGosu;
  }
  
}
