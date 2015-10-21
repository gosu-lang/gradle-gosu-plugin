package org.gosulang.gradle;

import org.gosulang.gradle.tasks.GosuRuntime;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;

public class GosuPlugin implements Plugin<Project> {

  public void apply(Project project) {
    project.getPluginManager().apply(GosuBasePlugin.class);
    project.getPluginManager().apply(JavaPlugin.class);

    JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);

    refreshTestRuntimeClasspath(javaConvention);
  }

  /**
   * Ensures that the runtime dependency on gosu-core is included the testRuntime's classpath
   * @param pluginConvention
   */
  private void refreshTestRuntimeClasspath( final JavaPluginConvention pluginConvention ) {
    final Project project = pluginConvention.getProject();
    GosuRuntime gosuRuntime = project.getExtensions().getByType(GosuRuntime.class);

    SourceSet main = pluginConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
    SourceSet test = pluginConvention.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);

    test.setRuntimeClasspath(project.files(
        test.getOutput(), 
        main.getOutput(), 
        project.getConfigurations().getByName(JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME),
        gosuRuntime.inferGosuClasspath(project.getConfigurations().getByName(JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME))));
  }

}
