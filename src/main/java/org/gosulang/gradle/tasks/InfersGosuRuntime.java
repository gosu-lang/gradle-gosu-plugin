package org.gosulang.gradle.tasks;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;

public interface InfersGosuRuntime {

  ConfigurableFileCollection getGosuClasspath();

  void setGosuClasspath(FileCollection gosuClasspath);

}
