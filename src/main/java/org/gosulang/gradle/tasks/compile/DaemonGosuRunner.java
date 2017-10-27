package org.gosulang.gradle.tasks.compile;

import org.gradle.api.internal.tasks.compile.CompilationFailedException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.tooling.BuildException;

import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public class DaemonGosuRunner implements Runnable {
  private static final Logger LOGGER = Logging.getLogger(DaemonGosuRunner.class);

  private final String _projectName;
  private final boolean _failOnError;
  private final String[] _args;

  @Inject
  public DaemonGosuRunner(String projectName, boolean failOnError, List<String> args) {
    _projectName = projectName;
    _failOnError = failOnError;
    _args = args.toArray(new String[0]);
  }

  @Override
  public void run() {
    String exceptionMsg = null;
    try {
      Class<?> clazz = Class.forName("gw.lang.gosuc.cli.CommandLineCompiler");
      exceptionMsg = (String) clazz.getField("COMPILE_EXCEPTION_MSG").get(null);
      Method mainMethod = clazz.getMethod("main", String[].class, boolean.class);
      mainMethod.invoke(null, _args, false);
    } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | NoSuchFieldException e) {
      throw new BuildException(e.getMessage(), e);
    } catch (InvocationTargetException e) {
      if(e.getCause() != null && e.getCause() instanceof RuntimeException && !e.getCause().getMessage().equals(exceptionMsg)) {
        throw new BuildException(e.getMessage(), e);
      }
      if(!_failOnError) {
        LOGGER.quiet(String.format("%s completed with errors, but ignoring as 'gosuOptions.failOnError = false' was specified.", _projectName.isEmpty() ? "gosuc" : _projectName));
      } else {
        throw new CompilationFailedException();
      }
    }
  }
  
}
