package org.gosulang.gradle.tasks.gosudoc;

import org.gosulang.gradle.tasks.InfersGosuRuntime;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;

@CacheableTask
public abstract class GosuDoc extends SourceTask implements InfersGosuRuntime {

  private FileCollection _classpath;
  private ConfigurableFileCollection gosuClasspath;
  private DirectoryProperty _destinationDir;
  private GosuDocOptions _gosuDocOptions = new GosuDocOptions();
  private String _title;

  @Inject
  protected abstract ExecOperations getExecOperations();

  @Inject
  protected abstract FileSystemOperations getFileSystemOperations();

  @Inject
  protected abstract ObjectFactory getObjectFactory();

  @Inject
  protected abstract ProjectLayout getLayout();

  /**
   * {@inheritDoc}
   */
  @PathSensitive(PathSensitivity.RELATIVE)
  @Override
  public FileTree getSource() {
    return super.getSource();
  }

  /**
   * @return the target directory to generate the API documentation.
   */
  @OutputDirectory
  public DirectoryProperty getDestinationDir() {
    if (_destinationDir == null) {
      _destinationDir = getObjectFactory().directoryProperty();
    }
    return _destinationDir;
  }

  /**
   *
   * @deprecated Use {@link #getDestinationDir()} methods
   * @see DirectoryProperty
   */
  @Deprecated
  public void setDestinationDir(File destinationDir) {
    getDestinationDir().set(destinationDir);
  }

  /**
   * <p>Returns the classpath to use to locate classes referenced by the documented source.</p>
   *
   * @return The classpath.
   */
  @Classpath
  @InputFiles
  public FileCollection getClasspath() {
    return _classpath;
  }

  public void setClasspath( FileCollection classpath ) {
    _classpath = classpath;
  }

  /**
   * Returns the classpath to use to load the gosudoc tool.
   * @return the classpath to use to load the gosudoc tool.
   */
  @Override
  @Classpath
  @InputFiles
  public ConfigurableFileCollection getGosuClasspath() {
    // Field named 'gosuClasspath' (not '_gosuClasspath') — see GosuCompile for the CC rationale.
    if (gosuClasspath == null) {
      gosuClasspath = getObjectFactory().fileCollection();
    }
    return gosuClasspath;
  }

  @Override
  public void setGosuClasspath(FileCollection gosuClasspath) {
    getGosuClasspath().setFrom(gosuClasspath);
  }

  /**
   * Returns the gosudoc generation options.
   * @return the gosudoc options
   */
  @Nested
  public GosuDocOptions getGosuDocOptions() {
    return _gosuDocOptions;
  }

  public void setGosuDocOptions(GosuDocOptions gosuDocOptions) {
    _gosuDocOptions = gosuDocOptions;
  }

  /**
   * Returns the documentation title.
   * @return the documentation title.
   */
  @Input
  @Optional
  public String getTitle() {
    return _title;
  }

  public void setTitle( String title ) {
    this._title = title;
  }

  @TaskAction
  protected void generate() {
    GosuDocOptions options = getGosuDocOptions();
    if (options.getTitle() != null && !options.getTitle().isEmpty()) {
      options.setTitle(getTitle());
    }
    new CommandLineGosuDoc(getSource(), getDestinationDir().get().getAsFile(), getGosuClasspath(), getClasspath(), options,
        getExecOperations(), getFileSystemOperations(), getObjectFactory(), getLayout(), getPath()).execute();
  }
}
