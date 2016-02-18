package org.gosulang.gradle.tasks.compile;

import groovy.lang.Closure;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec;

import java.io.File;
import java.util.Set;

public class DefaultGosuCompileSpec extends DefaultJavaCompileSpec implements GosuCompileSpec {

  private GosuCompileOptions _gosuCompileOptions;
  private transient Closure<FileCollection> _gosuClasspath;
  private Set<File> _srcDirSet;

  @Override
  public Set<File> getSourceRoots() {
    return _srcDirSet;
  }

  @Override
  public void setSourceRoots( Set<File> srcDirSet ) {
    _srcDirSet = srcDirSet;
  }

  @Override
  public Closure<FileCollection> getGosuClasspath() {
    return _gosuClasspath;
  }

  @Override
  public void setGosuClasspath(Closure<FileCollection> _gosuClasspathClosure) {
    _gosuClasspath = _gosuClasspathClosure;
  }

  public GosuCompileOptions getGosuCompileOptions() {
    return _gosuCompileOptions;
  }

  public void setGosuCompileOptions(GosuCompileOptions gosuCompileOptions) {
    _gosuCompileOptions = gosuCompileOptions;
  }


}
