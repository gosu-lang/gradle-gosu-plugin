package org.gosulang.gradle.tasks.compile;

import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import org.gradle.api.file.FileCollection;
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

public class AntGosuCompiler implements Compiler<GosuCompileSpec> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AntGosuCompiler.class);
  
  private final IsolatedAntBuilder _antBuilder;
  private Iterable<File> _compileClasspath;
  private Iterable<File> _gosuClasspath;
  private final String _projectName;
  
  public AntGosuCompiler( IsolatedAntBuilder antBuilder, Iterable<File> compileClasspath, Iterable<File> gosuClasspath ) {
    this( antBuilder, compileClasspath, gosuClasspath, "" );
  }

  public AntGosuCompiler( IsolatedAntBuilder antBuilder, Iterable<File> compileClasspath, Iterable<File> gosuClasspath, String projectName ) {
    _antBuilder = antBuilder;
    _compileClasspath = compileClasspath;
    _gosuClasspath = gosuClasspath;
    _projectName = projectName;
  }
      
  @Override
  public WorkResult execute(GosuCompileSpec spec) {
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

        LOGGER.debug("About to call antBuilder.invokeMethod(\"path\")");
        LOGGER.debug("classpath map {}", classpath);
        
        antBuilder.invokeMethod("path", classpath);

        LOGGER.debug("Finished calling antBuilder.invokeMethod(\"path\")");

        optionsMap.put("destdir", destinationDir.getAbsolutePath());
        optionsMap.put("classpathref", gosuClasspathRefId);
        optionsMap.put("projectname", _projectName);

        LOGGER.debug("Dumping optionsMap:");
        optionsMap.forEach(( key, value ) -> LOGGER.debug('\t' + key + '=' + value));
        
        LOGGER.debug("About to call antBuilder.invokeMethod(\"" + taskName + "\")");

        antBuilder.invokeMethod(taskName, new Object[]{ optionsMap, new Closure<Object>(this, this) {
          public Object doCall(Object ignore) {
            spec.getSource().addToAntBuilder(antBuilder, "src", FileCollection.AntType.MatchingTask);

            return null;
          }
        }});

        return null;
      }
    });

    return new SimpleWorkResult(true);
  }

}
