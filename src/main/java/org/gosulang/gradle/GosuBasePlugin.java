package org.gosulang.gradle;


import org.gosulang.gradle.tasks.DefaultGosuSourceSet;
import org.gosulang.gradle.tasks.GosuRuntime;
import org.gosulang.gradle.tasks.GosuSourceDirectorySet;
import org.gosulang.gradle.tasks.compile.GosuCompile;
import org.gosulang.gradle.tasks.gosudoc.GosuDoc;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.tasks.DefaultSourceSetOutput;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.internal.Cast;

import javax.inject.Inject;
import java.util.concurrent.Callable;

import static org.gosulang.gradle.tasks.Util.javaPluginExtension;
import static org.gradle.api.internal.lambdas.SerializableLambdas.spec;

public abstract class GosuBasePlugin implements Plugin<Project> {
  public static final String GOSU_RUNTIME_EXTENSION_NAME = "gosuRuntime";

  @Inject
  public abstract ObjectFactory getObjectFactory();

  @Override
  public void apply(Project project) {
    project.getPluginManager().apply(JavaBasePlugin.class);

    GosuRuntime gosuRuntime = project.getExtensions().create(GOSU_RUNTIME_EXTENSION_NAME, GosuRuntime.class);
    configureCompileDefaults(project, gosuRuntime);
    configureSourceSetDefaults(project);
    configureGosuDoc(project, gosuRuntime);
  }

  /**
   * Sets the gosuClasspath property for all GosuCompile tasks: compileGosu and compileTestGosu
   */
  private static void configureCompileDefaults(Project project, GosuRuntime gosuRuntime) {
    project.getTasks().withType(GosuCompile.class).configureEach(gosuCompile -> {
        gosuCompile.getGosuClasspath().convention((Callable<FileCollection>) () -> gosuRuntime.inferGosuClasspath(gosuCompile.getClasspath()));
    });
  }

 private void configureSourceSetDefaults(Project project) {
     javaPluginExtension(project).getSourceSets().all(sourceSet -> {

         GosuSourceDirectorySet gosuSource = getGosuSourceDirectorySet(sourceSet);
         sourceSet.getExtensions().add(GosuSourceDirectorySet.class, "gosu", gosuSource);
         gosuSource.srcDir("src/" + sourceSet.getName() + "/gosu");

         // Explicitly capture only a FileCollection in the lambda below for compatibility with configuration-cache.
         final FileCollection gosuSourceFiles = gosuSource;
         sourceSet.getResources().getFilter().exclude(
             spec(element -> gosuSourceFiles.contains(element.getFile()))
         );
         sourceSet.getAllSource().source(gosuSource);
      configureGosuCompile(project, sourceSet, gosuSource);
    });
  }

  private GosuSourceDirectorySet getGosuSourceDirectorySet(SourceSet sourceSet) {
      final DefaultGosuSourceSet gosuSourceSet = getObjectFactory().newInstance(DefaultGosuSourceSet.class, sourceSet.getName());

      // TODO remove in Gradle 9.0?
      new DslObject(sourceSet).getConvention().getPlugins().put("gosu", gosuSourceSet);

      return gosuSourceSet.getGosu();
  }

  /**
   * Create and configure default compileGosu and compileTestGosu tasks
   * Gradle 4.0+: call local equivalent of o.g.a.p.i.SourceSetUtil.configureForSourceSet(sourceSet, gosuSourceSet.getGosu(), gosuCompile, _project)
   * Gradle 2.x, 3.x: call javaPlugin.configureForSourceSet(sourceSet, gosuCompile);
   */
  private void configureGosuCompile(Project project, SourceSet sourceSet, GosuSourceDirectorySet gosuSourceSet) {
    String compileTaskName = sourceSet.getCompileTaskName("gosu");
    TaskProvider<GosuCompile> gosuCompile = project.getTasks().register(compileTaskName, GosuCompile.class);
    configureForSourceSet(sourceSet, gosuSourceSet, gosuCompile, project);
    gosuCompile.configure(t -> t.dependsOn(sourceSet.getCompileJavaTaskName()));
    gosuCompile.configure(t -> t.setSource((Object) gosuSourceSet)); // Gradle 4.0 overloads setSource; must upcast to Object for backwards compatibility
    gosuCompile.configure(t -> {
        t.getProjectName().set(t.getProject().getName());
        t.getProjectDir().set(t.getProject().getLayout().getProjectDirectory());
    });
    project.getTasks().named(sourceSet.getClassesTaskName()).configure(task -> task.dependsOn(compileTaskName));
  }

  private static void configureGosuDoc(Project project, GosuRuntime gosuRuntime) {
    project.getTasks().withType(GosuDoc.class, gosudoc -> {
      gosudoc.getProjectName().set(project.getName());
      gosudoc.getProjectDir().set(project.getLayout().getProjectDirectory());
      gosudoc.getBuildDir().set(project.getLayout().getBuildDirectory());
      gosudoc.getGosuClasspath().convention((Callable<FileCollection>) () -> gosuRuntime.inferGosuClasspath(gosudoc.getClasspath()));
      gosudoc.getDestinationDir().convention(javaPluginExtension(project).getDocsDir().dir("gosudoc"));
      gosudoc.getTitle().convention(project.getExtensions().getByType(ReportingExtension.class).getApiDocTitle());
    });
  }

  private static void configureForSourceSet(final SourceSet sourceSet, final GosuSourceDirectorySet sourceDirectorySet, TaskProvider<? extends AbstractCompile> compile, final Project target) {
    compile.configure(t -> {
      t.setDescription("Compiles the " + sourceDirectorySet.getDisplayName() + ".");
      t.setSource(sourceSet.getJava());
      t.getConventionMapping().map("classpath", () -> sourceSet.getCompileClasspath().plus(target.files(sourceSet.getJava().getDestinationDirectory())));
    });
    configureOutputDirectoryForSourceSet(sourceSet, sourceDirectorySet, target, compile);
  }

 private static void configureOutputDirectoryForSourceSet(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, final Project target, TaskProvider<? extends AbstractCompile> compileTask) {
    final String sourceSetChildPath = "classes/" + sourceDirectorySet.getName() + "/" + sourceSet.getName();
    sourceDirectorySet.getDestinationDirectory().convention(target.getLayout().getBuildDirectory().dir(sourceSetChildPath));
    DefaultSourceSetOutput sourceSetOutput = Cast.cast(DefaultSourceSetOutput.class, sourceSet.getOutput());
    sourceSetOutput.getClassesDirs().from(sourceDirectorySet.getDestinationDirectory()).builtBy(compileTask);
    sourceDirectorySet.compiledBy(compileTask, AbstractCompile::getDestinationDirectory);
  }

}
