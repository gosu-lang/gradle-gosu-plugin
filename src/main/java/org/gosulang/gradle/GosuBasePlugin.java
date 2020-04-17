package org.gosulang.gradle;


import org.codehaus.groovy.runtime.InvokerHelper;
import org.gosulang.gradle.tasks.DefaultGosuSourceSet;
import org.gosulang.gradle.tasks.GosuRuntime;
import org.gosulang.gradle.tasks.GosuSourceSet;
import org.gosulang.gradle.tasks.compile.GosuCompile;
import org.gosulang.gradle.tasks.gosudoc.GosuDoc;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.SourceDirectorySet;
//import org.gradle.api.internal.file.SourceDirectorySetFactory;  //TODO unavoidable use of internal API
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.Convention;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.plugins.internal.JvmPluginsHelper;
//import org.gradle.api.plugins.internal.SourceSetUtil;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.util.VersionNumber;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Callable;

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
                GosuSourceSet gosuSourceSet = new DefaultGosuSourceSet(sourceSet.getName(), _objectFactory);

      Convention sourceSetConvention = (Convention) InvokerHelper.getProperty(sourceSet, "convention");
      sourceSetConvention.getPlugins().put("gosu", gosuSourceSet);

      gosuSourceSet.getGosu().srcDir("src/" + sourceSet.getName() + "/gosu");

      sourceSet.getResources().getFilter().exclude(element -> gosuSourceSet.getGosu().contains(element.getFile()));

      sourceSet.getAllSource().source(gosuSourceSet.getGosu());

      configureGosuCompile(javaBasePlugin, sourceSet, gosuSourceSet);
    });
  }

  /**
   * Create and configure default compileGosu and compileTestGosu tasks
   * Gradle 4.0+: call local equivalent of o.g.a.p.i.SourceSetUtil.configureForSourceSet(sourceSet, gosuSourceSet.getGosu(), gosuCompile, _project)
   * Gradle 2.x, 3.x: call javaPlugin.configureForSourceSet(sourceSet, gosuCompile);
   */
  private void configureGosuCompile(JavaBasePlugin javaPlugin, SourceSet sourceSet, GosuSourceSet gosuSourceSet) {
    String compileTaskName = sourceSet.getCompileTaskName("gosu");
    GosuCompile gosuCompile = _project.getTasks().create(compileTaskName, GosuCompile.class);

    VersionNumber gradleVersion = VersionNumber.parse(_project.getGradle().getGradleVersion());
    if(gradleVersion.compareTo(VersionNumber.parse("4.0")) >= 0) {
      //Gradle 4.0+
      configureForSourceSet(sourceSet, gosuSourceSet.getGosu(), gosuCompile, _project);
    } else {
//      javaPlugin.configureForSourceSet(sourceSet, gosuCompile);
      ConventionMapping conventionMapping = gosuCompile.getConventionMapping();
      gosuCompile.setSource(sourceSet.getJava());
      conventionMapping.map("classpath", new Callable<Object>() {
        public Object call() throws Exception {
          return sourceSet.getCompileClasspath().plus(gosuCompile.getProject().files(new Object[]{sourceSet.getJava().getOutputDir()}));
        }
      });
      JvmPluginsHelper.configureAnnotationProcessorPath(sourceSet, gosuSourceSet.getGosu(), gosuCompile.getOptions(), _project);
      gosuCompile.setDestinationDir(_project.provider(new Callable<File>() {
        public File call() {
          return sourceSet.getJava().getOutputDir();
        }
      }));
      gosuCompile.setDescription("Compiles the " + gosuSourceSet.getGosu() + ".");
    }
    gosuCompile.dependsOn(sourceSet.getCompileJavaTaskName());
    gosuCompile.setSource((Object) gosuSourceSet.getGosu()); // Gradle 4.0 overloads setSource; must upcast to Object for backwards compatibility

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

  private static void configureForSourceSet(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, AbstractCompile compile, final Project target) {
    compile.setDescription("Compiles the " + sourceDirectorySet.getDisplayName() + ".");
    compile.setSource(sourceSet.getJava());
    compile.getConventionMapping().map("classpath", () -> sourceSet.getCompileClasspath().plus(target.files(sourceSet.getJava().getOutputDir())));
    configureOutputDirectoryForSourceSet(sourceSet, sourceDirectorySet, compile, target);
  }

  private static void configureOutputDirectoryForSourceSet(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, AbstractCompile compile, final Project target) {
    final String sourceSetChildPath = "classes/" + sourceDirectorySet.getName() + "/" + sourceSet.getName();
    sourceDirectorySet.setOutputDir(target.provider(() -> {
        return new File(target.getBuildDir(), sourceSetChildPath);
    }));

    ((ConfigurableFileCollection) sourceSet.getOutput().getClassesDirs()).from(target.provider( sourceDirectorySet::getOutputDir));

    compile.setDestinationDir(target.provider(sourceDirectorySet::getOutputDir));
  }

}
