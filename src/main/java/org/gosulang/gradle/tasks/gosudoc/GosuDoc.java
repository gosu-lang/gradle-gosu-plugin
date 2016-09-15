package org.gosulang.gradle.tasks.gosudoc;

import groovy.lang.Closure;
import org.gosulang.gradle.tasks.InfersGosuRuntime;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;

public class GosuDoc extends SourceTask implements InfersGosuRuntime {

  private FileCollection _classpath;
  private Closure<FileCollection> _gosuClasspath;
  private File _destinationDir;
  private GosuDocOptions _gosuDocOptions = new GosuDocOptions();
  private String _title;

  public GosuDoc() {
    getLogging().captureStandardOutput(LogLevel.INFO);
  }
  
  /**
   * Returns the target directory to generate the API documentation.
   * @return the target directory to generate the API documentation.
   */
  @OutputDirectory
  public File getDestinationDir() {
    return _destinationDir;
  }

  public void setDestinationDir( File destinationDir ) {
    _destinationDir = destinationDir;
  }

  /**
   * <p>Returns the classpath to use to locate classes referenced by the documented source.</p>
   *
   * @return The classpath.
   */
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
  @InputFiles
  public Closure<FileCollection> getGosuClasspath() {
    return _gosuClasspath;
  }

  @Override
  public void setGosuClasspath( Closure<FileCollection> gosuClasspathClosure ) {
    _gosuClasspath = gosuClasspathClosure;
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
    new CommandLineGosuDoc(getSource(), getDestinationDir(), getGosuClasspath().call(), getClasspath(), options, getProject()).execute();
  }
}
