package org.gosulang.gradle.tasks;

import org.gradle.api.file.FileCollection;

import java.io.File;
import java.util.List;

public interface InfersGosuRuntime {

  FileCollection getGosuClasspath();

  void setGosuClasspath(FileCollection gosuClasspath);

}
