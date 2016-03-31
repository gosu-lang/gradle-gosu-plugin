package org.gosulang.gradle.tasks.compile;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec;

import java.io.File;
import java.util.Set;

public class DefaultGosuCompileSpec extends DefaultJavaCompileSpec implements GosuCompileSpec {

  private GosuCompileOptions _gosuCompileOptions;
  private transient FileCollection _gosuClasspath;
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
  public FileCollection getGosuClasspath() {
    return _gosuClasspath;
  }

  @Override
  public void setGosuClasspath(FileCollection gosuClasspath) {
    _gosuClasspath = gosuClasspath;
  }

  public GosuCompileOptions getGosuCompileOptions() {
    return _gosuCompileOptions;
  }

  public void setGosuCompileOptions(GosuCompileOptions gosuCompileOptions) {
    _gosuCompileOptions = gosuCompileOptions;
  }


}
