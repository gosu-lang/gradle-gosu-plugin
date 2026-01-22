package org.gosulang.gradle.tasks.compile;

import groovy.lang.Closure;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.compile.CompileOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class DefaultGosuCompileSpec implements GosuCompileSpec {

  private GosuCompileOptions _gosuCompileOptions;
  private transient Closure<FileCollection> _gosuClasspath;
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
  private boolean _incremental = false;
  private boolean _fullRebuildRequired = false;
  private Set<String> _changedTypes = new HashSet<>();  // Changed type FQCNs (Java + Gosu)
  private Set<String> _removedTypes = new HashSet<>();  // Removed type FQCNs (Java + Gosu)
  private Set<String> _localJavaTypes = new HashSet<>();  // Local Java type FQCNs for selective tracking

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

  public boolean isIncremental() {
    return _incremental;
  }

  public void setIncremental(boolean incremental) {
    _incremental = incremental;
  }

  public boolean isFullRebuildRequired() {
    return _fullRebuildRequired;
  }

  public void setFullRebuildRequired(boolean fullRebuildRequired) {
    _fullRebuildRequired = fullRebuildRequired;
  }

  public Set<String> getChangedTypes() {
    return _changedTypes;
  }

  public void setChangedTypes(Set<String> changedTypes) {
    _changedTypes = changedTypes;
  }

  public Set<String> getRemovedTypes() {
    return _removedTypes;
  }

  public void setRemovedTypes(Set<String> removedTypes) {
    _removedTypes = removedTypes;
  }

  public Set<String> getLocalJavaTypes() {
    return _localJavaTypes;
  }

  public void setLocalJavaTypes(Set<String> localJavaTypes) {
    _localJavaTypes = localJavaTypes != null ? localJavaTypes : new HashSet<>();
  }

}
