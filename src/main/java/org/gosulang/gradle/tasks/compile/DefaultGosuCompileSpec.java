package org.gosulang.gradle.tasks.compile;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.compile.CompileOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultGosuCompileSpec implements GosuCompileSpec {

  private GosuCompileOptions _gosuCompileOptions;
  private FileCollection _gosuClasspath;
  private FileCollection _srcDirSet;

  @Override
  public FileCollection getSourceRoots() {
    return _srcDirSet;
  }

  @Override
  public void setSourceRoots( FileCollection srcDirSet ) {
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

  //-- below are copied from org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec
  private MinimalGosuCompileOptions compileOptions;

  public MinimalGosuCompileOptions getCompileOptions() {
    return this.compileOptions;
  }

  public void setCompileOptions(CompileOptions compileOptions) {
    this.compileOptions = new MinimalGosuCompileOptions(compileOptions);
  }

  //-- below are copied from org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec
  private File _tempDir;
  private List<File> _classpath;
  private File _destinationDir;
  private FileCollection _source;

  @Override
  public File getDestinationDir() {
    return _destinationDir;
  }
  @Override
  public void setDestinationDir(File destinationDir) {
    _destinationDir = destinationDir;
  }

  @Override
  public File getTempDir() {
    return _tempDir;
  }

  @Override
  public void setTempDir(File tempDir) {
    _tempDir = tempDir;
  }

  @Override
  public FileCollection getSource() {
    return _source;
  }

  @Override
  public void setSource(FileCollection source) {
    _source = source;
  }

  @Deprecated
  @Override
  public Iterable<File> getClasspath() {
    return _classpath;
  }

  @Deprecated
  @Override
  public void setClasspath(Iterable<File> classpath) {
    List<File> target = new ArrayList<>();
    classpath.forEach(target::add);
    _classpath = Collections.unmodifiableList(target);
  }

}
