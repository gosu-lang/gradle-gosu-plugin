package org.gosulang.gradle.tasks.compile;

import org.gradle.api.internal.tasks.compile.daemon.AbstractDaemonCompiler;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonFactory;
import org.gradle.api.internal.tasks.compile.daemon.DaemonForkOptions;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DaemonGosuCompiler<T extends GosuCompileSpec> extends AbstractDaemonCompiler<T> {
  /**
   * SHARED_PACKAGES is a misleading name
   * Rather, this value will expose these packages and subpackages to the worker thread's classloader;
   * maps to FilteredClassLoader filteredApplication in ImplementationClassLoaderWorker
   */
  private static final Iterable<String> SHARED_PACKAGES = Arrays.asList("org.gosulang", "com.sun.tools.javac");
  private final Iterable<File> _gosuClasspath; //FIXME for serialization??

  public DaemonGosuCompiler( File daemonWorkingDir, Compiler<T> delegate, CompilerDaemonFactory compilerDaemonFactory, Iterable<File> gosuClasspath ) {
    super(daemonWorkingDir, delegate, compilerDaemonFactory);
    _gosuClasspath = gosuClasspath;
  }


  @Override
  protected DaemonForkOptions toDaemonOptions( GosuCompileSpec spec ) {
    GosuForkOptions options = spec.getGosuCompileOptions().getForkOptions();
    List<File> classPath = new ArrayList<>();
    for (File file : _gosuClasspath) {
      classPath.add(file);
    }
    List<String> daemonJvmArgs = new ArrayList<>(options.getJvmArgs());
    return new DaemonForkOptions(options.getMemoryInitialSize(), options.getMemoryMaximumSize(), daemonJvmArgs, classPath, SHARED_PACKAGES);
  }
  
}
