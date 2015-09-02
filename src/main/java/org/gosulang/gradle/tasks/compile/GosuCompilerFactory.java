package org.gosulang.gradle.tasks.compile;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.JavaCompilerFactory;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager;
import org.gradle.api.internal.tasks.compile.daemon.InProcessCompilerDaemonFactory;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerFactory;

public class GosuCompilerFactory implements CompilerFactory<DefaultGosuCompileSpec> {

  private final ProjectInternal _project;
  private final JavaCompilerFactory _javaCompilerFactory;
  private final CompilerDaemonManager _compilerDaemonManager;
  private final InProcessCompilerDaemonFactory _inProcessCompilerDaemonFactory;

  public GosuCompilerFactory(ProjectInternal project, JavaCompilerFactory javaCompilerFactory, CompilerDaemonManager compilerDaemonManager) {
    this(project, javaCompilerFactory, compilerDaemonManager, null);
  }

  public GosuCompilerFactory(ProjectInternal project, JavaCompilerFactory javaCompilerFactory, CompilerDaemonManager compilerDaemonManager, InProcessCompilerDaemonFactory inProcessCompilerDaemonFactory) {
    _project = project;
    _javaCompilerFactory = javaCompilerFactory;
    _compilerDaemonManager = compilerDaemonManager;
    _inProcessCompilerDaemonFactory = inProcessCompilerDaemonFactory;
  }

  @Override
  public Compiler<DefaultGosuCompileSpec> newCompiler( DefaultGosuCompileSpec spec ) {
    Compiler<DefaultGosuCompileSpec> gosuCompiler = new InProcessGosuCompiler();
    return gosuCompiler;
  }
}
