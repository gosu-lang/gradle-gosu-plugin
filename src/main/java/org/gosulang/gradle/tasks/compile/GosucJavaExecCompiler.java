package org.gosulang.gradle.tasks.compile;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.language.base.internal.compile.Compiler;

import org.gradle.api.tasks.JavaExec;

import java.util.Collections;

public class GosucJavaExecCompiler extends JavaExec implements Compiler<DefaultGosuCompileSpec> {

  @Override
  public WorkResult execute( DefaultGosuCompileSpec spec ) {
//    ForkOptions forkOptions = spec.getCompileOptions().getForkOptions();
//    JavaExec fu = new JavaExec();
//    fu.setClasspath(null);
//    fu.setMain("fuuuuu");
//    fu.setArgs(Collections.emptyList());
//    fu.exec();
    
    super.exec();
    return new SimpleWorkResult(true);
  }

//  @Override
//  public void exec() {
//    super.setArgs(getArgs());
//    super.exec();
//  }

}
