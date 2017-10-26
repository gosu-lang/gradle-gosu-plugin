package org.gosulang.gradle.tasks.compile;

import gw.lang.gosuc.cli.CommandLineCompiler;
import gw.lang.gosuc.cli.CommandLineOptions;
import gw.lang.gosuc.simple.ICompilerDriver;
import gw.lang.gosuc.simple.SoutCompilerDriver;
import org.gradle.tooling.BuildException;

import javax.inject.Inject;

public class DaemonGosuRunner implements Runnable {

  private final CommandLineOptions _options;
  private final ICompilerDriver _driver;

  @Inject
  public DaemonGosuRunner( CommandLineOptions options, ICompilerDriver driver) {
    _options = options;
    _driver = driver;
  }

  @Override
  public void run() {
    try {
      boolean thresholdExceeded = CommandLineCompiler.invoke(_options, (SoutCompilerDriver) _driver);
      // TODO coopt summarize() method
      if(thresholdExceeded) {
        throw new RuntimeException("Error/warning threshold was exceeded");
      }
    } catch (Exception e) {
      throw new BuildException(e.getMessage(), e);
    } 
  }
}
