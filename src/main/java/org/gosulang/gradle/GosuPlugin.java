package org.gosulang.gradle;

import org.codehaus.groovy.runtime.InvokerHelper;
import org.gosulang.gradle.tasks.GosuRuntime;
import org.gosulang.gradle.tasks.GosuSourceSet;
import org.gosulang.gradle.tasks.gosudoc.GosuDoc;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;

public class GosuPlugin implements Plugin<Project> {

  @SuppressWarnings("WeakerAccess")
  public static final String GOSUDOC_TASK_NAME = "gosudoc";

  public void apply(Project project) {
    project.getPluginManager().apply(GosuBasePlugin.class);
    project.getPluginManager().apply(JavaPlugin.class);

    refreshTestRuntimeClasspath(project);
    configureGosuDoc(project);
  }

  /**
   * Ensures that the runtime dependency on gosu-core is included the testRuntime's classpath
   */
  private void refreshTestRuntimeClasspath( final Project project ) {
    final JavaPluginExtension pluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
    GosuRuntime gosuRuntime = project.getExtensions().getByType(GosuRuntime.class);

    SourceSet main = pluginExtension.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
    SourceSet test = pluginExtension.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);

    test.setRuntimeClasspath(project.files(
        test.getOutput(),
        main.getOutput(),
        project.getConfigurations().getByName(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME),
        gosuRuntime.inferGosuClasspath(project.getConfigurations().getByName(JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME))));
  }

  private void configureGosuDoc( final Project project ) {
    GosuDoc gosuDoc = project.getTasks().create(GOSUDOC_TASK_NAME, GosuDoc.class);
    gosuDoc.setDescription("Generates Gosudoc API documentation for the main source code.");
    gosuDoc.setGroup(JavaBasePlugin.DOCUMENTATION_GROUP);

    JavaPluginExtension extension = project.getExtensions().getByType(JavaPluginExtension.class);
    SourceSet sourceSet = extension.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
    gosuDoc.setClasspath(sourceSet.getOutput().plus(sourceSet.getCompileClasspath()));

    Convention sourceSetConvention = (Convention) InvokerHelper.getProperty(sourceSet, "convention");
    GosuSourceSet gosuSourceSet = sourceSetConvention.getPlugin(GosuSourceSet.class);

    gosuDoc.setSource((Object) gosuSourceSet.getGosu());  // Gradle 4.0 overloads setSource; must upcast to Object for backwards compatibility
  }

}
