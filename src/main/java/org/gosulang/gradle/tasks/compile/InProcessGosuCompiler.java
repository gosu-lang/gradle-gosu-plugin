package org.gosulang.gradle.tasks.compile;

import org.gradle.api.file.FileTree;
import org.gradle.api.internal.tasks.compile.CompilationFailedException;
import org.gradle.api.internal.tasks.compile.daemon.CompileResult;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @deprecated Use AntGosuCompiler instead
 */
@Deprecated
public class InProcessGosuCompiler implements Compiler<GosuCompileSpec>, Serializable {

  private static final Logger LOGGER = Logging.getLogger(InProcessGosuCompiler.class);

  @Override
  public WorkResult execute( GosuCompileSpec spec ) {
    LOGGER.info("Initializing Gosu compiler...");

    Class<?> driverIF = null;
    Object driver = null;
    Object gosuc = null;

    try {
      driverIF = Class.forName("gw.lang.gosuc.simple.ICompilerDriver");
      driver = Class.forName("gw.lang.gosuc.simple.SoutCompilerDriver").newInstance();
      gosuc = Class.forName("gw.lang.gosuc.simple.GosuCompiler").newInstance();
    }
    catch(ClassNotFoundException | InstantiationException | IllegalAccessException e) {
      LOGGER.error(e.getMessage());
      throw new RuntimeException(e.getClass() + ": gosu-core-api not on classpath: " + e.getMessage()); //FIXME
    }

//    final ICompilerDriver driver = new SoutCompilerDriver();
//    final IGosuCompiler gosuc = new GosuCompiler();
    boolean didWork = false;

    List<String> sourceRoots = spec.getSourceRoots().stream()
        .map(File::getAbsolutePath)
        .collect(Collectors.toList());

    if(LOGGER.isInfoEnabled()) {
      LOGGER.info("Gosu compiler using the following source roots:");
      sourceRoots.forEach(LOGGER::info);
    }

    List<String> gosuClasspath = new ArrayList<>();
//    spec.getGosuClasspath().forEach(file -> gosuClasspath.add(file.getAbsolutePath()));

    List<String> classpath = new ArrayList<>();
    spec.getClasspath().forEach(file -> classpath.add(file.getAbsolutePath()));

    classpath.addAll(gosuClasspath);
    classpath.addAll(getJreJars());

    Method initializeGosuMethod = null;
    try {
      LOGGER.quiet("gosuc: " + gosuc);
      LOGGER.quiet("gosuc.getClass(): " + gosuc.getClass());
      initializeGosuMethod = gosuc.getClass().getMethod("initializeGosu", List.class, List.class, String.class);
      initializeGosuMethod.invoke(gosuc, sourceRoots, classpath, spec.getDestinationDir().getAbsolutePath());
    }
    catch(NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      Throwable cause = e.getCause(); //FIXME
      LOGGER.error(cause.getMessage());
      LOGGER.error(e.getMessage());
    }

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

    Method compileMethod = null;
    boolean isDebug = LOGGER.isDebugEnabled();
    try {
      if(driverIF.getClass() == null || driver.getClass() == null || gosuc.getClass() == null) {
        System.out.println("**************************************");
      }
      LOGGER.quiet("driverIF: " + driverIF);
      LOGGER.quiet("driver.getClass(): " + driver.getClass());
      LOGGER.quiet("gosuc.getClass(): " + gosuc.getClass());
      compileMethod = gosuc.getClass().getMethod("compile", File.class, driverIF);//driver.getClass());
      LOGGER.quiet("compileMethod.toString(): " + compileMethod.toString());
    }
    catch (NoSuchMethodException e) {
      Throwable cause = e.getCause(); //FIXME
//      LOGGER.error(cause.getMessage());
      LOGGER.error(e.getMessage());
    }
      for(File sourceFile : allSourceFiles) {
      if (isDebug) {
        LOGGER.debug("Compiling Gosu source file: " + sourceFile.getAbsolutePath());
      }
      try {
        LOGGER.quiet("driver.getClass(): " + driver.getClass());
        LOGGER.quiet("driver: " + driver);
        LOGGER.quiet("sourceFile: " + sourceFile);
        LOGGER.quiet("compileMethod: " + compileMethod);
        compileMethod.invoke(gosuc, sourceFile, driver);
      } catch (InvocationTargetException | IllegalAccessException e) {
        Throwable cause = e.getCause(); //FIXME
        LOGGER.error(cause.getMessage());
        LOGGER.error(e.getMessage());
      }
    }

    Method uninitializeGosuMethod = null;
    try {
      uninitializeGosuMethod = gosuc.getClass().getMethod("unitializeGosu");
      uninitializeGosuMethod.invoke(gosuc);
    }
    catch(NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      Throwable cause = e.getCause(); //FIXME
      LOGGER.error(cause.getMessage());
      LOGGER.error(e.getMessage());
    }

    Method hasErrorsMethod, getWarningsMethod, getErrorsMethod;
    boolean errorsInCompilation = false;
    List<String> warnings = null;
    List<String> errors = null;
    try {
      hasErrorsMethod = driver.getClass().getMethod("hasErrors");
      getWarningsMethod = driver.getClass().getMethod("getWarnings");
      getErrorsMethod = driver.getClass().getMethod("getErrors");
      errorsInCompilation = (boolean) hasErrorsMethod.invoke(driver);
      warnings = (List<String>) getWarningsMethod.invoke(driver);
      errors = (List<String>) getErrorsMethod.invoke(driver);
    }
    catch(NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      Throwable cause = e.getCause(); //FIXME
      LOGGER.error(cause.getMessage());
      LOGGER.error(e.getMessage());
    }

    List<String> warningMessages = new ArrayList<>();
    List<String> errorMessages = new ArrayList<>();

    warnings.forEach(warning -> warningMessages.add("[WARNING] " + warning));
    int numWarnings = warningMessages.size();

    int numErrors = 0;
    if(errorsInCompilation) {
     errors.forEach(error -> errorMessages.add("[ERROR] " + error));
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
      if(spec.getGosuCompileOptions().isFailOnError()) {
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
  private List<String> getJreJars() {
    String javaHome = System.getProperty("java.home");
    Path libsDir = FileSystems.getDefault().getPath(javaHome, "/lib");
    try {
      return Files.walk(libsDir)
          .filter( path -> path.toFile().isFile())
          .filter( path -> path.toString().endsWith(".jar"))
          .map( java.nio.file.Path::toString )
          .collect(Collectors.toList());
    } catch (SecurityException | IOException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

}
