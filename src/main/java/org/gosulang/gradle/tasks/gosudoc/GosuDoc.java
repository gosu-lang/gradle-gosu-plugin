package org.gosulang.gradle.tasks.gosudoc;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;

public class GosuDoc extends SourceTask {

  private FileCollection _gosuClasspath;
  private FileCollection _classpath;
  private File _destinationDir;
  private AntGosuDoc _antGosuDoc;
  private GosuDocOptions _gosuDocOptions = new GosuDocOptions();
  private String _title;

  public GosuDoc() {
    getLogging().captureStandardOutput(LogLevel.INFO);
  }
  
  @Inject
  protected IsolatedAntBuilder getAntBuilder() {
    throw new UnsupportedOperationException();
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
   * Returns the classpath to use to load the gosu-doc tool.
   * @return the classpath to use to load the gosu-doc tool.
   */
  @InputFiles
  public FileCollection getGosuClasspath() {
    return _gosuClasspath;
  }

  public void setGosuClasspath( FileCollection gosuClasspath ) {
    _gosuClasspath = gosuClasspath;
  }

  /**
   * Returns the gosu-doc generation options.
   * @return the gosu-doc options
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

  public AntGosuDoc getAntGosuDoc() {
    if (_antGosuDoc == null) {
      IsolatedAntBuilder antBuilder = getServices().get(IsolatedAntBuilder.class);
      _antGosuDoc = new AntGosuDoc(antBuilder);
    }
    return _antGosuDoc;
  }

  public void setAntGosuDoc(AntGosuDoc antGosuDoc) {
    _antGosuDoc = antGosuDoc;
  }

  @TaskAction
  protected void generate() {
    GosuDocOptions options = getGosuDocOptions();
    if (options.getTitle() != null && !options.getTitle().isEmpty()) {
      options.setTitle(getTitle());
    }
    getAntGosuDoc().execute(getSource(), getDestinationDir(), getClasspath(), getGosuClasspath(), options, getProject());
  }
}
