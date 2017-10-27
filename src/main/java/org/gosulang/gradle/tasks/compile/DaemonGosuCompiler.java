package org.gosulang.gradle.tasks.compile;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.internal.jvm.Jvm;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.process.JavaForkOptions;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerConfiguration;
import org.gradle.workers.WorkerExecutor;

import java.util.List;

public class DaemonGosuCompiler implements Compiler<DefaultGosuCompileSpec> {
  private static final Logger LOGGER = Logging.getLogger(DaemonGosuCompiler.class);

  private final ProjectInternal _project;
  private final String _projectName;
  private final WorkerExecutor _workerExecutor;

  DaemonGosuCompiler( ProjectInternal project, String projectName, WorkerExecutor workerExecutor ) {
    _project = project;
    _projectName = projectName;
    _workerExecutor = workerExecutor;
  }

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
      wc.setDisplayName("Daemonized gosuc");

      List<String> args = CommandLineGosuCompiler.formatSpecAsListOfStringArguments(spec);
      
      wc.setParams(_projectName, spec.getGosuCompileOptions().isFailOnError(), args);
      LOGGER.info("Invoking gosuc with arguments: " + args.toString());

      wc.setClasspath(spec.getGosuClasspath().call().plus(_project.files(Jvm.current().getToolsJar())));

      wc.forkOptions( javaForkOptions -> {
        setJvmArgs(spec, javaForkOptions);
        //javaForkOptions.setJvmArgs(Arrays.asList("-Xdebug", "-Xrunjdwp:transport=dt_socket,address=5006,server=y,suspend=y"));
      });
      wc.setIsolationMode(mode);
    });
    
    //_workerExecutor.await();
    
    return WorkResults.didWork(true);
  }
  
  private void setJvmArgs( DefaultGosuCompileSpec spec, JavaForkOptions forkOptions) {
    setMemoryBounds(spec.getGosuCompileOptions().getForkOptions(), forkOptions);
    
    forkOptions.jvmArgs(spec.getGosuCompileOptions().getForkOptions().getJvmArgs()); //TODO confirm if gw, set compiler.type=gw
    
    //respect JAVA_OPTS, if it exists
    final String JAVA_OPTS = "JAVA_OPTS";
    String javaOpts = System.getenv(JAVA_OPTS);
    if(javaOpts != null && !javaOpts.isEmpty()) {
      forkOptions.jvmArgs(javaOpts);
    }
    
  }

  private void setMemoryBounds(ForkOptions spec, JavaForkOptions forkOptions) {
    String xms = spec.getMemoryInitialSize();
    if(xms != null && !xms.isEmpty()) {
      forkOptions.setMinHeapSize(xms);
    }

    String xmx = spec.getMemoryMaximumSize();
    if(xmx != null && !xmx.isEmpty()) {
      forkOptions.setMaxHeapSize(xmx);
    }
  }
  
}
