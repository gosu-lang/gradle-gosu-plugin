package org.gosulang.gradle.tasks;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.SourceSet;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultGosuSourceSet implements GosuSourceSet {

  private final SourceDirectorySet _gosu;
  private final SourceDirectorySet _allGosu;

  private static final List<String> _gosuAndJavaExtensions = Arrays.asList("**/*.java", "**/*.gs", "**/*.gsx", "**/*.gst", "**/*.gsp");
  private static final List<String> _gosuExtensionsOnly = _gosuAndJavaExtensions.subList(1, _gosuAndJavaExtensions.size());

  // Ported from Gradle's org.gradle.util.internal.GUtil#toWords (Apache License 2.0:
  // https://github.com/gradle/gradle/blob/master/platforms/core-runtime/base-services/src/main/java/org/gradle/util/internal/GUtil.java),
  // since org.gradle.util.GUtil is removed in Gradle 9.0. Converts an arbitrary string to
  // space-separated words, e.g. camelCase -> camel case, with-separators -> with separators.
  private static final Pattern UPPER_LOWER = Pattern.compile("(?m)([A-Z]*)([a-z0-9]*)");

  private static String toWords(String string) {
    StringBuilder builder = new StringBuilder();
    int pos = 0;
    Matcher matcher = UPPER_LOWER.matcher(string);
    while (pos < string.length()) {
      matcher.find(pos);
      if (matcher.end() == pos) {
        // Not looking at a match
        pos++;
        continue;
      }
      if (builder.length() > 0) {
        builder.append(' ');
      }
      String group1 = matcher.group(1).toLowerCase();
      String group2 = matcher.group(2);
      if (group2.length() == 0) {
        builder.append(group1);
      } else {
        if (group1.length() > 1) {
          builder.append(group1, 0, group1.length() - 1);
          builder.append(' ');
          builder.append(group1.charAt(group1.length() - 1));
        } else {
          builder.append(group1);
        }
        builder.append(group2);
      }
      pos = matcher.end();
    }
    return builder.toString();
  }

  private final String name;
  private final String baseName;
  private final String displayName;

  public DefaultGosuSourceSet( String name, ObjectFactory objectFacotry ) {

    this.name = name;
    this.baseName = name.equals(SourceSet.MAIN_SOURCE_SET_NAME) ? "" : name.toUpperCase();
    displayName = toWords(this.name);
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
  public GosuSourceSet gosu(Closure<?> configureClosure) {
    // Replicates org.gradle.util.ConfigureUtil#configure's DELEGATE_FIRST behavior (removed in
    // Gradle 9.0) without depending on it: setting the delegate + resolve strategy lets
    // DSL-style calls inside the closure body (e.g. `srcDir 'foo'`) resolve against the
    // SourceDirectorySet target rather than the closure's original owner/scope.
    configureClosure.setDelegate(getGosu());
    configureClosure.setResolveStrategy(Closure.DELEGATE_FIRST);
    configureClosure.call(getGosu());
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
