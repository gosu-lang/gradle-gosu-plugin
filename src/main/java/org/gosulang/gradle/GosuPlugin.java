package org.gosulang.gradle;

import org.codehaus.groovy.runtime.InvokerHelper;
import org.gosulang.gradle.tasks.GosuRuntime;
import org.gosulang.gradle.tasks.GosuSourceSet;
import org.gosulang.gradle.tasks.gosudoc.GosuDoc;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.*;
import org.gradle.api.plugins.internal.JavaPluginHelper;
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal;
import org.gradle.api.tasks.SourceSet;

import static org.gosulang.gradle.tasks.Util.javaPluginExtension;

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

    GosuRuntime gosuRuntime = project.getExtensions().getByType(GosuRuntime.class);

    SourceSet main = javaPluginExtension(project).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
    SourceSet test = javaPluginExtension(project).getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);

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
    // JvmFeatureInternal mainFeature = JavaPluginHelper.getJavaComponent(project).getMainFeature();//alternative approach but needs to be tested
    // gosuDoc.setClasspath(mainFeature.getSourceSet().getOutput().plus(mainFeature.getSourceSet().getCompileClasspath()));
    SourceSet sourceSet = javaPluginExtension(project).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
    gosuDoc.setClasspath(sourceSet.getOutput().plus(sourceSet.getCompileClasspath()));
    Convention sourceSetConvention = (Convention) InvokerHelper.getProperty(sourceSet, "convention");
    GosuSourceSet gosuSourceSet = sourceSetConvention.getPlugin(GosuSourceSet.class);
    gosuDoc.setSource((Object) gosuSourceSet.getGosu());  // Gradle 4.0 overloads setSource; must upcast to Object for backwards compatibility
  }


}
