package org.gosulang.gradle.tasks.compile;

import java.io.File;
import java.util.Set;

interface GosuCompileSpec {

  Set<File> getSourceRoots();

  void setSourceRoots(Set<File> srcDirSet);

}
