package org.gosulang.gradle.tasks.compile;

import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class NormalizingGosuCompiler implements Compiler<GosuCompileSpec> {
  private static final Logger LOGGER = Logging.getLogger(NormalizingGosuCompiler.class);
  private final Compiler<GosuCompileSpec> _delegate;
  
  public NormalizingGosuCompiler(Compiler<GosuCompileSpec> delegate) {
    _delegate = delegate;
  }
  
  @Override
  public WorkResult execute(GosuCompileSpec spec) {
    resolveAndFilterSourceFiles(spec);
    resolveClasspath(spec);
//    resolveNonStringsInCompilerArgs(spec);
    logSourceFiles(spec);
    logCompilerArguments(spec);
    return delegateAndHandleErrors(spec);
  }

  private void resolveAndFilterSourceFiles(final GosuCompileSpec spec) {
    spec.setSource(new SimpleFileCollection(spec.getSource().getFiles()));
  }

  private void resolveClasspath(GosuCompileSpec spec) {
    List<File> classPath = new ArrayList<>();
    for(File file : spec.getClasspath()) {
      classPath.add(file);
    }
    classPath.add(spec.getDestinationDir());
    for(File file : spec.getGosuClasspath().call()) {
      classPath.add(file);
    }
    spec.setClasspath(classPath);

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Class path including Gosu: {}", spec.getClasspath());
    }
  }

  private void logSourceFiles(GosuCompileSpec spec) {
    StringBuilder builder = new StringBuilder();
    builder.append("Source files to be compiled:");
    for (File file : spec.getSource()) {
      builder.append('\n');
      builder.append(file);
    }

    LOGGER.info(builder.toString());
  }

  private void logCompilerArguments(GosuCompileSpec spec) {
    if (!LOGGER.isDebugEnabled()) {
      return;
    }

    List<String> compilerArgs = new JavaCompilerArgumentsBuilder((DefaultGosuCompileSpec) spec).includeLauncherOptions(true).includeSourceFiles(true).build();
    //TODO jam in this plugin FTW
//    compilerArgs.add("-bootclasspath");
//    compilerArgs.add("/home/kmoore/.m2/repository/org/gosu-lang/gosu/gradle-gosu-plugin/0.1.4-SNAPSHOT/gradle-gosu-plugin-0.1.4-SNAPSHOT.jar");
    String joinedArgs = String.join(" ", compilerArgs);
    LOGGER.debug("Gosu Compiler process arguments: {}", joinedArgs);
  }

  private WorkResult delegateAndHandleErrors(GosuCompileSpec spec) {
    return _delegate.execute(spec);
  }
  
}
