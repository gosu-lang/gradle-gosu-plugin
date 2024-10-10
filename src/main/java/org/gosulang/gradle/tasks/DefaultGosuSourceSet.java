package org.gosulang.gradle.tasks;

import org.gradle.api.Action;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.util.GUtil;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

import static org.gradle.api.reflect.TypeOf.typeOf;

@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated
public abstract class DefaultGosuSourceSet implements GosuSourceSet, HasPublicType {

  private final GosuSourceDirectorySet _gosu;
  private final SourceDirectorySet _allGosu;

  private static final List<String> _gosuAndJavaExtensions = Arrays.asList("**/*.java", "**/*.gs", "**/*.gsx", "**/*.gst", "**/*.gsp");
  private static final List<String> _gosuExtensionsOnly = _gosuAndJavaExtensions.subList(1, _gosuAndJavaExtensions.size());

  private final String name;
  private final String displayName;

  @Inject
  public abstract ObjectFactory getObjectFactory();

  @Inject
  public DefaultGosuSourceSet(String name) {
    this.name = name;
    displayName = GUtil.toWords(this.name);
    _gosu = getObjectFactory().newInstance(DefaultGosuSourceDirectorySet.class, getObjectFactory().sourceDirectorySet("gosu", displayName + " Gosu source"));
    _gosu.getFilter().include(_gosuAndJavaExtensions);
    _allGosu = getObjectFactory().sourceDirectorySet("gosu", displayName + " Gosu source");
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
  public GosuSourceDirectorySet getGosu() {
    return _gosu;
  }

  @Override
  public GosuSourceSet gosu(Action<? super SourceDirectorySet> configureAction) {
    configureAction.execute(getGosu());
    return this;
  }
  
  @Override
  public SourceDirectorySet getAllGosu() {
    return _allGosu;
  }

  @Override
  public TypeOf<?> getPublicType() {
    return typeOf(org.gosulang.gradle.tasks.GosuSourceSet.class);
  }
}
