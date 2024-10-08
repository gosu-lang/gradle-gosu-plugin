package org.gosulang.gradle;


import org.codehaus.groovy.runtime.InvokerHelper;
import org.gosulang.gradle.tasks.DefaultGosuSourceSet;
import org.gosulang.gradle.tasks.GosuRuntime;
import org.gosulang.gradle.tasks.GosuSourceSet;
import org.gosulang.gradle.tasks.compile.GosuCompile;
import org.gosulang.gradle.tasks.gosudoc.GosuDoc;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.tasks.DefaultSourceSetOutput;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.internal.Cast;

import javax.inject.Inject;
import java.io.File;

import static org.gosulang.gradle.tasks.Util.javaPluginExtension;

public class GosuBasePlugin implements Plugin<Project> {
  public static final String GOSU_RUNTIME_EXTENSION_NAME = "gosuRuntime";

  private final ObjectFactory _objectFactory;

  private Project _project;
  private GosuRuntime _gosuRuntime;

  @Inject
  GosuBasePlugin(ObjectFactory objectFactory){
  _objectFactory = objectFactory;
  }

  @Override
  public void apply(Project project) {
    _project = project;
    _project.getPluginManager().apply(JavaBasePlugin.class);

    configureGosuRuntimeExtension();
    configureCompileDefaults();
    configureSourceSetDefaults();
    configureGosuDoc();
  }

  private void configureGosuRuntimeExtension() {
    _gosuRuntime = _project.getExtensions().create(GOSU_RUNTIME_EXTENSION_NAME, GosuRuntime.class);
  }

  /**
   * Sets the gosuClasspath property for all GosuCompile tasks: compileGosu and compileTestGosu
   */
  private void configureCompileDefaults() {
    _project.getTasks().withType(GosuCompile.class, gosuCompile ->
        gosuCompile.getConventionMapping().map("gosuClasspath", () -> _gosuRuntime.inferGosuClasspath(gosuCompile.getClasspath())));
  }

 private void configureSourceSetDefaults() {
     javaPluginExtension(_project).getSourceSets().all(sourceSet -> {
      DefaultGosuSourceSet gosuSourceSet = new DefaultGosuSourceSet(sourceSet.getName(), _objectFactory);
     //have to be revisit to avoid using the covention here
      Convention sourceSetConvention = (Convention) InvokerHelper.getProperty(sourceSet, "convention");
      sourceSetConvention.getPlugins().put("gosu", gosuSourceSet);
  //    sourceSet.getExtensions().add(SourceDirectorySet.class, "gosu", gosuSourceSet.getGosu()); //alternative but it's not working
      gosuSourceSet.getGosu().srcDir("src/" + sourceSet.getName() + "/gosu");
      sourceSet.getResources().getFilter().exclude(element -> gosuSourceSet.getGosu().contains(element.getFile()));
      sourceSet.getAllSource().source(gosuSourceSet.getGosu());
      configureGosuCompile(sourceSet, gosuSourceSet);
    });
  }

  /**
   * Create and configure default compileGosu and compileTestGosu tasks
   * Gradle 4.0+: call local equivalent of o.g.a.p.i.SourceSetUtil.configureForSourceSet(sourceSet, gosuSourceSet.getGosu(), gosuCompile, _project)
   * Gradle 2.x, 3.x: call javaPlugin.configureForSourceSet(sourceSet, gosuCompile);
   */
  private void configureGosuCompile(SourceSet sourceSet, GosuSourceSet gosuSourceSet) {
    String compileTaskName = sourceSet.getCompileTaskName("gosu");
    TaskProvider<? extends AbstractCompile> gosuCompile = _project.getTasks().register(compileTaskName, GosuCompile.class);
    configureForSourceSet(sourceSet, gosuSourceSet.getGosu(), gosuCompile, _project);
    gosuCompile.configure(t -> t.dependsOn(sourceSet.getCompileJavaTaskName()));
    gosuCompile.configure(t -> t.setSource((Object) gosuSourceSet.getGosu())); // Gradle 4.0 overloads setSource; must upcast to Object for backwards compatibility
    _project.getTasks().getByName(sourceSet.getClassesTaskName()).dependsOn(compileTaskName);
  }

  private void configureGosuDoc() {
    _project.getTasks().withType(GosuDoc.class, gosudoc -> {
      gosudoc.getConventionMapping().map("gosuClasspath", () -> _gosuRuntime.inferGosuClasspath(gosudoc.getClasspath()));
      gosudoc.getConventionMapping().map("destinationDir", () -> new File(javaPluginExtension(_project).getDocsDir().get().getAsFile(), "gosudoc"));
      gosudoc.getConventionMapping().map("title", () -> _project.getExtensions().getByType(ReportingExtension.class).getApiDocTitle());
      //gosudoc.getConventionMapping().map("windowTitle", (Callable<Object>) () -> _project.getExtensions().getByType(ReportingExtension.class).getApiDocTitle());
    });
  }

  private static void configureForSourceSet(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, TaskProvider<? extends AbstractCompile> compile, final Project target) {
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
