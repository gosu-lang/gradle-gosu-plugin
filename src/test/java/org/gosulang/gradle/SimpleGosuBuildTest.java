package org.gosulang.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class SimpleGosuBuildTest {

  private final URL _pluginClasspathResource = getClass().getClassLoader().getResource("plugin-classpath.txt");
  private final URL _gosuVersionResource = getClass().getClassLoader().getResource("gosuVersion.txt");

  @Rule
  public final TemporaryFolder _testProjectDir = new TemporaryFolder();

  private File _javaSourceRoot;
  private File _gosuSourceRoot;
  private File _javaTestRoot;
  private File _gosuTestRoot;
  private File _buildFile;
  private File _pojo;
  private File _pogo;
  private File _enhancement;
  private File _javaTest;
  private File _gosuTest;

  @BeforeClass
  public static void beforeClass() {
    //noop
  }

  @Before
  public void beforeMethod() throws IOException {
    _buildFile = _testProjectDir.newFile("build.gradle");
    _javaSourceRoot = _testProjectDir.newFolder("src", "main", "java");
    _gosuSourceRoot = _testProjectDir.newFolder("src", "main", "gosu");
    _javaTestRoot = _testProjectDir.newFolder("src", "test", "java");
    _gosuTestRoot = _testProjectDir.newFolder("src", "test", "gosu");
  }

  @Test
  public void applyGosuPlugin() throws IOException {
    List<String> pluginClasspathRaw = new BufferedReader(new FileReader(_pluginClasspathResource.getFile())).lines().collect(Collectors.toList());
    String pluginClasspath = "'" + String.join("', '", pluginClasspathRaw) + "'"; //wrap each entry in single quotes

    String gosuVersion = new BufferedReader(new FileReader(_gosuVersionResource.getFile())).lines().findFirst().get();

    String LF = System.lineSeparator();

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
        "        classpath files(" + pluginClasspath + ")" + LF +
        "    }" + LF +
        "}" + LF +
        "repositories {" + LF +
        "    jcenter() " + LF +
        "    maven {" + LF +
        "        url 'http://gosu-lang.org/nexus/content/repositories/snapshots'" + LF + //for Gosu snapshot builds
        "    }" + LF +
        "}" + LF +
        "apply plugin: 'org.gosu-lang.gosu'";
    writeFile(_buildFile, buildFileContent);

    String simplePogoContent =
        "package example.gradle" + LF +
        LF +
        "public class SimplePogo {}";
    _pogo = new File(_gosuSourceRoot, "example/gradle/SimplePogo.gs");
    _pogo.getParentFile().mkdirs();
    writeFile(_pogo, simplePogoContent);

    System.out.println("--- Dumping build.gradle ---");
    System.out.println(buildFileContent);
    System.out.println("--- Done dumping build.gradle ---");

    BuildResult result = GradleRunner.create()
        .withProjectDir(_testProjectDir.getRoot())
        .withArguments("compileGosu", "-is")
        .build();

    System.out.println("--- Dumping stdout ---");
    System.out.println(result.getStandardOutput());
    System.out.println("--- Done dumping stdout ---");

    assertTrue(result.getStandardOutput().contains("Initializing Gosu compiler..."));
    assertTrue(result.getStandardError().isEmpty());
    assertEquals(TaskOutcome.SUCCESS, result.task(":compileGosu").getOutcome());

    //did we actually compile anything?
    assertTrue(new File(_testProjectDir.getRoot(), "build/classes/main/example/gradle/SimplePogo.class").exists());
  }

  private void writeFile(File destination, String content) throws IOException {
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
