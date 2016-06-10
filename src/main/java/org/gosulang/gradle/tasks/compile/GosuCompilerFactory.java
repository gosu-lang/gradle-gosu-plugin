package org.gosulang.gradle.tasks.compile;

import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonFactory;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class GosuCompilerFactory implements CompilerFactory<GosuCompileSpec> {

  private final String _taskPath;
  private final IsolatedAntBuilder _antBuilder;
  private final CompilerDaemonFactory _compilerDaemonFactory;
  private FileCollection _gosuClasspath;
  private final File _rootProjectDirectory;
  private final File _gradleUserHomeDir;

  public GosuCompilerFactory(String taskPath, File rootProjectDirectory, IsolatedAntBuilder antBuilder, CompilerDaemonFactory compilerDaemonFactory, FileCollection gosuClasspath, File gradleUserHomeDir) {
    _taskPath = taskPath;
    _rootProjectDirectory = rootProjectDirectory;
    _antBuilder = antBuilder;
    _compilerDaemonFactory = compilerDaemonFactory;
    _gosuClasspath = gosuClasspath;
    _gradleUserHomeDir = gradleUserHomeDir;
  }

  @Override
  public Compiler<GosuCompileSpec> newCompiler(GosuCompileSpec spec) {
    GosuCompileOptions gosuCompileOptions = (GosuCompileOptions) spec.getGosuCompileOptions();

    if(gosuCompileOptions.isUseAnt()) {
      if(gosuCompileOptions.isFork()) {
        throw new GradleException("Ant-based Gosu compilation does not support forking. "
            + "The combination of 'gosuCompileOptions.useAnt=false' and 'gosuCompileOptions.fork=true' is invalid.");
      }
      Compiler<GosuCompileSpec> gosuCompiler = new AntGosuCompiler(_antBuilder, spec.getClasspath(), _gosuClasspath, _taskPath);
      return gosuCompiler;
      //return new NormalizingGosuCompiler(gosuCompiler);
    }

    //TODO:KM FGC constructor cannot take spec.getClasspath() or similar runtime-resolved iterable; must pass a resolved Set<File>
    Set<File> gosuClasspathFiles = _gosuClasspath.getFiles();
    Iterable<File> classpath = spec.getClasspath();
    List<File> fullClasspath = new ArrayList<File>();
    fullClasspath.addAll(gosuClasspathFiles);
    for(File file : classpath) {
      fullClasspath.add(file);
    }

    Compiler<GosuCompileSpec> gosuCompiler = new DaemonGosuCompiler<GosuCompileSpec>(_rootProjectDirectory, new ForkingGosuCompiler(gosuClasspathFiles, _gradleUserHomeDir, _taskPath), _compilerDaemonFactory, gosuClasspathFiles);
    return new NormalizingGosuCompiler(gosuCompiler);
  }
}
