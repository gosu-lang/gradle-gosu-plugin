package org.gosulang.gradle.tasks;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.GUtil;

import java.util.Arrays;
import java.util.List;

public class DefaultGosuSourceSet implements GosuSourceSet {

  private final SourceDirectorySet _gosu;
  private final SourceDirectorySet _allGosu;

  private static final List<String> _gosuAndJavaExtensions = Arrays.asList("**/*.java", "**/*.gs", "**/*.gsx", "**/*.gst", "**/*.gsp");
  private static final List<String> _gosuExtensionsOnly = _gosuAndJavaExtensions.subList(1, _gosuAndJavaExtensions.size());

  private final String name;
  private final String baseName;
  private final String displayName;

  public DefaultGosuSourceSet( String name, ObjectFactory objectFacotry ) {

    this.name = name;
    this.baseName = name.equals(SourceSet.MAIN_SOURCE_SET_NAME) ? "" : GUtil.toCamelCase(name);
    displayName = GUtil.toWords(this.name);
    _gosu = objectFacotry.sourceDirectorySet("gosu", displayName + " Gosu source");
    _gosu.getFilter().include(_gosuAndJavaExtensions);
    _allGosu = objectFacotry.sourceDirectorySet("gosu", displayName + " Gosu source");
    _allGosu.getFilter().include(_gosuExtensionsOnly);
    _allGosu.source(_gosu);
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return "source set '" + getDisplayName() + "'";
  }

  public String getDisplayName() {
    return displayName;
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
