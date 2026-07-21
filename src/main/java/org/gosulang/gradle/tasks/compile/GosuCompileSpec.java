package org.gosulang.gradle.tasks.compile;

import org.gradle.api.file.FileCollection;

import java.io.File;
import java.util.Set;

interface GosuCompileSpec {

  FileCollection getGosuClasspath();

  void setGosuClasspath(FileCollection gosuClasspath);

  MinimalGosuCompileOptions getCompileOptions();

  FileCollection getSourceRoots();

  void setSourceRoots(FileCollection srcDirSet);

  GosuCompileOptions getGosuCompileOptions(); //TODO roll into MGCO?

  //--- below are copied from org.gradle.api.internal.tasks.compile.JvmLanguageCompileSpec
  File getTempDir();

  void setTempDir(File tempDir);

  File getDestinationDir();

  void setDestinationDir(File destinationDir);

  FileCollection getSource();

  void setSource(FileCollection source);

  @Deprecated
  Iterable<File> getClasspath();

  @Deprecated
  void setClasspath(Iterable<File> classpath);

  //--- incremental compilation state
  File getDependencyFile();

  void setDependencyFile(File dependencyFile);

  boolean isIncremental();

  void setIncremental(boolean incremental);

  boolean isFullRebuildRequired();

  void setFullRebuildRequired(boolean fullRebuildRequired);

  Set<String> getChangedTypes();

  void setChangedTypes(Set<String> changedTypes);

  Set<String> getRemovedTypes();

  void setRemovedTypes(Set<String> removedTypes);

  Set<String> getLocalJavaTypes();

  void setLocalJavaTypes(Set<String> localJavaTypes);

}
