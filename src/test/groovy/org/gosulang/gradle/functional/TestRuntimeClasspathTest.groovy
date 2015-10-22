package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.util.stream.Collectors

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class TestRuntimeClasspathTest extends Specification {

    private final URL _pluginClasspathResource = getClass().getClassLoader().getResource("plugin-classpath.txt")
    private final URL _gosuVersionResource = getClass().getClassLoader().getResource("gosuVersion.txt")

    public final TemporaryFolder _testProjectDir = new TemporaryFolder()

    /**
     *  Creates a build script, simple Gosu class and a simple JUnit test to instantiate the class
     *  
     *  This performs a valuable end-to-end check of three key factors:
     *  <ol>
     *    <li>The result of the compileGosu task are included in compileTestGosu's classpath
     *    <li>The result of both compileGosu and compileTestGosu are included in the testRuntime configuration
     *    <li>The gosu-core jar is included in the testRuntime configuration - otherwise typesystem initialization will wail when executing tests, despite compiling them successfully
     *  </ol>
     */
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
    return "Hello there"
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
    Assert.assertEquals("Hello there", x.sayHi())
  }
}
"""
        File pogoTest = new File(_testProjectDir.newFolder('src', 'test', 'gosu'), 'SimplePogoTest.gs')
        pogoTest.getParentFile().mkdirs()
        writeFile(pogoTest, simplePogoTestContent)
    }
    
    def "End-to-end classpath test"() {
        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(_testProjectDir.getRoot())
                .withArguments('clean', 'test', '-is')

        BuildResult result = runner.build()

        println("--- Dumping stdout ---")
        println(result.getStandardOutput())
        println("--- Done dumping stdout ---")
        println()
        println("--- Dumping stderr ---")
        println(result.getStandardError())
        println("--- Done dumping stderr ---")

        then:
        notThrown(UnexpectedBuildFailure)
        result.getStandardError().isEmpty()
        result.task(":test").getOutcome().equals(SUCCESS)
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