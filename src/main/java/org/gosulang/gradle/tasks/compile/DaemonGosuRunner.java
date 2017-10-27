package org.gosulang.gradle.tasks.compile;

import org.gradle.tooling.BuildException;

import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public class DaemonGosuRunner implements Runnable {

  private final String[]_args;

  @Inject
  public DaemonGosuRunner(List<String> args) { //TODO inject GosuCompilerSpec, not args
    _args = args.toArray(new String[0]);
  }

  @Override
  public void run() {
//    try {

//      boolean thresholdExceeded = false;

      //TODO reflectively invoke main (args, false)
      try {
        Class<?> clazz = Class.forName("gw.lang.gosuc.cli.CommandLineCompiler");
        Method mainMethod = clazz.getMethod("main", String[].class, boolean.class);
        /*thresholdExceeded = (boolean)*/ mainMethod.invoke(null, _args, false);
      } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
        throw new BuildException(e.getMessage(), e);
      }

      //TODO check failOnError
    
      // TODO coopt summarize() method
//      if(thresholdExceeded) {
//        throw new RuntimeException("Error/warning threshold was exceeded");
//      }
//    } catch (Exception e) {
//      throw new BuildException(e.getMessage(), e);
//    } 
  }
}
