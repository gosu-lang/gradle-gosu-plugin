package org.gosulang.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.plugins.JavaPlugin;

import java.util.Set;

public class GosuPlugin implements Plugin<Project> {

  private Project _project;

  public void apply(Project project) {
    _project = project;
    project.getPluginManager().apply(GosuBasePlugin.class);
    project.getPluginManager().apply(JavaPlugin.class);

    addGosuRuntimeDependencies();
  }

  private void addGosuRuntimeDependencies() {
    Set<ResolvedArtifact> buildScriptDeps = _project.getBuildscript().getConfigurations().getByName("classpath").getResolvedConfiguration().getResolvedArtifacts();
    ResolvedArtifact gosuCore = GosuPlugin.getArtifactWithName("gosu-core", buildScriptDeps);
    ResolvedArtifact gosuCoreApi = GosuPlugin.getArtifactWithName("gosu-core-api", buildScriptDeps);

    //inject Gosu jar dependencies into the classpath of the project implementing this plugin
    if(gosuCore != null) {
      _project.getDependencies().add("runtime", gosuCore.getModuleVersion().getId().toString());
    }
    if(gosuCoreApi != null) {
      _project.getDependencies().add("compile", gosuCoreApi.getModuleVersion().getId().toString());
    }
  }

  public static ResolvedArtifact getArtifactWithName(final String name, final Set<ResolvedArtifact> artifacts) {
    ResolvedArtifact retval = null;
    for (ResolvedArtifact artifact : artifacts) {
      if (artifact.getName().equals(name)) {
        retval = artifact;
      }
    }
    return retval;
    //throw new IllegalStateException("Could not find a dependency with name " + name);
  }

}
