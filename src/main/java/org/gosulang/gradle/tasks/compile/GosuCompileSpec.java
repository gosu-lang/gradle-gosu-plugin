package org.gosulang.gradle.tasks.compile;

import org.gosulang.gradle.tasks.InfersGosuRuntime;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.compile.JvmLanguageCompileSpec;

interface GosuCompileSpec extends JvmLanguageCompileSpec, InfersGosuRuntime {

  FileCollection getSourceRoots();

  void setSourceRoots(FileCollection srcDirSet);

  GosuCompileOptions getGosuCompileOptions();
  
}
