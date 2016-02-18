package org.gosulang.gradle.tasks.compile;

import org.gradle.api.internal.tasks.compile.CompilationFailedException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class StupidSimpleGosuCompiler implements Compiler<GosuCompileSpec>, Serializable {
  private final String _projectName;
  private static final Logger LOGGER = Logging.getLogger(StupidSimpleGosuCompiler.class);

  public StupidSimpleGosuCompiler(String projectName) {
    _projectName = projectName;
  }
  
  @Override
  public WorkResult execute( GosuCompileSpec spec ) {
    LOGGER.info("Initializing stupid-simple Gosu compiler...");

    int exitValue = -1;

    BufferedReader stdout = null, stderr = null;
    
    try {
      String[] env = { 
          "JAVA_HOME=/java/64/jdk1.8.0_45", 
          "PATH=$PATH:$JAVA_HOME/bin"
      };
      
//      String javaExecutable = "/java/64/jdk1.8.0_45/bin/java -Dcompiler.type=gw";
      String javaExecutable = "/java/64/jdk1.8.0_45/bin/java";
      
      List<String> gosuClasspathAsList = new ArrayList<>();
      spec.getGosuClasspath().call().forEach(file -> gosuClasspathAsList.add(file.getAbsolutePath()));
      String gosuClasspath = String.join(":", gosuClasspathAsList);
      
      List<String> classpathAsList = new ArrayList<>();
      spec.getClasspath().forEach(file -> classpathAsList.add(file.getAbsolutePath()));
      String classpath = String.join(":", classpathAsList);

      String main = "gw.lang.gosuc.simple.GosuCompiler";

      List<String> sourceRootsAsList = new ArrayList<>();
      spec.getSourceRoots().forEach(srcDir -> sourceRootsAsList.add(srcDir.getAbsolutePath()));
      String sourceRoots = String.join(File.pathSeparator, sourceRootsAsList);

      List<String> explicitListOfSources = new ArrayList<>();
      spec.getSource().forEach(sourceFile -> explicitListOfSources.add(sourceFile.getAbsolutePath()));
      String sources = String.join(File.pathSeparator, explicitListOfSources);

      //spec.getWorkingDir();

      String argFilename = new File(spec.getWorkingDir(), _projectName + "Args").getAbsolutePath();

      /**
       * 0: CSL of system properties to set (reader will assign -D) ex.: compiler.type=gw, checkedArithmetic=true
       * 1: ':'-delimited list of source roots
       * 2: ':'-delimited list of the classpath for the Gosu compiler
       * 3: absolute path of destination folder
       * 4: ':'-delimited list of explicit source files to compile
       * 5: projectName (optional)
       */
      try (PrintWriter writer = new PrintWriter(argFilename)) {
        writeLine(writer, "compiler.type=gw, checkedArithmetic=false");
        writeLine(writer, sourceRoots);
        writeLine(writer, classpath);
        writeLine(writer, spec.getDestinationDir().getAbsolutePath());
        writeLine(writer, sources);
        writeLine(writer, _projectName);
      }
      
      //this is single-threaded, right?
      StringBuilder exec = new StringBuilder();
//      exec.append(javaExecutable)
//          .append(" -cp ")
//          .append(gosuClasspath)
//          .append(' ')
//          .append(main)
//          .append(' ')
//          .append(sourceRoots)
//          .append(' ')
//          .append(classpath)
//          .append(' ')
//          .append(spec.getDestinationDir().getAbsolutePath())
//          .append(' ')
//          .append(sources)
//          .append(' ')
//          .append(_projectName);      
      exec.append(javaExecutable)
          .append(" -cp ")
          .append(gosuClasspath)
          .append(' ')
          .append(main)
          .append(" @")
          .append(argFilename);

      final String cmd = exec.toString();
      
      LOGGER.info("About to execute:\n" + cmd);
      
//      Process p = Runtime.getRuntime().exec("/java/64/jdk1.8.0_45/bin/java -version", env);
      Process p = Runtime.getRuntime().exec(cmd, env);
      
      exitValue = p.waitFor();
      
      LOGGER.quiet("Dumping stdout");
      stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
      stdout.lines().forEach(LOGGER::quiet);
      LOGGER.quiet("Done dumping stdout");

      LOGGER.quiet("Dumping stderr");
      stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
      stderr.lines().forEach(LOGGER::quiet);
      LOGGER.quiet("Done dumping stderr");
      
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    } finally {
      try {
        stdout.close();
        stderr.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    if (exitValue != 0) { //todo and options.failonerror...
      throw new CompilationFailedException();
    }

    return null;
  }

  private void writeLine( PrintWriter writer, String s ) {
    writer.write(s + '\n');
  }

}
