package org.gosulang.gradle.tasks;

import groovy.lang.Closure;
import org.gradle.api.file.FileCollection;

public interface InfersGosuRuntime {

  Closure<FileCollection> getGosuClasspath();

  void setGosuClasspath(Closure<FileCollection> gosuClasspathClosure);

}
