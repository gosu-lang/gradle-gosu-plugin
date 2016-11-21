package org.gosulang.gradle;

import org.gosulang.gradle.tasks.DefaultGosuSourceSet;
import org.gosulang.gradle.tasks.GosuRuntime;
import org.gosulang.gradle.tasks.GosuSourceSet;
import org.gosulang.gradle.tasks.compile.GosuCompile;
import org.gosulang.gradle.tasks.gosudoc.GosuDoc;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.file.SourceDirectorySetFactory;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.tasks.DefaultSourceSet;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;
import java.io.File;

public class GosuBasePlugin implements Plugin<Project> {
  public static final String GOSU_RUNTIME_EXTENSION_NAME = "gosuRuntime";

  private final SourceDirectorySetFactory sourceDirectorySetFactory;

  private Project _project;
  private GosuRuntime _gosuRuntime;

  @Inject
  GosuBasePlugin(SourceDirectorySetFactory sourceDirectorySetFactory) {
    this.sourceDirectorySetFactory = sourceDirectorySetFactory;
  }

  @Override
  public void apply(Project project) {
    _project = project;
    _project.getPluginManager().apply(JavaBasePlugin.class);

    JavaBasePlugin javaBasePlugin = _project.getPlugins().getPlugin(JavaBasePlugin.class);

    configureGosuRuntimeExtension();
    configureCompileDefaults();
    configureSourceSetDefaults(javaBasePlugin);
    configureGosuDoc();
  }

  private void configureGosuRuntimeExtension() {
    _gosuRuntime = _project.getExtensions().create(GOSU_RUNTIME_EXTENSION_NAME, GosuRuntime.class, _project);
  }

  /**
   * Sets the gosuClasspath property for all GosuCompile tasks: compileGosu and compileTestGosu
   */
  private void configureCompileDefaults() {

    _project.getTasks().withType(GosuCompile.class, gosuCompile -> 
        gosuCompile.getConventionMapping().map("gosuClasspath", () -> _gosuRuntime.inferGosuClasspath(gosuCompile.getClasspath())));
  }

  private void configureSourceSetDefaults(final JavaBasePlugin javaBasePlugin) {
    _project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().all(sourceSet -> {
      DefaultGosuSourceSet gosuSourceSet = new DefaultGosuSourceSet(((DefaultSourceSet) sourceSet).getDisplayName(), sourceDirectorySetFactory);
      new DslObject(sourceSet).getConvention().getPlugins().put("gosu", gosuSourceSet);

      gosuSourceSet.getGosu().srcDir("src/" + sourceSet.getName() + "/gosu");

      sourceSet.getResources().getFilter().exclude(element -> gosuSourceSet.getGosu().contains(element.getFile()));

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

    _project.getTasks().getByName(sourceSet.getClassesTaskName()).dependsOn(compileTaskName);
  }

  private void configureGosuDoc() {
    _project.getTasks().withType(GosuDoc.class, gosudoc -> {
      gosudoc.getConventionMapping().map("gosuClasspath", () -> _gosuRuntime.inferGosuClasspath(gosudoc.getClasspath()));
      gosudoc.getConventionMapping().map("destinationDir", () -> new File(_project.getConvention().getPlugin(JavaPluginConvention.class).getDocsDir(), "gosudoc"));
      gosudoc.getConventionMapping().map("title", () -> _project.getExtensions().getByType(ReportingExtension.class).getApiDocTitle());
      //gosudoc.getConventionMapping().map("windowTitle", (Callable<Object>) () -> _project.getExtensions().getByType(ReportingExtension.class).getApiDocTitle());
    });
  }

}
