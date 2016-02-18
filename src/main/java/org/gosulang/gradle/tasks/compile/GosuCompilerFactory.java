package org.gosulang.gradle.tasks.compile;

import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GosuCompilerFactory implements CompilerFactory<GosuCompileSpec> {

  private final ProjectInternal _project;
  private final String _taskPath;
  private final IsolatedAntBuilder _antBuilder;
  private FileCollection _gosuClasspath;

  public GosuCompilerFactory(ProjectInternal project, String forTask, IsolatedAntBuilder antBuilder, CompilerDaemonManager compilerDaemonManager) {
    _project = project;
    _taskPath = forTask;
    _antBuilder = antBuilder;
  }

  @Override
  public Compiler<GosuCompileSpec> newCompiler( GosuCompileSpec spec ) {
    //CompileOptions compileOptions = spec.getCompileOptions();
    GosuCompileOptions gosuCompileOptions = spec.getGosuCompileOptions();
    Compiler<GosuCompileSpec> gosuCompiler;
    FileCollection gosuClasspath = spec.getGosuClasspath().call();

//    options.setBootClasspath("/Users/kmoore/.m2/repository/org/gosu-lang/gosu/gradle-gosu-plugin/0.1.4-SNAPSHOT/gradle-gosu-plugin-0.1.4-SNAPSHOT.jar");

    Iterable<File> classpath = spec.getClasspath();
    List<File> fullClasspath = new ArrayList<>();
    fullClasspath.addAll(gosuClasspath.getFiles());
    for(File file : classpath) {
      fullClasspath.add(file);
    }
    if(gosuCompileOptions.isUseAnt()) {
      if(gosuCompileOptions.isFork()) {
        throw new GradleException("Ant-based Gosu compilation does not support forking. " +
          "The combination of 'gosuCompileOptions.useAnt=false' and 'gosuCompileOptions.fork=true' is invalid.");
      }
      gosuCompiler = new AntGosuCompiler(_antBuilder, spec.getClasspath(), fullClasspath, _taskPath);
    } else {//if(gosuCompileOptions.isFork()) {
      //make GosucCompiler
//      gosuCompiler = new GosucJavaExecCompiler(); //InProcessGosuCompiler();
//      _project.getLogger().info("About to create a DaemonGosuCompiler");
//      gosuCompiler = new DaemonGosuCompiler<>(_project.getRootDir(), new ForkingGosuCompiler(), _compilerDaemonFactory, gosuClasspath.getFiles()); //todo unify with non-forking instantiation
//      _project.getLogger().info("Created a DaemonGosuCompiler");
      _project.getLogger().info("About to create a StupidSimpleGosuCompiler");
      gosuCompiler = new StupidSimpleGosuCompiler(_taskPath);
      _project.getLogger().info("Created a StupidSimpleGosuCompiler");
//    } else { //isUseAnt == false, isFork == false 
//      _project.getLogger().info("About to create InProcessGosuCompiler");
////      CompilerDaemonFactory daemonFactory = _inProcessGosuCompilerDaemonFactory;
//      //gosuCompiler = new DaemonGosuCompiler<>(_project.getRootDir(), new InProcessGosuCompiler(), _inProcessGosuCompilerDaemonFactory, gosuClasspath.getFiles()); //todo unify
//      _project.getLogger().info("Created InProcessGosuCompiler");
    }
    
    return new NormalizingGosuCompiler(gosuCompiler);
  }
}
