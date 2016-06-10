package org.gosulang.gradle.tasks.compile;

import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.internal.tasks.compile.CompilationFailedException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A Gosu {@link org.gradle.language.base.internal.compile.Compiler} which does some normalization of the compile configuration and behaviour before delegating to some other compiler.
 */
public class NormalizingGosuCompiler implements Compiler<GosuCompileSpec> {
  private static final Logger LOGGER = Logging.getLogger(NormalizingGosuCompiler.class);
  private final Compiler<GosuCompileSpec> delegate;

  public NormalizingGosuCompiler(Compiler<GosuCompileSpec> delegate) {
    this.delegate = delegate;
  }

  @Override
  public WorkResult execute(GosuCompileSpec spec) {
    resolveAndFilterSourceFiles(spec);
    resolveClasspath(spec);
    logSourceFiles(spec);
    return delegateAndHandleErrors(spec);
  }

  private void resolveAndFilterSourceFiles(final GosuCompileSpec spec) {
    spec.setSource(new SimpleFileCollection(spec.getSource().getFiles()));
  }

  private void resolveClasspath(GosuCompileSpec spec) {
    List<File> classPath = new ArrayList<File>();
    for(File file : spec.getClasspath()) {
      classPath.add(file);
    }
    classPath.add(spec.getDestinationDir());
    spec.setClasspath(classPath);

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Class path including Gosu: {}", spec.getClasspath());
    }
  }

  private void logSourceFiles(GosuCompileSpec spec) {
    if (!spec.getGosuCompileOptions().isListFiles()) {
      return;
    }

    StringBuilder builder = new StringBuilder();
    builder.append("Source files to be compiled:");
    for (File file : spec.getSource()) {
      builder.append('\n');
      builder.append(file);
    }

    LOGGER.quiet(builder.toString());
  }

  private WorkResult delegateAndHandleErrors(GosuCompileSpec spec) {
    try {
      return delegate.execute(spec);
    } catch (CompilationFailedException e) {
      if (spec.getGosuCompileOptions().isFailOnError()) {
        throw e;
      }
      LOGGER.debug("Ignoring compilation failure.");
      return new SimpleWorkResult(false);
    }
  }
}
