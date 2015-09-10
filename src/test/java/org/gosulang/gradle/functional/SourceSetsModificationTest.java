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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class SourceSetsModificationTest extends AbstractGradleTest {

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
//    _javaSourceRoot = _testProjectDir.newFolder("src", "main", "java");
    _gosuSourceRoot = _testProjectDir.newFolder("src", "main", "gosu");
//    _javaTestRoot = _testProjectDir.newFolder("src", "test", "java");
//    _gosuTestRoot = _testProjectDir.newFolder("src", "test", "gosu");
  }

  @Test
  public void nonStandardSourceRoots() throws IOException {
    String buildFileContent = getBasicBuildScriptForTesting();

    String[] rootOne = {"folder", "containing", "POGOs"};
    String[] rootTwo = {"foo", "bar"};
    String[] rootThree = {"baz"};
    String[] ignoredRoot = {"ignored", "source", "root"};

    File configuredSourceRootOne = _testProjectDir.newFolder(rootOne); //.join("/", rootOne); // "folder/containing/POGOs";
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
        "package " + asPackage(rootOne) + LF +
            LF +
            "public class ConfiguredPogoOne {}";
    _pogo = new File(configuredSourceRootOne, "ConfiguredPogoOne.gs");
    _pogo.getParentFile().mkdirs();
    writeFile(_pogo, configuredPogoOne);

    String configuredPogoTwo =
        "package " + asPackage(rootTwo) + LF +
            LF +
            "public class ConfiguredPogoTwo {}";
    _pogo = new File(configuredSourceRootTwo, "ConfiguredPogoTwo.gs");
    _pogo.getParentFile().mkdirs();
    writeFile(_pogo, configuredPogoTwo);

    String configuredPogoThree =
        "package " + asPackage(rootThree) + LF +
            LF +
            "public class ConfiguredPogoThree {}";
    _pogo = new File(configuredSourceRootThree, "ConfiguredPogoThree.gs");
    _pogo.getParentFile().mkdirs();
    writeFile(_pogo, configuredPogoThree);

    String ignoredPogo =
        "package " + asPackage(ignoredRoot) + LF +
            LF +
            "public class IgnoredPogo {}";
    _pogo = new File(ignoredSourceRoot, "IgnoredPogo.gs");
    _pogo.getParentFile().mkdirs();
    writeFile(_pogo, ignoredPogo);

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

    assertTrue(result.getStandardOutput().contains("Initializing Gosu compiler..."));
    assertTrue(result.getStandardError().isEmpty());
    assertEquals(TaskOutcome.SUCCESS, result.task(":compileGosu").getOutcome());
    assertEquals(TaskOutcome.UP_TO_DATE, result.task(":compileTestGosu").getOutcome()); //no tests to compile
    assertEquals(TaskOutcome.UP_TO_DATE, result.task(":test").getOutcome()); //no tests to compile

    //did we actually compile anything?
    assertTrue(new File(_testProjectDir.getRoot(), asPath("build", "classes", asPath(rootOne), "ConfiguredPogoOne.class")).exists());
    assertTrue(new File(_testProjectDir.getRoot(), configuredSourceRootTwo.getPath() + "/ConfiguredPogoOne.class").exists());
    assertTrue(new File(_testProjectDir.getRoot(), configuredSourceRootThree.getPath() + "/ConfiguredPogoOne.class").exists());
    assertFalse(new File(_testProjectDir.getRoot(), ignoredSourceRoot.getPath() + "/IgnoredPogo.class").exists());
  }
}
