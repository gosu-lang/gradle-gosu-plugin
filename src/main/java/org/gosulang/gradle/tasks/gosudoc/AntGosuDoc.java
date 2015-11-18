package org.gosulang.gradle.tasks.gosudoc;

import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AntGosuDoc {
  private static final Logger LOGGER = LoggerFactory.getLogger(AntGosuDoc.class);

  private final IsolatedAntBuilder _antBuilder;
  private final Iterable<File> _bootclasspathFiles;
  private final Iterable<File> _extensionDirs;
  //private final Iterable<Object> _filters; //TODO add me
//  private Iterable<File> _gosuClasspath;

  public AntGosuDoc( IsolatedAntBuilder antBuilder ) {
    this(antBuilder, Collections.emptyList(), Collections.emptyList());
  }

  public AntGosuDoc( IsolatedAntBuilder antBuilder, Iterable<File> bootclasspathFiles, Iterable<File> extensionDirs ) {
    _antBuilder = antBuilder;
    _bootclasspathFiles = bootclasspathFiles;
    _extensionDirs = extensionDirs;
  }

  public WorkResult execute(FileCollection source, File targetDir, Iterable<File> classpathFiles, Iterable<File> gosuClasspath, GosuDocOptions options, Project project) {
    final String gosuClasspathRefId = "gosu.classpath";
    Map<String, Object> optionsMap = options.optionMap();

    final String taskName = "gosudoc";
    
    LOGGER.info("Creating GosuDoc Ant gosudoc task.");
    LOGGER.info("Ant gosudoc task options: {}", optionsMap);
   
    List<File> jointClasspath = new ArrayList<>();
    jointClasspath.add(getToolsJar());
    classpathFiles.forEach(jointClasspath::add);
    gosuClasspath.forEach(jointClasspath::add);
    LOGGER.info("Ant gosudoc jointClasspath: {}", jointClasspath);
    
    //'source' is a FileCollection with explicit paths.
    // We don't want that, so instead we create a temp directory with the contents of 'source'
    // Copying 'source' to the temp dir should honor its include/exclude patterns
    // Finally, the tmpdir will be the sole inputdir passed to the gosudoc task

    final File tmpDir = new File(project.getBuildDir(), "tmp/gosudoc");
    FileOperations fileOperations = (ProjectInternal) project;
    fileOperations.delete(tmpDir);
    fileOperations.copy(copySpec -> copySpec.from(source).into(tmpDir));
    
    _antBuilder.withClasspath(jointClasspath).execute(new Closure<Object>(this, this) {
      @SuppressWarnings("UnusedDeclaration")
      public Object doCall( Object it ) {
        final GroovyObjectSupport antBuilder = (GroovyObjectSupport) it;

        LOGGER.info("About to call antBuilder.invokeMethod(\"taskdef\")");

        Map<String, Object> taskdefMap = new HashMap<>();
        taskdefMap.put("name", taskName);
        taskdefMap.put("classname", "gosu.tools.ant.Gosudoc"); //TODO load from antlib.xml

        antBuilder.invokeMethod("taskdef", taskdefMap);

        LOGGER.info("Finished calling antBuilder.invokeMethod(\"taskdef\")");
        
        //define the PATH for the classpath
        List<String> gosuClasspathAsStrings = new ArrayList<>();
        jointClasspath.forEach(file -> gosuClasspathAsStrings.add(file.getAbsolutePath()));

        Map<String, Object> classpath = new HashMap<>();
        classpath.put("id", gosuClasspathRefId);
        classpath.put("path", String.join(":", gosuClasspathAsStrings));

        LOGGER.info("About to call antBuilder.invokeMethod(\"path\")");
        LOGGER.info("classpath map {}", classpath);

        antBuilder.invokeMethod("path", classpath);

        LOGGER.info("Finished calling antBuilder.invokeMethod(\"path\")");
        
        optionsMap.put("inputdirs", tmpDir.getAbsolutePath()); //TODO or use 'String.join(":", srcDirAsStrings)' ??
        optionsMap.put("outputdir", targetDir);
        optionsMap.put("verbose", "true");
        optionsMap.put("classpathref", gosuClasspathRefId);

        LOGGER.info("Dumping optionsMap:");
        optionsMap.forEach(( key, value ) -> LOGGER.info('\t' + key + '=' + value));

        LOGGER.debug("About to call antBuilder.invokeMethod(\"" + taskName + "\")");
        
        antBuilder.invokeMethod(taskName, optionsMap);

        return null;
      }
    });

    return new SimpleWorkResult(true);
  }

  /**
   * Get all tools.jar from the lib directory of the System's java.home property
   * @return File reference to tools.jar
   */
  private File getToolsJar() {
    String javaHome = System.getProperty("java.home");
    java.nio.file.Path libsDir = FileSystems.getDefault().getPath(javaHome, "/lib");
    return new File(libsDir.toFile(), "tools.jar");
  }

}
