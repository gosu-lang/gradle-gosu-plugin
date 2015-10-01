package org.gosulang.gradle.functional;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import static org.junit.Assert.*;

public class SimpleGosuBuildTest extends AbstractGradleTest {

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
    //no-op
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
    String buildFileContent = getBasicBuildScriptForTesting();
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

    System.out.println("--- Dumping stderr ---");
    System.out.println(result.getStandardError());
    System.out.println("--- Done dumping stderrt ---");    
    
    assertTrue(result.getStandardOutput().contains("Initializing Gosu compiler..."));
    assertTrue(result.getStandardError().isEmpty());
    assertEquals(TaskOutcome.SUCCESS, result.task(":compileGosu").getOutcome());

    //did we actually compile anything?
    assertTrue(new File(_testProjectDir.getRoot(), "build/classes/main/example/gradle/SimplePogo.class").exists());
  }



}
