package org.gosulang.gradle.tasks.compile;

import com.google.common.collect.ImmutableMap;
import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import org.codehaus.groovy.runtime.MethodClosure;
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
  private final Iterable<File> _bootclasspathFiles;
  private final Iterable<File> _extensionDirs;
  private Iterable<File> _gosuClasspath;
  
  public AntGosuCompiler( IsolatedAntBuilder antBuilder, Iterable<File> gosuClasspath ) {
    _gosuClasspath = gosuClasspath;
    _antBuilder = antBuilder;
    _bootclasspathFiles = new ArrayList<>();
    _extensionDirs = new ArrayList<>();
  }
      
  @Override
  public WorkResult execute(DefaultGosuCompileSpec spec) {
    final String gosuClasspathRefId = "gosu.classpath";
    final File destinationDir = spec.getDestinationDir();
    final GosuCompileOptions options = spec.getGosuCompileOptions();
    Map<String, Object> optionsMap = options.optionMap();

    final String taskName = "gosuc";
    Iterable<File> compileClasspath = spec.getClasspath();

    LOGGER.info("Compiling with Ant gosuc task.");
//    LOGGER.info("Ant gosuc task generic options: {}", genericOptions); //TODO change to debug
    LOGGER.info("Ant gosuc task options: {}", optionsMap); //TODO change to debug
    LOGGER.info("_gosuClasspath: {}", _gosuClasspath); //TODO change to debug
    
    _antBuilder.withClasspath(_gosuClasspath).execute(new Closure<Object>(this, this) {
      @SuppressWarnings("UnusedDeclaration")
      public Object doCall( Object it ) {
        final GroovyObjectSupport antBuilder = (GroovyObjectSupport) it;
        
        LOGGER.info("About to call antBuilder.invokeMethod(\"taskdef\")");
        
//        antBuilder.invokeMethod("taskdef", ImmutableMap.of(
//            "resource", "gosu/tools/ant/antlib.xml"
//        ));

        antBuilder.invokeMethod("taskdef", ImmutableMap.of(
            "name", taskName,
            "classname", "gosu.tools.ant.Gosuc"
        ));        
        
        LOGGER.info("Finished calling antBuilder.invokeMethod(\"taskdef\")");
        
        //define the PATH for the classpath
        List<String> gosuClasspathAsStrings = new ArrayList<>();
        _gosuClasspath.forEach(file -> gosuClasspathAsStrings.add(file.getAbsolutePath()));

        Map<String, Object> classpath = new HashMap<>();
        classpath.put("id", gosuClasspathRefId);
        classpath.put("path", String.join(":", gosuClasspathAsStrings));
//        classpath.put("path", '"' + String.join(":", gosuClasspathAsStrings) + '"');
//        _gosuClasspath.forEach(file -> classpath.put("file", file));

        LOGGER.info("About to call antBuilder.invokeMethod(\"path\")");
        LOGGER.info("classpath map {}", classpath);
        
        antBuilder.invokeMethod("path", classpath);

        LOGGER.info("Finished calling antBuilder.invokeMethod(\"path\")");

        LOGGER.info("About to call antBuilder.invokeMethod(\"" + taskName + "\")");

        //define the PATH for the source root(s)
        List<String> srcDirAsStrings = new ArrayList<>();
        spec.getSourceRoots().forEach(file -> srcDirAsStrings.add(file.getAbsolutePath()));

        optionsMap.putAll(ImmutableMap.of(
                "srcdir", String.join(":", srcDirAsStrings),
                "destdir", destinationDir.getAbsolutePath(),
                "classpathref", gosuClasspathRefId
            ));

        LOGGER.info("Dumping optionsMap:");
        optionsMap.forEach( (key, value) -> LOGGER.info('\t' + key + '=' + value));
        
        antBuilder.invokeMethod(taskName, optionsMap);

        return null;
      }
    });

    
    
    
    
    return new SimpleWorkResult(true);
  }

  private class AntClosure extends MethodClosure {

    public AntClosure( Object owner, String method ) {
      super(owner, method);
    }
  }
  
}
