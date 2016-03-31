package org.gosulang.gradle.tasks;

import groovy.lang.Closure;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.util.ConfigureUtil;

import java.lang.reflect.Constructor;

public class DefaultGosuSourceSet implements GosuSourceSet {

  private final SourceDirectorySet _gosu;

  public DefaultGosuSourceSet( String displayName, FileResolver fileResolver ) {
    _gosu = createSourceDirectorySet(displayName + " Gosu source", fileResolver);
    _gosu.getFilter().include("**/*.gs", "**/*.gsx", "**/*.gst");
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

  /**
   * <p>Gradle's APIs are very fluid.  Of course, we are using something in a package labeled "internal"
   * which is always risky.
   * <p>Thanks to <a href="https://github.com/ysb33r">Schalk W. Cronj&eacute;</a> for suggesting this workaround.
   * 
   * @return API-appropriate instance of a DefaultSourceDirectorySet
   */
  private DefaultSourceDirectorySet createSourceDirectorySet(String name, FileResolver fileResolver) {
    Constructor<DefaultSourceDirectorySet> ctor;
    try {
      ctor = DefaultSourceDirectorySet.class.getConstructor(String.class, FileResolver.class);
      return ctor.newInstance(name, fileResolver); //Gradle 2.11 and prior
    } catch (Exception gradle212api) {
      //We're probably using Gradle 2.12+ now, but we need to reflectively check for new interfaces first
      try {
        Class<?> dftfInterface = Class.forName("org.gradle.api.internal.file.collections.DirectoryFileTreeFactory");
        Class<?> fileTreeFactory = Class.forName("org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory");
        Object fileTreeFactoryInstance = fileTreeFactory.newInstance();
        ctor = DefaultSourceDirectorySet.class.getConstructor(String.class, FileResolver.class, dftfInterface);
        DefaultSourceDirectorySet instance = ctor.newInstance(name, fileResolver, fileTreeFactoryInstance);
        return instance;
      } catch (Exception fatalException) {
          fatalException.printStackTrace();
          throw new IllegalStateException(fatalException);
      }
    }
  }
  
}
