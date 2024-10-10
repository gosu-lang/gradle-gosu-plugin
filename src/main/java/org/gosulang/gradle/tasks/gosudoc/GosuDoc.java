package org.gosulang.gradle.tasks.gosudoc;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;

@CacheableTask
public abstract class GosuDoc extends SourceTask /*implements InfersGosuRuntime*/ {

  private GosuDocOptions _gosuDocOptions = new GosuDocOptions();

  @Inject
  public abstract ObjectFactory getObjectFactory();

  @Inject
  public GosuDoc() {
    getLogging().captureStandardOutput(LogLevel.INFO);
  }

  @Internal
  public abstract Property<String> getProjectName();

  @Internal
  public abstract DirectoryProperty getProjectDir();

  @Internal
  public abstract DirectoryProperty getBuildDir();

  /**
   * {@inheritDoc}
   */
  @PathSensitive(PathSensitivity.RELATIVE)
  @Override
  public FileTree getSource() {
    return super.getSource();
  }
  
  /**
   * Returns the target directory to generate the API documentation.
   * @return the target directory to generate the API documentation.
   */
  @OutputDirectory
  public abstract DirectoryProperty getDestinationDir();

  /**
   * <p>Returns the classpath to use to locate classes referenced by the documented source.</p>
   *
   * @return The classpath.
   */
  @Classpath
  public abstract ConfigurableFileCollection getClasspath();

  /**
   * Returns the classpath to use to load the gosudoc tool.
   *
   * @return the classpath to use to load the gosudoc tool.
   */
  @Classpath
  public abstract ConfigurableFileCollection getGosuClasspath();

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
  public abstract Property<String> getTitle();

  @TaskAction
  protected void generate() {
    GosuDocOptions options = getGosuDocOptions();
    if (options.getTitle() != null && !options.getTitle().isEmpty()) {
      options.setTitle(getTitle().get());
    }
    getObjectFactory().newInstance(CommandLineGosuDoc.class, getSource(), getDestinationDir().get(), getGosuClasspath(), getClasspath(), options, getProjectName().get(), getProjectDir().get(), getBuildDir().get()).execute();
  }
}
