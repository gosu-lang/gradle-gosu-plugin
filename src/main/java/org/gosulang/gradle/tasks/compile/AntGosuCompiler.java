package org.gosulang.gradle.tasks.compile;

import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AntGosuCompiler implements Compiler<DefaultGosuCompileSpec> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AntGosuCompiler.class);
  
  private final IsolatedAntBuilder _antBuilder;
  private Iterable<File> _compileClasspath;
  private Iterable<File> _gosuClasspath;
  
  public AntGosuCompiler( IsolatedAntBuilder antBuilder, Iterable<File> compileClasspath, Iterable<File> gosuClasspath) {
    _antBuilder = antBuilder;
    _compileClasspath = compileClasspath;
    _gosuClasspath = gosuClasspath;
  }
      
  @Override
  public WorkResult execute(DefaultGosuCompileSpec spec) {
    final String gosuClasspathRefId = "gosu.classpath";
    final File destinationDir = spec.getDestinationDir();
    final GosuCompileOptions options = spec.getGosuCompileOptions();
    Map<String, Object> optionsMap = options.optionMap();

    final String taskName = "gosuc";

    LOGGER.info("Compiling with Ant gosuc task.");
    LOGGER.info("Ant gosuc task options: {}", optionsMap);
    LOGGER.info("_compileClasspath: {}", _compileClasspath);
    LOGGER.info("_gosuClasspath: {}", _gosuClasspath);

    List<File> jointClasspath = new ArrayList<>();
    _compileClasspath.forEach(jointClasspath::add);
    _gosuClasspath.forEach(jointClasspath::add);

    LOGGER.info("jointClasspath: {}", jointClasspath);
    
    _antBuilder.withClasspath(jointClasspath).execute(new Closure<Object>(this, this) {
      @SuppressWarnings("UnusedDeclaration")
      public Object doCall( Object it ) {
        final GroovyObjectSupport antBuilder = (GroovyObjectSupport) it;
        
        LOGGER.debug("About to call antBuilder.invokeMethod(\"taskdef\")");
        
//        antBuilder.invokeMethod("taskdef", ImmutableMap.of(
//            "resource", "gosu/tools/ant/antlib.xml"
//        ));
        Map<String, Object> taskdefMap = new HashMap<>();
        taskdefMap.put("name", taskName);
        taskdefMap.put("classname", "gosu.tools.ant.Gosuc");  //TODO load from antlib.xml

        antBuilder.invokeMethod("taskdef", taskdefMap);

        LOGGER.debug("Finished calling antBuilder.invokeMethod(\"taskdef\")");
        
        //define the PATH for the classpath
        List<String> gosuClasspathAsStrings = new ArrayList<>();
        jointClasspath.forEach(file -> gosuClasspathAsStrings.add(file.getAbsolutePath()));

        Map<String, Object> classpath = new HashMap<>();
        classpath.put("id", gosuClasspathRefId);
        classpath.put("path", String.join(":", gosuClasspathAsStrings));
//        classpath.put("path", '"' + String.join(":", gosuClasspathAsStrings) + '"');
//        _gosuClasspath.forEach(file -> classpath.put("file", file));

        LOGGER.debug("About to call antBuilder.invokeMethod(\"path\")");
        LOGGER.debug("classpath map {}", classpath);
        
        antBuilder.invokeMethod("path", classpath);

        LOGGER.debug("Finished calling antBuilder.invokeMethod(\"path\")");

        //define the PATH for the source root(s)
        List<String> srcDirAsStrings = new ArrayList<>();
        spec.getSourceRoots().forEach(file -> srcDirAsStrings.add(file.getAbsolutePath()));

        optionsMap.put("srcdir", String.join(":", srcDirAsStrings));
        optionsMap.put("destdir", destinationDir.getAbsolutePath());
        optionsMap.put("classpathref", gosuClasspathRefId);

        LOGGER.debug("Dumping optionsMap:");
        optionsMap.forEach(( key, value ) -> LOGGER.debug('\t' + key + '=' + value));
        
        LOGGER.debug("About to call antBuilder.invokeMethod(\"" + taskName + "\")");
        
        antBuilder.invokeMethod(taskName, optionsMap);

        return null;
      }
    });

    return new SimpleWorkResult(true);
  }

}
