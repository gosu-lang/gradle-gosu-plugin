package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.util.stream.Collectors

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.FAILED

class CheckedArithmeticTest extends Specification {

    private final URL _pluginClasspathResource = getClass().getClassLoader().getResource("plugin-classpath.txt")
    private final URL _gosuVersionResource = getClass().getClassLoader().getResource("gosuVersion.txt")

    public final TemporaryFolder _testProjectDir = new TemporaryFolder()

    def setup() {
        _testProjectDir.create()

        def _buildFile = _testProjectDir.newFile("build.gradle")

        def gosuVersion = getGosuVersion(_gosuVersionResource)
        def buildFileContent =
                """
buildscript {
    dependencies {
        classpath files(${getClasspath()})
    }
}
apply plugin: 'org.gosu-lang.gosu'
repositories {
    mavenLocal()
    mavenCentral()
    maven {
        url 'https://oss.sonatype.org/content/repositories/snapshots' //for Gosu snapshot builds
    }
}

dependencies {
    compile group: 'org.gosu-lang.gosu', name: 'gosu-core-api', version: '$gosuVersion'
    testCompile group: 'junit', name: 'junit', version: '4.11'
}

compileGosu {
    gosuOptions.checkedArithmetic = true
}
"""

        writeFile(_buildFile, buildFileContent)

        println("--- Dumping build.gradle ---")
        println(buildFileContent)
        println("--- Done dumping build.gradle ---")

        //make a POGO to compile
        String simplePogoContent =
"""
public class SimplePogo {

  function sayHi() : String {
    var i = Integer.MAX_VALUE
    return "Hello there " + (i + 1) //should cause an ArithmeticException if checkedArithmetic is enabled
  }
}
"""
        File pogo = new File(_testProjectDir.newFolder('src', 'main', 'gosu'), 'SimplePogo.gs')
        pogo.getParentFile().mkdirs()
        writeFile(pogo, simplePogoContent)

        //make a POGO to compile
        String simplePogoTestContent =
"""
uses org.junit.Assert
uses org.junit.Test

public class SimplePogoTest {

  @Test
  function sayHiTest() {
    var x = new SimplePogo()
    Assert.assertNotNull(x)
    Assert.assertTrue(x.sayHi().startsWith("Hello there "))
  }
}
"""
        File pogoTest = new File(_testProjectDir.newFolder('src', 'test', 'gosu'), 'SimplePogoTest.gs')
        pogoTest.getParentFile().mkdirs()
        writeFile(pogoTest, simplePogoTestContent)
    }

    def "Checked arithmetic should cause failure"() {
        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(_testProjectDir.getRoot())
                .withArguments('clean', 'test', '-is')

        BuildResult result = runner.buildAndFail()

        println("--- Dumping stdout ---")
        println(result.getStandardOutput())
        println("--- Done dumping stdout ---")
        println()
        println("--- Dumping stderr ---")
        println(result.getStandardError())
        println("--- Done dumping stderr ---")

        then:
        result.getStandardOutput().contains("java.lang.ArithmeticException: integer overflow")
        result.task(":compileGosu").getOutcome().equals(SUCCESS)
        result.task(":compileTestGosu").getOutcome().equals(SUCCESS)
        result.task(":test").getOutcome().equals(FAILED)
    }

    protected static String getGosuVersion(URL url) throws IOException {
        return new BufferedReader(new FileReader(url.getFile())).lines().findFirst().get()
    }

    protected String getClasspath() throws IOException {
        return getClasspath(_pluginClasspathResource)
    }

    protected String getClasspath(URL url) throws IOException {
        List<String> pluginClasspathRaw = new BufferedReader(new FileReader(url.getFile())).lines().collect(Collectors.toList())
        return "'" + String.join("', '", pluginClasspathRaw) + "'" //wrap each entry in single quotes
    }

    protected static void writeFile(File destination, String content) throws IOException {
        BufferedWriter output = null
        try {
            output = new BufferedWriter(new FileWriter(destination))
            output.write(content)
        } finally {
            if (output != null) {
                output.close()
            }
        }
    }
}