package org.gosulang.gradle.tasks.compile;

import org.gosulang.gradle.GosuPlugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.JavaCompilerFactory;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class GosuCompile extends AbstractCompile {

  private Compiler<DefaultGosuCompileSpec> _compiler;
  private FileCollection _gosuClasspath;
  private Set<File> _sourceRoots;

  private final CompileOptions _compileOptions = new CompileOptions();

  private static final Logger LOGGER = Logging.getLogger(GosuCompile.class);

  @Override
  @TaskAction
  protected void compile() {
    DefaultGosuCompileSpec spec = createSpec();
    _compiler = getCompiler(spec);
    WorkResult result = _compiler.execute(spec);
  }

  @Nested
  public CompileOptions getOptions() {
    return _compileOptions;
  }

  /**
   * @return the classpath to use to load the Gosu compiler.
   */
  @InputFiles
  public FileCollection getGosuClasspath() {
    return _gosuClasspath;
  }

  public void setGosuClasspath(FileCollection gosuClasspath) {
    _gosuClasspath = gosuClasspath;
  }

  public Set<File> getSourceRoots() {
    return _sourceRoots;
  }

  public void setSourceRoots(Set<File> sourceRoots) {
    _sourceRoots = sourceRoots;
  }

  private DefaultGosuCompileSpec createSpec() {
    DefaultGosuCompileSpec spec = new DefaultGosuCompileSpecFactory(_compileOptions).create();
    Project project = getProject();
    spec.setSource(getSource()); //project.files([ "src/main/gosu" ])
    spec.setSourceRoots(getSourceRoots());
    spec.setDestinationDir(getDestinationDir());
    spec.setClasspath(getClasspath());

    //Force gosu-core into the classpath. Normally it's a runtime dependency but compilation requires it.
    Set<ResolvedArtifact> projectDeps = project.getConfigurations().getByName("runtime").getResolvedConfiguration().getResolvedArtifacts();
    File gosuCore = GosuPlugin.getArtifactWithName("gosu-core", projectDeps).getFile();
    spec.setGosuClasspath( Collections.singletonList( gosuCore ) );

    if(LOGGER.isDebugEnabled()) {
      LOGGER.debug("Gosu Compiler Spec classpath is:");
      for(File file : spec.getClasspath()) {
        LOGGER.debug(file.getAbsolutePath());
      }

      LOGGER.debug("Gosu Compile Spec gosuClasspath is:");
      for(File file : spec.getGosuClasspath()) {
        LOGGER.debug(file.getAbsolutePath());
      }
    }

    return spec;
  }

  private Compiler<DefaultGosuCompileSpec> getCompiler(DefaultGosuCompileSpec spec) {
    if(_compiler == null) {
      ProjectInternal projectInternal = (ProjectInternal) getProject();
      CompilerDaemonManager compilerDaemonManager = getServices().get(CompilerDaemonManager.class);
      //      var inProcessCompilerDaemonFactory = getServices().getFactory(InProcessCompilerDaemonFactory);
      JavaCompilerFactory javaCompilerFactory = getServices().get(JavaCompilerFactory.class);
      GosuCompilerFactory gosuCompilerFactory = new GosuCompilerFactory(projectInternal, javaCompilerFactory, compilerDaemonManager); //inProcessCompilerDaemonFactory
      LOGGER.quiet("Initializing Gosu compiler...");
      _compiler = gosuCompilerFactory.newCompiler(spec);
    }
    return _compiler;
  }


}
