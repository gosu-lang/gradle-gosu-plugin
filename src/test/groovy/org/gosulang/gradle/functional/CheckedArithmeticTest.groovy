package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.FAILED

class CheckedArithmeticTest extends Specification {

    @Rule 
    final TemporaryFolder testProjectDir = new TemporaryFolder()
    File buildFile, simplePogo, simplePogoTest
    List<File> pluginClasspath
    
    private final URL _pluginClasspathResource = getClass().getClassLoader().getResource("plugin-classpath.txt")
    private final URL _gosuVersionResource = getClass().getClassLoader().getResource("gosuVersion.txt")

    def setup() {
        testProjectDir.create()

        buildFile = testProjectDir.newFile('build.gradle')
        simplePogo = new File(testProjectDir.newFolder('src', 'main', 'gosu'), 'SimplePogo.gs')
        simplePogoTest = new File(testProjectDir.newFolder('src', 'test', 'gosu'), 'SimplePogoTest.gs')
        
        pluginClasspath = getClasspath()
    }

    def "Checked arithmetic should cause failure"() {
        given:
        def gosuVersion = getGosuVersion(_gosuVersionResource)
        
        buildFile << """
            plugins {
                id 'org.gosu-lang.gosu'
            }
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
        
        simplePogo << """
            public class SimplePogo {
            
              function sayHi() : String {
                var i = Integer.MAX_VALUE
                return "Hello there " + (i + 1) //should cause an ArithmeticException if checkedArithmetic is enabled
              }
            }
        """

        simplePogoTest << """
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

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath(pluginClasspath)
                .withArguments('clean', 'test', '-is')

        BuildResult result = runner.buildAndFail()

        println("--- Dumping stdout ---")
        println(result.standardOutput)
        println("--- Done dumping stdout ---")
        println()
        println("--- Dumping stderr ---")
        println(result.standardError)
        println("--- Done dumping stderr ---")

        then:
        result.standardOutput.contains('java.lang.ArithmeticException: integer overflow')
        result.task(":compileGosu").outcome == SUCCESS
        result.task(":compileTestGosu").outcome == SUCCESS
        result.task(":test").outcome == FAILED
    }

    protected static String getGosuVersion(URL url) throws IOException {
        return new BufferedReader(new FileReader(url.getFile())).lines().findFirst().get()
    }

    protected List<File> getClasspath() throws IOException {
        return getClasspath(_pluginClasspathResource)
    }

    protected List<File> getClasspath(URL url) throws IOException {
        return url.readLines().collect { new File( it ) }
    }

}