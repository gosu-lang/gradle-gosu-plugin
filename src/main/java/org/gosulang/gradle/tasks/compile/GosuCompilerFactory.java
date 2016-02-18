package org.gosulang.gradle.tasks.compile;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.JavaCompilerFactory;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager;
import org.gradle.api.internal.tasks.compile.daemon.InProcessCompilerDaemonFactory;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerFactory;

public class GosuCompilerFactory implements CompilerFactory<GosuCompileSpec> {

  private final ProjectInternal _project;
  private final String _taskPath;
  private final IsolatedAntBuilder _antBuilder;
  private final CompilerDaemonManager _compilerDaemonManager;
  private final InProcessCompilerDaemonFactory _inProcessCompilerDaemonFactory;

  public GosuCompilerFactory(ProjectInternal project, String forTask, IsolatedAntBuilder antBuilder, CompilerDaemonManager compilerDaemonManager) {
    this(project, forTask, antBuilder, compilerDaemonManager, null);
  }

  public GosuCompilerFactory(ProjectInternal project, String forTask, IsolatedAntBuilder antBuilder, CompilerDaemonManager compilerDaemonManager, InProcessCompilerDaemonFactory inProcessCompilerDaemonFactory) {
    _project = project;
    _taskPath = forTask;
    _antBuilder = antBuilder;
    _compilerDaemonManager = compilerDaemonManager;
    _inProcessCompilerDaemonFactory = inProcessCompilerDaemonFactory;
  }

  @Override
  public Compiler<GosuCompileSpec> newCompiler( GosuCompileSpec spec ) {
    GosuCompileOptions gosuOptions = spec.getGosuCompileOptions();
    Compiler<GosuCompileSpec> gosuCompiler;
    if(gosuOptions.isUseAnt()) {
      gosuCompiler = new AntGosuCompiler(_antBuilder, spec.getClasspath(), spec.getGosuClasspath().call(), _taskPath);
    } else {
      gosuCompiler = new InProcessGosuCompiler();
    }
    return gosuCompiler;
  }
}
