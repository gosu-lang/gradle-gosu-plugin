package org.gosulang.gradle.functional;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractGradleTest {

  protected static final String LF = System.lineSeparator();
  protected static final String FS = File.separator;

  protected final URL _pluginClasspathResource = getClass().getClassLoader().getResource("plugin-classpath.txt");
  protected final URL _gosuVersionResource = getClass().getClassLoader().getResource("gosuVersion.txt");

  protected String getClasspath() throws IOException {
    return getClasspath(_pluginClasspathResource);
  }

  protected String getClasspath(URL url) throws IOException {
    List<String> pluginClasspathRaw = new BufferedReader(new FileReader(url.getFile())).lines().collect(Collectors.toList());
    return "'" + String.join("', '", pluginClasspathRaw) + "'"; //wrap each entry in single quotes
  }

  protected String getGosuVersion() throws IOException {
    return getGosuVersion(_gosuVersionResource);
  }

  protected String getGosuVersion(URL url) throws IOException {
    return new BufferedReader(new FileReader(url.getFile())).lines().findFirst().get();
  }

  protected String getBasicBuildScriptForTesting() throws IOException {
    String gosuVersion = getGosuVersion();
    String buildFileContent =
        "buildscript {" + LF +
            "    repositories {" + LF +
            "        jcenter() " + LF +
            "        maven {" + LF +
            "            url 'http://gosu-lang.org/nexus/content/repositories/snapshots'" + LF + //for Gosu snapshot builds
            "        }" + LF +
            "    }" + LF +
            "    dependencies {" + LF +
            "        classpath 'org.gosu-lang.gosu:gosu-core:" + gosuVersion + "'" + LF +       // special hack for gradleTestKit - ordinarily these dependencies will be resolved by the gosu plugin's dependencies
            "        classpath 'org.gosu-lang.gosu:gosu-core-api:" + gosuVersion + "'" + LF +   // special hack for gradleTestKit - ordinarily these dependencies will be resolved by the gosu plugin's dependencies
            "        classpath files(" + getClasspath() + ")" + LF +
            "    }" + LF +
            "}" + LF +
            "repositories {" + LF +
            "    jcenter() " + LF +
            "    maven {" + LF +
            "        url 'http://gosu-lang.org/nexus/content/repositories/snapshots'" + LF + //for Gosu snapshot builds
            "    }" + LF +
            "}" + LF +
            "apply plugin: 'org.gosu-lang.gosu'" + LF;


    return buildFileContent;
  }

  protected String asPath(String... values) {
    return String.join(FS, values);
  }

  protected String asPackage(String... values) {
    return String.join(".", values);
  }

  protected void writeFile(File destination, String content) throws IOException {
    BufferedWriter output = null;
    try {
      output = new BufferedWriter(new FileWriter(destination));
      output.write(content);
    } finally {
      if (output != null) {
        output.close();
      }
    }
  }

}
