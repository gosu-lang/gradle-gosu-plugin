package org.gosulang.gradle.tasks.compile;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.JavaCompilerFactory;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager;
import org.gradle.api.internal.tasks.compile.daemon.InProcessCompilerDaemonFactory;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerFactory;

public class GosuCompilerFactory implements CompilerFactory<DefaultGosuCompileSpec> {

  private final ProjectInternal _project;
  private final IsolatedAntBuilder _antBuilder;
  private final JavaCompilerFactory _javaCompilerFactory;
  private final CompilerDaemonManager _compilerDaemonManager;
  private FileCollection _gosuClasspath;
  private final InProcessCompilerDaemonFactory _inProcessCompilerDaemonFactory;

  public GosuCompilerFactory(ProjectInternal project, IsolatedAntBuilder antBuilder, JavaCompilerFactory javaCompilerFactory, CompilerDaemonManager compilerDaemonManager, FileCollection gosuClasspath) {
    this(project, antBuilder, javaCompilerFactory, compilerDaemonManager, gosuClasspath, null);
  }

  public GosuCompilerFactory(ProjectInternal project, IsolatedAntBuilder antBuilder, JavaCompilerFactory javaCompilerFactory, CompilerDaemonManager compilerDaemonManager, FileCollection gosuClasspath, InProcessCompilerDaemonFactory inProcessCompilerDaemonFactory) {
    _project = project;
    _antBuilder = antBuilder;
    _javaCompilerFactory = javaCompilerFactory;
    _compilerDaemonManager = compilerDaemonManager;
    _gosuClasspath = gosuClasspath;
    _inProcessCompilerDaemonFactory = inProcessCompilerDaemonFactory;
  }

  @Override
  public Compiler<DefaultGosuCompileSpec> newCompiler( DefaultGosuCompileSpec spec ) {
    GosuCompileOptions gosuOptions = spec.getGosuCompileOptions();
    Compiler<DefaultGosuCompileSpec> gosuCompiler;
    if(gosuOptions.isUseAnt()) {
      gosuCompiler = new AntGosuCompiler(_antBuilder, spec.getClasspath(), spec.getGosuClasspath());
    } else {
      gosuCompiler = new InProcessGosuCompiler();
    }
    return gosuCompiler;
  }
}
