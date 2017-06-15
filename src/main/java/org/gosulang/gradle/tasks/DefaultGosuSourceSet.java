package org.gosulang.gradle.tasks;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.util.ConfigureUtil;

import java.lang.reflect.Constructor;

public class DefaultGosuSourceSet implements GosuSourceSet {

  private final SourceDirectorySet _gosu;
  private final SourceDirectorySet _allGosu;

  public DefaultGosuSourceSet( String name, FileResolver fileResolver ) {
    _gosu = createSourceDirectorySet("gosu", name + " Gosu source", fileResolver);
    _gosu.getFilter().include("**/*.java", "**/*.gs", "**/*.gsx", "**/*.gst", "**/*.gsp");
    _allGosu = createSourceDirectorySet("gosu", name + " Gosu source", fileResolver); //TODO 2 args only?
    _allGosu.getFilter().include("**/*.gs", "**/*.gsx", "**/*.gst", "**/*.gsp");
    _allGosu.source(_gosu);
  }

// Below is incompatible with Gradle versions prior to 2.12
// TODO Introduce this constructor along with Gradle 4.0, at which point we can do away with the reflection below and stop supporting Gradle 2.x
//  public DefaultGosuSourceSet( String displayName, SourceDirectorySetFactory sourceDirectorySetFactory ) {
//    _gosu = sourceDirectorySetFactory.create(displayName + " Gosu source");
//    _gosu.getFilter().include("**/*.gs", "**/*.gsx", "**/*.gst", "**/*.gsp");
//  }
  
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
  
  /**
   * <p>Gradle's APIs are very fluid.  Of course, we are using something in a package labeled "internal"
   * which is always risky.
   * <p>Thanks to <a href="https://github.com/ysb33r">Schalk W. Cronj&eacute;</a> for suggesting this workaround.
   * 
   * @return API-appropriate instance of a DefaultSourceDirectorySet
   */
  private DefaultSourceDirectorySet createSourceDirectorySet(String name, String displayName, FileResolver fileResolver) {
    Constructor<DefaultSourceDirectorySet> ctor;
    try {
      Class<?> dftfInterface = Class.forName("org.gradle.api.internal.file.collections.DirectoryFileTreeFactory");
      Class<?> fileTreeFactory = Class.forName("org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory");
      Object fileTreeFactoryInstance = fileTreeFactory.newInstance();
      ctor = DefaultSourceDirectorySet.class.getConstructor(String.class, String.class, FileResolver.class, dftfInterface);
      return ctor.newInstance(name, displayName, fileResolver, fileTreeFactoryInstance);
    } catch (Exception e) {
      //We're probably using a Gradle version lower than 2.12 now, but we need to reflectively check for new interfaces first
      // Prefer DefaultSourceDirectorySetFactory(
      try {
        ctor = DefaultSourceDirectorySet.class.getConstructor(String.class, FileResolver.class);
        return ctor.newInstance(name, fileResolver); //Gradle 2.11 and prior, scheduled for removal in Gradle 4.0
      } catch (Exception fatalException) {
          fatalException.printStackTrace();
          throw new IllegalStateException(fatalException);
      }
    }
  }
  
}
