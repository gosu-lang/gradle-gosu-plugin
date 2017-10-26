package org.gosulang.gradle.tasks.compile;

import gw.lang.gosuc.cli.CommandLineOptions;
import gw.internal.ext.com.beust.jcommander.JCommander;
//import gw.lang.gosuc.simple.ICompilerDriver;
import gw.lang.gosuc.simple.SoutCompilerDriver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.internal.jvm.Jvm;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerConfiguration;
import org.gradle.workers.WorkerExecutor;
import org.gradle.language.base.internal.compile.Compiler;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


public class DaemonGosuCompiler implements Compiler<DefaultGosuCompileSpec> {
  private static final Logger LOGGER = Logging.getLogger(DaemonGosuCompiler.class);

  private final ProjectInternal _project;
//  private final DefaultGosuCompileSpec _spec;
  private final String _projectName;
  
  private final WorkerExecutor _workerExecutor;

  public DaemonGosuCompiler( ProjectInternal project, String projectName, WorkerExecutor workerExecutor ) {
    _project = project;
//    _spec = spec;
    _projectName = projectName;
    _workerExecutor = workerExecutor;
  }

//  private WorkerExecutor getWorkerExecutor() {
//    throw new UnsupportedOperationException();
//  }

  @Override
  public WorkResult execute( DefaultGosuCompileSpec spec ) {
    String startupMsg = "Initializing gosuc compiler";
    if(_projectName.isEmpty()) {
      startupMsg += " for " + _projectName;
    }
    LOGGER.info(startupMsg);
    
    final IsolationMode mode = spec.getGosuCompileOptions().isFork() ? IsolationMode.PROCESS : IsolationMode.CLASSLOADER;
    LOGGER.quiet("Executing gosuc with IsolationMode: " + mode.name());
    
    _workerExecutor.submit(DaemonGosuRunner.class, ( WorkerConfiguration wc ) -> {
      LOGGER.quiet("Setting displayname");
      wc.setDisplayName("Daemonized gosuc");
      String[] args = CommandLineGosuCompiler.formatSpecAsListOfStringArguments(spec).toArray(new String[0]);
      CommandLineOptions options = new CommandLineOptions();
      new JCommander( options, args );
      gw.lang.gosuc.simple.ICompilerDriver driver = new SoutCompilerDriver();
      
      wc.setParams(options, driver);
      wc.setClasspath(spec.getGosuClasspath().call().plus(_project.files(Jvm.current().getToolsJar())));
      wc.forkOptions( javaForkOptions -> {
        javaForkOptions.systemProperty("compiler.type", "gw");
        //respect JAVA_OPTS, if it exists
        final String JAVA_OPTS = "JAVA_OPTS";
        String javaOpts = System.getenv(JAVA_OPTS);
        if(javaOpts != null && !javaOpts.isEmpty()) {
          Map<String, String> env = new HashMap<>();
          env.put(JAVA_OPTS, javaOpts);
          javaForkOptions.setEnvironment(env);
        }
        //javaForkOptions.setJvmArgs(Arrays.asList("-Xdebug", "-Xrunjdwp:transport=dt_socket,address=5006,server=y,suspend=y"));
      });
      
      wc.setIsolationMode(mode);
    });
    
    //_workerExecutor.await();
    
    return WorkResults.didWork(true);
  }
  
}
