package org.gosulang.gradle.tasks.compile;

import groovy.lang.Closure;
import org.gosulang.gradle.tasks.InfersGosuRuntime;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.BuildException;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.gradle.api.tasks.PathSensitivity.NAME_ONLY;

public class GosuCompile extends JavaCompile implements InfersGosuRuntime {

  @Deprecated
  private Closure<FileCollection> _gosuClasspath;
  private Closure<FileCollection> _orderClasspath;

  @Deprecated
  private final GosuCompileOptions _gosuCompileOptions = new GosuCompileOptions();

  @Inject
  public GosuCompile() {
  }

  @Override
  @TaskAction
  protected void compile() {
    getOptions().getCompilerArgs().add("-Xplugin:Manifold static");
    getOptions().setFork(true);
    getOptions().getForkOptions().setExecutable("javac");
    //noinspection ConstantConditions
    getOptions().getForkOptions().getJvmArgs().addAll(createExternalSourcesList());
    super.compile();
  }

  private List<String> createExternalSourcesList() {
    List<String> retval = Collections.emptyList();

    FileTree gosuSources = getSource().matching( filter -> filter.exclude("**/*.java") );

    if(!gosuSources.isEmpty()) {
      Path sourcesFile = new File(getTemporaryDir(), "manifold-additional-sources.txt").toPath();
      List<String> sources = gosuSources.getFiles().stream().map(File::getAbsolutePath).collect(Collectors.toList());
      try {
        Files.write(sourcesFile, sources, StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new BuildException(String.format("Unable to write source list to %s", sourcesFile), e);
      }
      retval = Collections.singletonList("-J-Dgosu.source.list=" + sourcesFile);
    }
    return retval;
  }

//  /**
//   * {@inheritDoc}
//   */
//  @Override
//  @PathSensitive(NAME_ONLY)
//  public FileTree getSource() {
//    return super.getSource();
//  }

  /**
   * @return Gosu-specific compilation options.
   */
  @Nested
  @Deprecated
  public GosuCompileOptions getGosuOptions() {
    return _gosuCompileOptions;
  }

  /**
   * We override in order to apply the {@link org.gradle.api.tasks.CompileClasspath}, in order to ignore changes in JAR'd resources.
   */
  @CompileClasspath
  @Deprecated() //TODO maybe deprecated?
  public FileCollection getClasspath() {
    return super.getClasspath();
  }

  /**
   * @return the classpath to use to load the Gosu compiler.
   */
  @Override
  @Classpath
  @InputFiles
  public Closure<FileCollection> getGosuClasspath() {
    return _gosuClasspath;
  }

  @Override
  public void setGosuClasspath(Closure<FileCollection> gosuClasspathClosure) {
    _gosuClasspath = gosuClasspathClosure;
  }

  /**
   * Annotating as @Input or @InputFiles causes errors in Guidewire applications, even when paired with @Optional.
   * Marking as @Internal instead to skip warning thrown by :validateTaskProperties (org.gradle.plugin.devel.tasks.ValidateTaskProperties)
   * @return a Closure returning a classpath to be passed to the GosuCompile task
   */
  @Internal
  public Closure<FileCollection> getOrderClasspath() {
    return _orderClasspath;
  }

  /**
   * Normally setting this value is not required.
   * Certain projects relying on depth-first resolution of module dependencies can use this
   * Closure to reorder the classpath as needed.
   *
   * @param orderClasspath a Closure returning a classpath to be passed to the GosuCompile task
   */
  public void setOrderClasspath(Closure<FileCollection> orderClasspath) {
    _orderClasspath = orderClasspath;
  }

  @Internal
  @Optional
  @Deprecated
  public FileCollection getSourceRoots() {
    Set<File> returnValues = new HashSet<>();
    //noinspection Convert2streamapi
    for(Object obj : this.source) {
      if(obj instanceof SourceDirectorySet) {
        returnValues.addAll(((SourceDirectorySet) obj).getSrcDirs());
      }
    }
    return getProject().files(returnValues);
  }

  @Deprecated
  private DefaultGosuCompileSpec createSpec() {
    DefaultGosuCompileSpec spec = new DefaultGosuCompileSpec();
    Project project = getProject();
    spec.setSource(getSource());
    spec.setSourceRoots(getSourceRoots());
    spec.setDestinationDir(getDestinationDir());
    spec.setTempDir(getTemporaryDir());
    spec.setGosuClasspath(getGosuClasspath());
    spec.setGosuCompileOptions(_gosuCompileOptions);

    if (_orderClasspath == null) {
      spec.setClasspath(asList(getClasspath()));
    } else {
      spec.setClasspath(asList(_orderClasspath.call(project, project.getConfigurations().getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME))));
    }

    Logger logger = project.getLogger();

    if(logger.isInfoEnabled()) {
      logger.info("Gosu Compiler source roots for {} are:", project.getName());
      if(spec.getSourceRoots().isEmpty()) {
        logger.info("<empty>");
      } else {
        for(File file : spec.getSourceRoots()) {
          logger.info(file.getAbsolutePath());
        }
      }

      logger.info("Gosu Compiler Spec classpath for {} is:", project.getName());
      if(!spec.getClasspath().iterator().hasNext()) {
        logger.info("<empty>");
      } else {
        for(File file : spec.getClasspath()) {
          logger.info(file.getAbsolutePath());
        }
      }

      logger.info("Gosu Compile Spec gosuClasspath for {} is:", project.getName());
      FileCollection gosuClasspath = spec.getGosuClasspath().call();
      if(gosuClasspath.isEmpty()) {
        logger.info("<empty>");
      } else {
      for(File file : gosuClasspath) {
        logger.info(file.getAbsolutePath());
        }
      }
    }

    return spec;
  }

//  private GosuCompiler<GosuCompileSpec> getCompiler(GosuCompileSpec spec) {
//    if(_compiler == null) {
//      GosuCompilerFactory gosuCompilerFactory = new GosuCompilerFactory(getProject(), this.getPath());
//      _compiler = gosuCompilerFactory.newCompiler(spec);
//    }
//    return _compiler;
//  }

  @Deprecated
  private List<File> asList(final FileCollection files) {
    List<File> list = new ArrayList<>();
    files.forEach(list::add);
    return list;
  }

}
