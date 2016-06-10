package org.gosulang.gradle.tasks;

import org.gradle.api.file.FileCollection;

public interface InfersGosuRuntime {

  FileCollection getGosuClasspath();
  
  void setGosuClasspath(FileCollection gosuClasspath);

}
