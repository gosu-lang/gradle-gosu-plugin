package org.gosulang.gradle.tasks.compile;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import org.codehaus.groovy.runtime.MethodClosure;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.javadoc.Groovydoc;
import org.gradle.language.base.internal.compile.Compiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;

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
    File destinationDir = spec.getDestinationDir();
//    GosuCompileOptions options = spec.getGosuCompileOptions(); //TODO implement GCO
    Object options = "";
    String taskName = "gosuc";
    Iterable<File> compileClasspath = spec.getClasspath();

    LOGGER.info("Compiling with Ant gosuc task.");
    LOGGER.debug("Ant gosuc task options: {}", options);
    
    _antBuilder.withClasspath(_gosuClasspath).execute(new Closure<Object>(this, this) {
      @SuppressWarnings("UnusedDeclaration")
      public Object doCall( Object it ) {
        final GroovyObjectSupport antBuilder = (GroovyObjectSupport) it;

        antBuilder.invokeMethod("taskdef", ImmutableMap.of(
//            "name", taskName,
//            "classname", "org.codehaus.groovy.ant.Groovydoc",
            "resource", "gosu/tools/ant/antlib.xml"
        ));
        
//        antBuilder.invokeMethod(taskName, new Object[]{options, new Closure<Object>(this, this) {
//          public Object doCall( Object ignore ) {
//            
//            return null;
//          }
//        }});

        antBuilder.invokeMethod(taskName, new Object[]{options, ImmutableMap.of(
            "srcdir", "",
            "destdir", "",
            "classpathref", "gosu.classpath",
            "failonerror", "true" //TODO parameterize
        )});


//        antBuilder.invokeMethod("groovydoc", new Object[]{args, new Closure<Object>(this, this) {
//          public Object doCall( Object ignore ) {
//            for (Groovydoc.Link link : links) {
//              antBuilder.invokeMethod("link", new Object[]{
//                  ImmutableMap.of(
//                      "packages", Joiner.on(",").join(link.getPackages()),
//                      "href", link.getUrl()
//                  )
//              });
//            }
//
//            return null;
//          }
//        }});

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
