package org.gosulang.gradle.tasks;

import org.gradle.api.Buildable;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.FailingFileCollection;
import org.gradle.api.internal.file.collections.LazilyInitializedFileCollection;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.plugins.jvm.internal.JvmPluginServices;
import org.gradle.util.VersionNumber;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class GosuRuntime {

  private static final Pattern GOSU_JAR_PATTERN = Pattern.compile("gosu-(\\w.*?)-(\\d.*).jar");
  private static final String LF = System.lineSeparator();

  private final Project _project;
  private final JvmPluginServices _jvmPluginServices;

  public GosuRuntime(Project project) {
    _project = project;
    _jvmPluginServices = ((ProjectInternal) project).getServices().get(JvmPluginServices.class);
  }

  /**
   * Searches the specified classpath for a 'gosu-core-api' Jar, and returns a classpath 
   * containing a corresponding (same version) 'gosu-doc' Jar and its dependencies, which includes 'gosu-core'. 
   *
   * <p>The returned class path may be empty, or may fail to resolve when asked for its contents.
   *
   * @param classpath a classpath containing a 'gosu-core-api' Jar
   * @return a classpath containing a corresponding 'gosu-doc' Jar and its dependencies
   */
  public FileCollection inferGosuClasspath(final Iterable<File> classpath) {
    return new LazilyInitializedFileCollection(((ProjectInternal) _project).getTaskDependencyFactory()) {
      @Override
      public String getDisplayName() {
        return "Gosu runtime classpath";
      }

      @Override
      public FileCollection createDelegate() {
        try {
          return inferGosuClasspath();
        } catch (RuntimeException e) {
          return new FailingFileCollection(getDisplayName(), e);
        }
      }

      private Configuration inferGosuClasspath() {
        File gosuCoreApiJar = findGosuJar(classpath, "core-api");
        if (gosuCoreApiJar == null) {
          List<String> classpathAsStrings = new ArrayList<>();
          classpath.forEach(file -> classpathAsStrings.add(file.getAbsolutePath()));
          String flattenedClasspath = String.join(":", classpathAsStrings);
          String errorMsg = String.format("Cannot infer Gosu classpath because the Gosu Core API Jar was not found." + LF +
                  "Does %s declare dependency to gosu-core-api? Searched classpath: %s.", _project, flattenedClasspath) + LF +
                  "An example dependencies closure may resemble the following:" + LF +
                  LF +
                  "dependencies {" + LF +
                  "    implementation 'org.gosu-lang.gosu:gosu-core-api:1.14.3' //a newer version may be available" + LF +
                  "}" + LF;
          _project.getLogger().quiet(errorMsg);
          throw new GradleException(errorMsg);
        }

        String gosuCoreApiRawVersion = getGosuVersion(gosuCoreApiJar);

        if (gosuCoreApiRawVersion == null) {
          throw new AssertionError(String.format("Unexpectedly failed to parse version of Gosu Jar file: %s in %s", gosuCoreApiJar, _project));
        }

        //Use Gradle's VersionNumber construct, which implements Comparable
        VersionNumber gosuCoreApiVersion = VersionNumber.parse(gosuCoreApiRawVersion);

        //Gosu dist with gosuc executable is required
        if (!gosuCoreApiRawVersion.endsWith("-SNAPSHOT") && !hasGosuc(gosuCoreApiVersion)) {
          throw new GradleException(String.format("Please declare a dependency on Gosu version 1.13.9, 1.14.2 or greater. Found: %s", gosuCoreApiRawVersion));
        }

        Configuration gosuRuntimeClasspath = _project.getConfigurations().detachedConfiguration();
        gosuRuntimeClasspath.getDependencies().add(_project.getDependencies().create("org.gosu-lang.gosu:gosu-doc:" + gosuCoreApiRawVersion));
        _jvmPluginServices.configureAsRuntimeClasspath(gosuRuntimeClasspath);
        return gosuRuntimeClasspath;
      }

      @Override
      public void visitDependencies(TaskDependencyResolveContext context) {
        if (classpath instanceof Buildable) {
          context.add(classpath);
        }
      }
    };
  }

  /**
   * Searches the specified class path for a Gosu Jar file (gosu-core, gosu-core-api, etc.) with the specified appendix (compiler, library, jdbc, etc.).
   * If no such file is found, {@code null} is returned.
   *
   * @param classpath the class path to search
   * @param appendix the appendix to search for
   * @return a Gosu Jar file with the specified appendix
   */
  @Nullable
  public File findGosuJar(Iterable<File> classpath, String appendix) {
    for (File file : classpath) {
      Matcher matcher = GOSU_JAR_PATTERN.matcher(file.getName());
      if (matcher.matches() && matcher.group(1).equals(appendix)) {
        return file;
      }
    }
    return null;
  }

  /**
   * Determines the version of a Gosu Jar file (gosu-core, gosu-core-api, etc.). 
   * If the version cannot be determined, or the file is not a Gosu
   * Jar file, {@code null} is returned.
   *
   * <p>Implementation note: The version is determined by parsing the file name, which
   * is expected to match the pattern 'gosu-[component]-[version].jar'.
   *
   * @param gosuJar a Gosu Jar file
   * @return the version of the Gosu Jar file
   */
  @Nullable
  public String getGosuVersion( File gosuJar ) {
    Matcher matcher = GOSU_JAR_PATTERN.matcher(gosuJar.getName());
    return matcher.matches() ? matcher.group(2) : null;
  }

  /**
   * @param gosuVersion The VersionNumber of the provided Gosu dependency
   * @return True if the Gosu version contains the gosuc command-line executable (Gosu 1.13.9+, or 1.14.2+); false otherwise
   */
  public boolean hasGosuc(VersionNumber gosuVersion) {
    VersionNumber gosu = gosuVersion.getBaseVersion();
    return gosu.compareTo(VersionNumber.parse("1.13.8")) > 0 &&
        !gosu.equals(VersionNumber.parse("1.14.0")) &&
        !gosu.equals(VersionNumber.parse("1.14.1"));
  }

}