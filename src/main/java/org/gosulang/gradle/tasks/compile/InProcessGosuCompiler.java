package org.gosulang.gradle.tasks.compile;

import gw.lang.gosuc.simple.GosuCompiler;
import gw.lang.gosuc.simple.ICompilerDriver;
import gw.lang.gosuc.simple.IGosuCompiler;
import gw.lang.gosuc.simple.SoutCompilerDriver;
import org.codehaus.plexus.util.FileUtils;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.tasks.compile.CompilationFailedException;
import org.gradle.api.internal.tasks.compile.daemon.CompileResult;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class InProcessGosuCompiler implements Compiler<DefaultGosuCompileSpec> {

  private static final Logger LOGGER = Logging.getLogger(InProcessGosuCompiler.class);

  @Override
  public WorkResult execute( DefaultGosuCompileSpec spec ) {
    LOGGER.info("Initializing Gosu compiler...");
    final ICompilerDriver driver = new SoutCompilerDriver();
    final IGosuCompiler gosuc = new GosuCompiler();
    boolean didWork = false;

    List<String> sourceRoots = spec.getSourceRoots().stream()
        .map(File::getAbsolutePath)
        .collect(Collectors.toList());

    if(LOGGER.isInfoEnabled()) {
      LOGGER.info("Gosu compiler using the following source roots:");
      sourceRoots.forEach(LOGGER::info);
    }

    List<String> gosuClasspath = new ArrayList<>();
    spec.getGosuClasspath().forEach(file -> gosuClasspath.add(file.getAbsolutePath()));

    List<String> classpath = new ArrayList<>();
    spec.getClasspath().forEach(file -> classpath.add(file.getAbsolutePath()));

    classpath.addAll(gosuClasspath);
    classpath.addAll(getJreJars());

    gosuc.initializeGosu(sourceRoots, classpath, spec.getDestinationDir().getAbsolutePath());

    FileTree allSourceFiles = spec.getSource().getAsFileTree();
    if(!allSourceFiles.isEmpty()) {
      didWork = true;
    }

    if(LOGGER.isInfoEnabled()) {
      int fileCount = allSourceFiles.getFiles().size();
      LOGGER.info("Compiling " + fileCount + " " +
          "source file" + (fileCount == 1 ? "" : "s") +
          " to " + spec.getDestinationDir().getAbsolutePath());
    }

    boolean isDebug = LOGGER.isDebugEnabled(); 
    allSourceFiles.forEach(sourceFile -> {
      if (isDebug) {
        LOGGER.debug("Compiling Gosu source file: " + sourceFile.getAbsolutePath());
      }
      try {
        gosuc.compile(sourceFile, driver);
      } catch (Exception e) {
        LOGGER.error(e.getMessage());
      }
    });

    gosuc.unitializeGosu();

    boolean errorsInCompilation = ((SoutCompilerDriver) driver).hasErrors();
    List<String> warningMessages = new ArrayList<>();
    List<String> errorMessages = new ArrayList<>();

    ((SoutCompilerDriver) driver).getWarnings().forEach(warning -> warningMessages.add("[WARNING] " + warning));
    int numWarnings = warningMessages.size();

    int numErrors = 0;
    if(errorsInCompilation) {
      ((SoutCompilerDriver) driver).getErrors().forEach(error -> errorMessages.add("[ERROR] " + error));
      numErrors = errorMessages.size();
    }

    boolean hasWarningsOrErrors = numWarnings > 0 || errorsInCompilation;
    StringBuilder sb;
    sb = new StringBuilder();
    sb.append("Gosu compilation completed");
    if(hasWarningsOrErrors) {
      sb.append(" with ");
      if(numWarnings > 0) {
        sb.append(numWarnings).append(" warning").append(numWarnings == 1 ? "" : 's');
      }
      if(errorsInCompilation) {
        sb.append(numWarnings > 0 ? " and " : "");
        sb.append(numErrors).append(" error").append(numErrors == 1 ? "" : 's');
      }
    } else {
      sb.append(" successfully.");
    }
    
    if(LOGGER.isInfoEnabled()) {
        sb.append(hasWarningsOrErrors ? ':' : "");
        LOGGER.info(sb.toString());
        warningMessages.forEach(LOGGER::info);
        errorMessages.forEach(LOGGER::info);
    } else {
      if(hasWarningsOrErrors) {
        sb.append("; rerun with INFO level logging to display details.");
        LOGGER.quiet(sb.toString());
      }
    }

    if(errorsInCompilation) {
      if(spec.getCompileOptions().isFailOnError()) {
        throw new CompilationFailedException();
      } else {
        LOGGER.info("Gosu Compiler: Ignoring compilation failure as 'failOnError' was set to false");
      }
    }

    return new CompileResult(didWork, null);
  }

  /**
   * Get all JARs from the lib directory of the System's java.home property
   * @return List of absolute paths to all JRE libraries
   */
  @SuppressWarnings("unchecked")
  private List<String> getJreJars() {
    File javaHome = new File(System.getProperty("java.home"));
    File libsDir = new File(javaHome + "/lib");
    List<String> classes = new ArrayList<>();
    try {
      classes = FileUtils.getFileNames(libsDir, "**/*.jar", null, true); //gradleApi is using an older version of plexus-utils which does not support generics
    } catch (IOException e) {
      e.printStackTrace();
    }
    return classes;
  }

}
