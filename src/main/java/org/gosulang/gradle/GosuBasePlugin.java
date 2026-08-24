package org.gosulang.gradle;


import org.gosulang.gradle.tasks.DefaultGosuSourceSet;
import org.gosulang.gradle.tasks.GosuRuntime;
import org.gosulang.gradle.tasks.GosuSourceSet;
import org.gosulang.gradle.tasks.compile.GosuCompile;
import org.gosulang.gradle.tasks.gosudoc.GosuDoc;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.AbstractCompile;

import javax.inject.Inject;
import java.io.Serializable;

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
    // Convention mapping defers evaluation of inferGosuClasspath() to first access (task execution on
    // non-CC builds; CC-store time on CC builds, when BeanPropertyWriter serialises the task graph).
    // This matches the pattern used by GroovyBasePlugin and ScalaBasePlugin.
    //
    // IMPORTANT – the backing field in GosuCompile/GosuDoc MUST be named 'gosuClasspath' (no
    // underscore prefix). Gradle's CC BeanPropertyWriter looks up convention mappings by the
    // backing-field name; an underscore-prefixed name produces a key mismatch so the convention
    // is never found, CC stores null for the field, and gosuClasspath is empty on CC reuse.
    // Fixed upstream in Gradle 8.8 via gradle/gradle#28248 (ConfigurableFileCollection now
    // wires its convention directly, bypassing the field-name lookup); required for Gradle ≤ 8.7.
    //
    // Using CFC#from(Callable) here instead (PR #93) causes CC to attempt to serialise the
    // lambda at store time; when that fails the property is left unconfigured, producing a
    // WorkValidationException before the task action runs.
    //
    // TODO: once minimum Gradle is 8.8, drop convention mapping here and in configureGosuDoc()
    // and replace with: gosuCompile.getGosuClasspath().convention(_project.getObjects()
    //   .fileCollection().from((Callable) () -> _gosuRuntime.inferGosuClasspath(...))).
    // CFC#convention(Callable) was introduced in Gradle 8.8 (gradle/gradle#28248); it wires
    // the convention directly on the CFC and is fully CC-safe without the field-name constraint.
    _project.getTasks().withType(GosuCompile.class, gosuCompile ->
        gosuCompile.getConventionMapping().map("gosuClasspath", () -> _gosuRuntime.inferGosuClasspath(gosuCompile.getClasspath())));
  }

 private void configureSourceSetDefaults() {
     javaPluginExtension(_project).getSourceSets().all(sourceSet -> {
      DefaultGosuSourceSet gosuSourceSet = new DefaultGosuSourceSet(sourceSet.getName(), _objectFactory);
      // org.gradle.api.plugins.Convention is removed in Gradle 9.0. Register the *SourceDirectorySet*
      // itself as the "gosu" extension, matching exactly how Gradle's own current GroovyBasePlugin/
      // ScalaBasePlugin attach their language sources (e.g. sourceSet.getExtensions().add(
      // GroovySourceDirectorySet.class, "groovy", groovySource)). This matters because Gradle's
      // extension-shorthand DSL (`gosu { ... }` inside a sourceSet block, or a bare `sourceSet.gosu`
      // property read) delegates directly to the registered extension OBJECT, not through any
      // custom method our own GosuSourceSet interface might define under the same name. Registering
      // the wrapper GosuSourceSet instance (first attempt) broke `gosu { srcDir(...) }` /
      // `gosu.srcDirs` DSL usage because those calls resolved against the wrapper object, which has
      // no such methods/properties (only its nested getGosu() SourceDirectorySet does).
      sourceSet.getExtensions().add(SourceDirectorySet.class, "gosu", gosuSourceSet.getGosu());
      gosuSourceSet.getGosu().srcDir("src/" + sourceSet.getName() + "/gosu");
      // Exclude gosu sources from this source set's resources with a *serializable* Spec, so the filter — and
      // therefore every consumer's ProcessResources task — is configuration-cache compatible. The
      // (Spec & Serializable) intersection cast produces a serializable lambda using only public Java/Gradle
      // API; it is exactly what Gradle's own GroovyBasePlugin/ScalaBasePlugin do via the internal
      // SerializableLambdas.spec (which a community plugin must not depend on). Capture a FileCollection view of
      // the gosu sources (serialized via the configuration cache's file-collection codec), NOT the
      // DefaultGosuSourceSet graph — capturing the latter was the dominant CC hazard this plugin imposed on
      // consumers. See #68.
      final FileCollection gosuSourceFiles = gosuSourceSet.getGosu();
      sourceSet.getResources().getFilter().exclude(
          (Spec<FileTreeElement> & Serializable) element -> gosuSourceFiles.contains(element.getFile()));
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
    TaskProvider<GosuCompile> gosuCompile = _project.getTasks().register(compileTaskName, GosuCompile.class);
    configureForSourceSet(sourceSet, gosuSourceSet.getGosu(), gosuCompile, _project);
    gosuCompile.configure(t -> {
      t.dependsOn(sourceSet.getCompileJavaTaskName());
      t.getSourceRoots().from(gosuSourceSet.getGosu().getSourceDirectories());
      t.setSource((Object) gosuSourceSet.getGosu()); // Gradle 4.0 overloads setSource; must upcast to Object for backwards compatibility
      // Configure Java classes directory tracking for fine-grained Java → Gosu dependency tracking
      t.getJavaClassesDir().from(sourceSet.getJava().getDestinationDirectory());
    });
    _project.getTasks().getByName(sourceSet.getClassesTaskName()).dependsOn(compileTaskName);
  }

  private void configureGosuDoc() {
    // Same convention-mapping pattern as configureCompileDefaults(); see that comment for rationale.
    _project.getTasks().withType(GosuDoc.class, gosudoc -> {
      gosudoc.getLogging().captureStandardOutput(LogLevel.INFO);
      gosudoc.getConventionMapping().map("gosuClasspath", () -> _gosuRuntime.inferGosuClasspath(gosudoc.getClasspath()));
      gosudoc.getDestinationDir().convention(javaPluginExtension(_project).getDocsDir().map(d -> d.dir("gosudoc")));
      gosudoc.getConventionMapping().map("title", () -> _project.getExtensions().getByType(ReportingExtension.class).getApiDocTitle());
      //gosudoc.getConventionMapping().map("windowTitle", (Callable<Object>) () -> _project.getExtensions().getByType(ReportingExtension.class).getApiDocTitle());
    });
  }

  private static void configureForSourceSet(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, TaskProvider<? extends AbstractCompile> compile, final Project target) {
    compile.configure(t -> {
      t.setDescription("Compiles the " + sourceDirectorySet.getDisplayName() + ".");
      t.setSource(sourceSet.getJava());
      // No javaClassesDir filtering here: GosuCompile.getClasspath() subtracts it on read, so
      // the @CompileClasspath input never contains the Java output regardless of how the value
      // was supplied. GosuCompile.createSpec() re-adds it to the classpath handed to gosuc.
      t.getConventionMapping().map("classpath", () -> sourceSet.getCompileClasspath());
    });
    configureOutputDirectoryForSourceSet(sourceSet, sourceDirectorySet, target, compile);
  }

 private static void configureOutputDirectoryForSourceSet(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, final Project target, TaskProvider<? extends AbstractCompile> compileTask) {
    final String sourceSetChildPath = "classes/" + sourceDirectorySet.getName() + "/" + sourceSet.getName();
    sourceDirectorySet.getDestinationDirectory().convention(target.getLayout().getBuildDirectory().dir(sourceSetChildPath));
    ((ConfigurableFileCollection) sourceSet.getOutput().getClassesDirs()).from(sourceDirectorySet.getDestinationDirectory()).builtBy(compileTask);
    sourceDirectorySet.compiledBy(compileTask, AbstractCompile::getDestinationDirectory);
  }

}
