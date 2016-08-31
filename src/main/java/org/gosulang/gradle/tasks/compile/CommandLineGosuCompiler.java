package org.gosulang.gradle.tasks.compile;

import org.apache.tools.ant.taskdefs.condition.Os;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.internal.tasks.compile.CompilationFailedException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.internal.jvm.Jvm;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.util.GUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class CommandLineGosuCompiler implements Compiler<DefaultGosuCompileSpec> {
  private static final Logger LOGGER = Logging.getLogger(CommandLineGosuCompiler.class);

  
  private final ProjectInternal _project;
  private final DefaultGosuCompileSpec _spec;
  private final String _projectName;
  
  public CommandLineGosuCompiler( ProjectInternal project, DefaultGosuCompileSpec spec, String projectName ) {
    _project = project;
    _spec = spec;
    _projectName = projectName;
  }
  
  @Override
  public WorkResult execute( DefaultGosuCompileSpec spec ) {
    String startupMsg = "Initializing gosuc compiler";
    if(_projectName.isEmpty()) {
      startupMsg += " for " + _projectName;
    }
    LOGGER.info(startupMsg);

    Process gosuc;
    int exitCode;
    String javaExecutable = Jvm.current().getJavaExecutable().getAbsolutePath();

    List<String> args = new ArrayList<>();
    
    args.add(javaExecutable);
    
    //todo custom compiler args, debug etc. from fork options
    ForkOptions forkOptions = spec.getGosuCompileOptions().getForkOptions();

    //respect JAVA_OPTS, if it exists
    String JAVA_OPTS = System.getenv("JAVA_OPTS");
    if(JAVA_OPTS != null) {
      args.add(JAVA_OPTS);
    }
    
    if(forkOptions.getMemoryInitialSize() != null && !forkOptions.getMemoryInitialSize().isEmpty()) {
      args.add("-Xms" + forkOptions.getMemoryInitialSize());
    }

    if(forkOptions.getMemoryMaximumSize() != null && !forkOptions.getMemoryMaximumSize().isEmpty()) {
      args.add("-Xmx" + forkOptions.getMemoryMaximumSize());
    }
    
    args.addAll(forkOptions.getJvmArgs());
    
    if(Os.isFamily(Os.FAMILY_MAC)) {
      args.add("-Xdock:name=Gosuc");
    }

    args.add("-classpath");
    args.add(spec.getGosuClasspath().call().getAsPath());

    args.add("gw.lang.gosuc.cli.CommandLineCompiler");
    
    File argFile;
    try {
      argFile = createArgFile(_spec);
      args.add("@" + argFile.getCanonicalPath().replace(File.separatorChar, '/'));
    } catch (IOException e) {
      LOGGER.error("Error creating argfile with gosuc arguments");
      throw new CompilationFailedException(e);
    }
    
    try {
      LOGGER.quiet(String.format("About to execute gosuc from working directory: %s", _project.getProjectDir()));
      LOGGER.quiet(String.format("Executing command %s", String.join(" ", args)));
      
      gosuc = new ProcessBuilder()
          .directory(_project.getProjectDir())
          .command(args)
          .start();

      exitCode = gosuc.waitFor();
          
    } catch (InterruptedException | IOException e) {
      throw new RuntimeException("gosuc execution failed", e);
    }
    
    //List<String> compilerMessages;
    
    try (BufferedReader stdout = new BufferedReader(new InputStreamReader(gosuc.getInputStream())); 
         BufferedReader stderr = new BufferedReader(new InputStreamReader(gosuc.getErrorStream()))) {
        LOGGER.quiet("Dumping stdout");
        stdout.lines().forEach(LOGGER::quiet);
        LOGGER.quiet("Done dumping stdout");
  
        LOGGER.quiet("Dumping stderr");
        stderr.lines().forEach(LOGGER::quiet);
        LOGGER.quiet("Done dumping stderr");      
      
        //TODO compilerMessages parsing?
      
    } catch (IOException e) {
      e.printStackTrace();
    }

    if(exitCode != 0 ) {
      if(!_spec.getGosuCompileOptions().isFailOnError()) {
        LOGGER.warn(String.format("%s completed with errors, but ignoring as 'gosuOptions.failOnError = false' was specified.", _projectName.isEmpty() ? "gosuc" : _projectName));
      } else {
        throw new CompilationFailedException();
      }
    } else {
      LOGGER.warn(String.format("%s completed successfully.", _projectName.isEmpty() ? "gosuc" : _projectName));
    }
    
    return new SimpleWorkResult(true);
  }

  private File createArgFile(DefaultGosuCompileSpec spec) throws IOException {
    File tempFile;
    if (LOGGER.isDebugEnabled()) {
      tempFile = File.createTempFile(CommandLineGosuCompiler.class.getName(), "arguments", new File(spec.getDestinationDir().getAbsolutePath()));
    } else {
      tempFile = File.createTempFile(CommandLineGosuCompiler.class.getName(), "arguments");
      tempFile.deleteOnExit();
    }

    List<String> fileOutput = new ArrayList<>();

    // The classpath used to initialize Gosu; CommandLineCompiler will supplement this with the JRE jars
    fileOutput.add("-classpath");
    fileOutput.add(String.join(File.pathSeparator, GUtil.asPath(spec.getClasspath())));

    fileOutput.add("-d");
    fileOutput.add(spec.getDestinationDir().getAbsolutePath());

    fileOutput.add("-sourcepath");
    fileOutput.add(String.join(File.pathSeparator, GUtil.asPath(spec.getSourceRoots())));

    if(!spec.getCompileOptions().isWarnings()) {
      fileOutput.add("-nowarn");
    }

//    if(spec.isVerbose()) { //TODO enhance DSL
//      fileOutput.add("-verbose");
//    }

    for(File sourceFile : spec.getSource()) {
      fileOutput.add(sourceFile.getPath());
    }

    Files.write(tempFile.toPath(), fileOutput, StandardCharsets.UTF_8);

    return tempFile;
  }  
  
}

