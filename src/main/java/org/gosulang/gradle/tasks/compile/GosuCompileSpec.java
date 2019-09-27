package org.gosulang.gradle.tasks.compile;

// why does this import fail?

import org.gosulang.gradle.tasks.InfersGosuRuntime;
import org.gradle.api.file.FileCollection;
import org.gradle.internal.impldep.com.google.common.collect.Lists;
import org.gradle.language.base.internal.compile.CompileSpec;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public interface GosuCompileSpec extends CompileSpec, InfersGosuRuntime {

  default boolean incrementalCompilationEnabled() {
    return true;
  }

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

  // why are these deprecated?
  @Deprecated
  Iterable<File> getClasspath();

  // why are these deprecated?
  @Deprecated
  void setClasspath(Iterable<File> classpath);

  Set<String> getClasses();
  void setClasses(Set<String> classesToProcess);

  default List<File> getModulePath() {
    int i = getCompileOptions().getCompilerArgs().indexOf("--module-path");
    if (i < 0) {
      return Collections.emptyList();
    }
    String[] modules = getCompileOptions().getCompilerArgs().get(i + 1).split(File.pathSeparator);
    List<File> result = Lists.newArrayListWithCapacity(modules.length);
    for (String module : modules) {
      result.add(new File(module));
    }
    return result;
  }
}
