package org.gosulang.gradle.tasks.compile;

import java.io.File;
import java.util.Set;

/**
 * Created by kmoore on 9/1/15.
 */
interface GosuCompileSpec {

  Set<File> getSourceRoots();

  void setSourceRoots(Set<File> srcDirSet);

}
