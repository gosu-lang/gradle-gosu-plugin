package org.gosulang.gradle.functional;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.gradle.testkit.runner.TaskOutcome.*;
import static org.junit.Assert.*;

public class SanityTest extends AbstractGradleTest {

  @Rule
  public final TemporaryFolder testProjectDir = new TemporaryFolder();
  private File _buildFile;

  @Before
  public void beforeMethod() throws IOException {
    _buildFile = testProjectDir.newFile("build.gradle");
  }

  @Test
  public void helloWorld() throws IOException {
    String buildFileContent = "task helloWorld {" +
        "    doLast {" +
        "        println 'Hello world!'" +
        "    }" +
        "}";
    writeFile(_buildFile, buildFileContent);

    BuildResult result = GradleRunner.create()
        .withProjectDir(testProjectDir.getRoot())
        .withArguments("helloWorld", "-i")
        .build();

    System.out.println("--- Dumping stdout ---");
    System.out.println(result.getStandardOutput());
    System.out.println("--- Done dumping stdout ---");

    assertTrue(result.getStandardOutput().contains("Hello world!"));
    assertTrue(result.getStandardError().isEmpty());
    assertEquals(result.task(":helloWorld").getOutcome(), SUCCESS);
  }

}
