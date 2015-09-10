package org.gosulang.gradle;

import org.gosulang.gradle.tasks.DefaultGosuSourceSet;
import org.gosulang.gradle.tasks.GosuSourceSet;
import org.gosulang.gradle.tasks.compile.GosuCompile;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.tasks.DefaultSourceSet;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;
import java.util.Set;

public class GosuBasePlugin implements Plugin<Project> {

  private final FileResolver _fileResolver;
  private Project _project;

  @Inject
  GosuBasePlugin(FileResolver fileResolver) {
    _fileResolver = fileResolver;
  }

  @Override
  public void apply(Project project) {
    _project = project;
    _project.getPluginManager().apply(JavaBasePlugin.class);

    JavaBasePlugin javaBasePlugin = _project.getPlugins().getPlugin(JavaBasePlugin.class);

    configureCompileDefaults();
    configureSourceSetDefaults(javaBasePlugin);
    addGosuRuntimeDependencies();
  }

  private void configureCompileDefaults() {
    _project.getTasks().withType(GosuCompile.class, gosuCompile -> {
      gosuCompile.getConventionMapping().map("gosuClasspath", () -> {
        FileCollection cp = gosuCompile.getClasspath();
        return cp;
      });
    });
  }

  private void configureSourceSetDefaults(final JavaBasePlugin javaBasePlugin) {
    _project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().all( sourceSet -> {
      DefaultGosuSourceSet gosuSourceSet = new DefaultGosuSourceSet(((DefaultSourceSet) sourceSet).getDisplayName(), _fileResolver);
      new DslObject(sourceSet).getConvention().getPlugins().put("gosu", gosuSourceSet);

      gosuSourceSet.getGosu().srcDir("src/" + sourceSet.getName() + "/gosu");

      sourceSet.getResources().getFilter().exclude( element -> {
        return gosuSourceSet.getGosu().contains(element.getFile());
      });

      sourceSet.getAllSource().source(gosuSourceSet.getGosu());

      configureGosuCompile(javaBasePlugin, sourceSet, gosuSourceSet);
    });
  }

  private void configureGosuCompile(JavaBasePlugin javaPlugin, SourceSet sourceSet, GosuSourceSet gosuSourceSet) {
    String compileTaskName = sourceSet.getCompileTaskName("gosu");
    GosuCompile gosuCompile = _project.getTasks().create(compileTaskName, GosuCompile.class);
    javaPlugin.configureForSourceSet(sourceSet, gosuCompile);
    gosuCompile.dependsOn(sourceSet.getCompileJavaTaskName());
    gosuCompile.setDescription("Compiles the " + sourceSet.getName() + " Gosu source");
    gosuCompile.setSource(gosuSourceSet.getGosu());
    gosuCompile.setSourceRoots(gosuSourceSet.getGosu().getSrcDirs());

    _project.getTasks().getByName(sourceSet.getClassesTaskName()).dependsOn(compileTaskName);
  }

  private void addGosuRuntimeDependencies() {
    Set<ResolvedArtifact> buildScriptDeps = _project.getBuildscript().getConfigurations().getByName("classpath").getResolvedConfiguration().getResolvedArtifacts();
    ResolvedArtifact gosuCore = GosuBasePlugin.getArtifactWithName("gosu-core", buildScriptDeps);
    ResolvedArtifact gosuCoreApi = GosuBasePlugin.getArtifactWithName("gosu-core-api", buildScriptDeps);

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
