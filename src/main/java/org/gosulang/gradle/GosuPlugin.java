package org.gosulang.gradle;

import org.gosulang.gradle.tasks.GosuRuntime;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.testing.Test;

import java.util.Set;
import java.util.concurrent.Callable;

public class GosuPlugin implements Plugin<Project> {

  private Project _project;

  public void apply(Project project) {
    _project = project;
    project.getPluginManager().apply(GosuBasePlugin.class);
    project.getPluginManager().apply(JavaPlugin.class);

    configureTestRuntime();

//    addGosuRuntimeDependencies();
  }

  private void configureTestRuntime() {
    GosuRuntime gosuRuntime = _project.getExtensions().getByType(GosuRuntime.class);
    System.out.println("delete me! Test task count: " + _project.getTasks().withType(Test.class).size());
    Test theTask = _project.getTasks().withType(Test.class).iterator().next();
    System.out.println("using theTask.getClasspath(): " + theTask.getClasspath().getFiles());
    System.out.println("using theTask.getBootStrapClasspath(): " + theTask.getBootstrapClasspath().getFiles());

    _project.getTasks().withType(Test.class, testTask -> {
      testTask.getConventionMapping().map("bootstrapClasspath", () -> {
//        System.out.println("using testTask.getClasspath(): " + testTask.getClasspath().getFiles());
//        System.out.println("using testTask.getBootStrapClasspath(): " + testTask.getBootstrapClasspath().getFiles());
//        System.out.println("inferred runtime from bootstrapclasspath: " + gosuRuntime.inferGosuClasspath(testTask.getBootstrapClasspath()));
//        System.out.println("inferred runtime from classpath: " + gosuRuntime.inferGosuClasspath(testTask.getClasspath()));
        return gosuRuntime.inferGosuClasspath(testTask.getBootstrapClasspath());
      });
    });

//    theTask = _project.getTasks().withType(Test.class).iterator().next();
//    System.out.println("After config; using theTask.getBootStrapClasspath(): " + theTask.getBootstrapClasspath().getFiles());
  
  }
  
//  private void addGosuRuntimeDependencies() {
//    Set<ResolvedArtifact> buildScriptDeps = _project.getBuildscript().getConfigurations().getByName("classpath").getResolvedConfiguration().getResolvedArtifacts();
//    ResolvedArtifact gosuCore = GosuPlugin.getArtifactWithName("gosu-core", buildScriptDeps);
//    ResolvedArtifact gosuCoreApi = GosuPlugin.getArtifactWithName("gosu-core-api", buildScriptDeps);
//
//    //inject Gosu jar dependencies into the classpath of the project implementing this plugin
//    if(gosuCore != null) {
//      _project.getDependencies().add("runtime", gosuCore.getModuleVersion().getId().toString());
//    }
//    if(gosuCoreApi != null) {
//      _project.getDependencies().add("compile", gosuCoreApi.getModuleVersion().getId().toString());
//    }
//  }
//
//  public static ResolvedArtifact getArtifactWithName(final String name, final Set<ResolvedArtifact> artifacts) {
//    ResolvedArtifact retval = null;
//    for (ResolvedArtifact artifact : artifacts) {
//      if (artifact.getName().equals(name)) {
//        retval = artifact;
//      }
//    }
//    return retval;
//    //throw new IllegalStateException("Could not find a dependency with name " + name);
//  }

}
