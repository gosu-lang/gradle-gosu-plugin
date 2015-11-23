package org.gosulang.gradle.tasks.compile;

import groovy.lang.Closure;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.tasks.compile.CompileOptions;

import java.io.File;
import java.util.Set;

public class DefaultGosuCompileSpec extends DefaultJvmLanguageCompileSpec implements JavaCompileSpec, GosuCompileSpec {

  private CompileOptions _compileOptions;
  private GosuCompileOptions _gosuCompileOptions;
  private Closure<FileCollection> _gosuClasspath;
  private File _dependencyCacheDir;
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
  public CompileOptions getCompileOptions() {
    return _compileOptions;
  }

  public void setCompileOptions(CompileOptions compileOptions) {
    _compileOptions = compileOptions;
  }
  
  @Override
  public File getDependencyCacheDir() {
    return _dependencyCacheDir;
  }

  @Override
  public void setDependencyCacheDir( File dependencyCacheDir ) {
    _dependencyCacheDir = dependencyCacheDir;
  }

  Closure<FileCollection> getGosuClasspath() {
    return _gosuClasspath;
  }

  void setGosuClasspath(Closure<FileCollection> _gosuClasspathClosure) {
    _gosuClasspath = _gosuClasspathClosure;
  }

  public GosuCompileOptions getGosuCompileOptions() {
    return _gosuCompileOptions;
  }

  public void setGosuCompileOptions(GosuCompileOptions gosuCompileOptions) {
    _gosuCompileOptions = gosuCompileOptions;
  }


}
