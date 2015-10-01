package org.gosulang.gradle.functional;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class SourceSetsModificationTest extends AbstractGradleTest {

  @Rule
  public final TemporaryFolder _testProjectDir = new TemporaryFolder();

  private File _gosuSourceRoot;
  private File _buildFile;

  @BeforeClass
  public static void beforeClass() {
    //noop
  }

  @Before
  public void beforeMethod() throws IOException {
    _buildFile = _testProjectDir.newFile("build.gradle");
    _gosuSourceRoot = _testProjectDir.newFolder("src", "main", "gosu");
  }

  @Test
  public void nonStandardSourceRoots() throws IOException {
    String buildFileContent = getBasicBuildScriptForTesting();

    String[] rootOne = {"folder", "containing", "POGOs"};
    String[] rootTwo = {"foo", "bar"};
    String[] rootThree = {"baz"};
    String[] ignoredRoot = {"ignored", "source", "root"};

    File configuredSourceRootOne = _testProjectDir.newFolder(rootOne);
    File configuredSourceRootTwo = _testProjectDir.newFolder(rootTwo);
    File configuredSourceRootThree = _testProjectDir.newFolder(rootThree);
    File ignoredSourceRoot = _testProjectDir.newFolder(ignoredRoot);

    buildFileContent +=
        "sourceSets {" + LF +
            "    main {" + LF +
            "        gosu {" + LF +
            "            srcDir '" + asPath(rootOne) + "'" + LF +
            "            srcDirs '" + asPath(rootTwo) + "', '" + asPath(rootThree) + "'" + LF +
            "        }" + LF +
            "    }" + LF +
            "}" + LF;

    writeFile(_buildFile, buildFileContent);

    String configuredPogoOne =
        "package one" + LF +
            LF +
            "public class ConfiguredPogoOne {}";
    File pogo = new File(configuredSourceRootOne, asPath("one", "ConfiguredPogoOne.gs"));
    pogo.getParentFile().mkdirs();
    writeFile(pogo, configuredPogoOne);

    String configuredPogoTwo =
        "package two" + LF +
            LF +
            "public class ConfiguredPogoTwo {}";
    pogo = new File(configuredSourceRootTwo, asPath("two", "ConfiguredPogoTwo.gs"));
    pogo.getParentFile().mkdirs();
    writeFile(pogo, configuredPogoTwo);

    String configuredPogoThree =
        "package three" + LF +
            LF +
            "public class ConfiguredPogoThree {}";
    pogo = new File(configuredSourceRootThree, asPath("three", "ConfiguredPogoThree.gs"));
    pogo.getParentFile().mkdirs();
    writeFile(pogo, configuredPogoThree);

    String ignoredPogo =
        "package four" + LF +
            LF +
            "public class IgnoredPogo {}";
    pogo = new File(ignoredSourceRoot, "IgnoredPogo.gs");
    pogo.getParentFile().mkdirs();
    writeFile(pogo, ignoredPogo);

    System.out.println("--- Dumping build.gradle ---");
    System.out.println(buildFileContent);
    System.out.println("--- Done dumping build.gradle ---");

    BuildResult result = GradleRunner.create()
        .withProjectDir(_testProjectDir.getRoot())
        .withArguments("build", "-is")
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
    assertEquals(TaskOutcome.UP_TO_DATE, result.task(":compileTestGosu").getOutcome()); //no tests to compile
    assertEquals(TaskOutcome.UP_TO_DATE, result.task(":test").getOutcome()); //no tests to compile

    //did we actually compile anything?
    File buildOutputRoot = new File(_testProjectDir.getRoot(), asPath("build", "classes", "main"));
    assertTrue(new File(buildOutputRoot, asPath("one", "ConfiguredPogoOne.class")).exists());
    assertTrue(new File(buildOutputRoot, asPath("two", "ConfiguredPogoTwo.class")).exists());
    assertTrue(new File(buildOutputRoot, asPath("three", "ConfiguredPogoThree.class")).exists());
    assertFalse(new File(buildOutputRoot, asPath("four", "IgnoredPogo.class")).exists());
  }
}
