package org.gosulang.gradle.tasks.compile;

import org.gosulang.gradle.tasks.InfersGosuRuntime;
import org.gradle.api.internal.tasks.compile.JvmLanguageCompileSpec;

import java.io.File;
import java.util.Set;

interface GosuCompileSpec extends JvmLanguageCompileSpec, InfersGosuRuntime {

  Set<File> getSourceRoots();

  void setSourceRoots(Set<File> srcDirSet);

  GosuCompileOptions getGosuCompileOptions();
  
}
