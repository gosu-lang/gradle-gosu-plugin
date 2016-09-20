package org.gosulang.gradle.tasks.gosudoc;

import org.apache.tools.ant.taskdefs.condition.Os;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.internal.jvm.Jvm;
import org.gradle.process.ExecResult;
import org.gradle.process.JavaExecSpec;
import org.gradle.tooling.BuildException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class CommandLineGosuDoc {
  private static final Logger LOGGER = Logging.getLogger(CommandLineGosuDoc.class);
  
  private final FileCollection _source;
  private final File _targetDir;
  private final FileCollection _projectClasspath;
  private final FileCollection _gosuClasspath;
  private final GosuDocOptions _options;
  private final Project _project;


  public CommandLineGosuDoc(FileCollection source, File targetDir, FileCollection gosuClasspath, FileCollection projectClasspath, GosuDocOptions options, Project project) {
    _source = source;
    _targetDir = targetDir;
    _gosuClasspath = gosuClasspath;
    _projectClasspath = projectClasspath;
    _options = options;
    _project = project;
  }
  
  public void execute() {
    String startupMsg = "Initializing gosudoc generator";
    if(_project.getName().isEmpty()) {
      startupMsg += " for " + _project.getName();
    }
    LOGGER.info(startupMsg);
    
    //'source' is a FileCollection with explicit paths.
    // We don't want that, so instead we create a temp directory with the contents of 'source'
    // Copying 'source' to the temp dir should honor its include/exclude patterns
    // Finally, the tmpdir will be the sole inputdir passed to the gosudoc task
    final File tmpDir = new File(_project.getBuildDir(), "tmp/gosudoc");
    _project.delete(tmpDir);
    _project.copy(copySpec -> copySpec.from(_source).into(tmpDir));

    List<String> gosudocArgs = new ArrayList<>();

    File argFile;
    try {
      argFile = createArgFile(tmpDir);
      gosudocArgs.add("@" + argFile.getCanonicalPath().replace(File.separatorChar, '/'));
    } catch (IOException e) {
      throw new BuildException("Error creating argfile with gosudoc arguments", e);
    }
    
//    gosudocArgs.add("-classpath");
//    gosudocArgs.add(_projectClasspath.getAsPath());
//    
//    gosudocArgs.add("-inputDirs");
//    gosudocArgs.add(tmpDir.getAbsolutePath());
//    
//    gosudocArgs.add("-output");
//    gosudocArgs.add(_targetDir.getAbsolutePath());
//    
//    gosudocArgs.add("-verbose"); //TODO parameterize

    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    FileCollection jointClasspath = _project.files(Jvm.current().getToolsJar()).plus(_gosuClasspath).plus(_projectClasspath);

    // make temporary classpath jar with Class-Path attribute because jointClasspath will be way too long in some cases
//    File classpathJar;
//    try {
//      classpathJar = createClasspathJarFromFileCollection(jointClasspath);
//    } catch (IOException e) {
//      throw new BuildException("Error creating classpath JAR for gosudoc generation", e);
//    }
//    
//    LOGGER.info("Created classpathJar at " + classpathJar.getAbsolutePath());
    
    ExecResult result = _project.javaexec(javaExecSpec -> {
      javaExecSpec.setWorkingDir(_project.getProjectDir());
      setJvmArgs(javaExecSpec, _options.getForkOptions());
      javaExecSpec.setMain("gw.gosudoc.cli.Gosudoc")
//          .setClasspath(_project.files(classpathJar))
          .setClasspath(_project.files(Jvm.current().getToolsJar(), _gosuClasspath, _project.files(tmpDir.getAbsolutePath())))
          .setArgs(gosudocArgs);
      javaExecSpec.setStandardOutput(stdout);
      javaExecSpec.setErrorOutput(stderr);
      javaExecSpec.setIgnoreExitValue(true); //otherwise fails immediately before displaying output
    });

    LOGGER.info("Dumping stdout");
    LOGGER.info(stdout.toString());
    LOGGER.info("Done dumping stdout");

    String errorContent = stderr.toString();
    if(errorContent != null && !errorContent.isEmpty()) {
      throw new GradleException("gosudoc failed with errors: \n" + errorContent);
    }

    result.assertNormalExitValue();
  }

  private File createClasspathJarFromFileCollection(FileCollection classpath) throws IOException {
    File tempFile;
    tempFile = File.createTempFile(CommandLineGosuDoc.class.getName(), "classpath.jar");

    LOGGER.info("Creating classpath JAR at " + tempFile.getAbsolutePath());
    
    Manifest man = new Manifest();
    man.getMainAttributes().putValue(Attributes.Name.MANIFEST_VERSION.toString(), "1.0");
    man.getMainAttributes().putValue(Attributes.Name.CLASS_PATH.toString(), convertFileCollectionToURIs(classpath));

    //noinspection EmptyTryBlock
    try(FileOutputStream fos = new FileOutputStream(tempFile); 
        JarOutputStream jarOut = new JarOutputStream(fos, man)) {
        //This is a bit silly.
        //The try-with-resources construct with two autoclosable resources saves us 
        //from having to deal with a boilerplate finally block to close the streams.
        //Further, the JarOutputStream constructor with Manifest attribute does all the work we need,
        //which is why the try block is intentionally empty.
    }
    
    return tempFile;
  }
  
  private String convertFileCollectionToURIs(FileCollection files) {
    List<String> entries = new ArrayList<>();

    //noinspection Convert2streamapi
    for(File entry : files) {
      LOGGER.info("Encoding " + entry.getAbsolutePath());
        entries.add(entry.toURI().toString());
    }
    
    return String.join(" ", entries);
  }

  private void setJvmArgs(JavaExecSpec spec, ForkOptions forkOptions) {
    if(forkOptions.getMemoryInitialSize() != null && !forkOptions.getMemoryInitialSize().isEmpty()) {
      spec.setMinHeapSize(forkOptions.getMemoryInitialSize());
    }
    if(forkOptions.getMemoryMaximumSize() != null && !forkOptions.getMemoryMaximumSize().isEmpty()) {
      spec.setMaxHeapSize(forkOptions.getMemoryMaximumSize());
    }

    List<String> args = new ArrayList<>();

    //respect JAVA_OPTS, if it exists
    String JAVA_OPTS = System.getenv("JAVA_OPTS");
    if(JAVA_OPTS != null) {
      args.add(JAVA_OPTS);
    }

    args.addAll(forkOptions.getJvmArgs());

    if(Os.isFamily(Os.FAMILY_MAC)) {
      args.add("-Xdock:name=gosudoc");
    }
    
    spec.setJvmArgs(args);
  }

  private File createArgFile(File tmpDir) throws IOException {
    File tempFile;
    if (LOGGER.isDebugEnabled()) {
      tempFile = File.createTempFile(CommandLineGosuDoc.class.getName(), "arguments", new File(_targetDir.getAbsolutePath()));
    } else {
      tempFile = File.createTempFile(CommandLineGosuDoc.class.getName(), "arguments");
      tempFile.deleteOnExit();
    }

    List<String> fileOutput = new ArrayList<>();

    fileOutput.add("-classpath");
    fileOutput.add(_projectClasspath.getAsPath());

    fileOutput.add("-inputDirs");
    fileOutput.add(tmpDir.getAbsolutePath());

    fileOutput.add("-output");
    fileOutput.add(_targetDir.getAbsolutePath());

    if(_options.isVerbose()) {
      fileOutput.add("-verbose"); //TODO parameterize
    }
    
    Files.write(tempFile.toPath(), fileOutput, StandardCharsets.UTF_8);

    return tempFile;
  }  
  
}
