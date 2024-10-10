package org.gosulang.gradle;

import org.gosulang.gradle.tasks.GosuRuntime;
import org.gosulang.gradle.tasks.GosuSourceDirectorySet;
import org.gosulang.gradle.tasks.gosudoc.GosuDoc;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.internal.JavaPluginHelper;
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import static org.gosulang.gradle.tasks.Util.javaPluginExtension;

public abstract class GosuPlugin implements Plugin<Project> {

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
  private void refreshTestRuntimeClasspath(final Project project) {
    GosuRuntime gosuRuntime = project.getExtensions().getByType(GosuRuntime.class);

    SourceSet main = javaPluginExtension(project).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
    SourceSet test = javaPluginExtension(project).getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);

    test.setRuntimeClasspath(project.files(
      test.getOutput(),
      main.getOutput(),
      project.getConfigurations().getByName(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME),
      gosuRuntime.inferGosuClasspath(project.getConfigurations().getByName(JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME))));
  }

  private void configureGosuDoc(final Project project) {
    TaskProvider<GosuDoc> gosuDoc = project.getTasks().register(GOSUDOC_TASK_NAME, GosuDoc.class, t -> {
      t.setDescription("Generates Gosudoc API documentation for the main source code.");
      t.setGroup(JavaBasePlugin.DOCUMENTATION_GROUP);

      JvmFeatureInternal mainFeature = JavaPluginHelper.getJavaComponent(project).getMainFeature(); //alternative approach but needs to be tested
      t.getClasspath().from(mainFeature.getSourceSet().getOutput().plus(mainFeature.getSourceSet().getCompileClasspath()));

      SourceDirectorySet gosuSourceSet = mainFeature.getSourceSet().getExtensions().getByType(GosuSourceDirectorySet.class);
      t.setSource(gosuSourceSet);
    });
  }

}
