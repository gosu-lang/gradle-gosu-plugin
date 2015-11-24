package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.VersionNumber
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Unroll

import java.util.stream.Collectors

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Gradle 2.9 introduced a withGradleVersion() method to the testkit's GradleRunner API
 * This is very useful for testing iteratively against older Gradle versions.
 *  - however - 
 * All of our functional tests use the GradleRunner#withPluginClasspath method, introduced in Gradle 2.8
 *
 * Instead of reverting all functional tests to the pre-2.8 API, where the plugin classpath is substituted
 * into the buildscript, this test will use the Gradle 2.7-style GradleRunner, which should allow us to run a basic
 * sanity test against many older versions of Gradle.
 */
@Unroll
class SimpleGosuBuildWIthLegacyTestKitApiTest extends Specification {

    protected static final String LF = System.lineSeparator
    protected static final String FS = File.separator

    @Rule
    final TemporaryFolder testProjectDir = new TemporaryFolder()

    protected final URL _pluginClasspathResource = this.class.classLoader.getResource("plugin-classpath.txt")
    protected final URL _gosuVersionResource = this.class.classLoader.getResource("gosuVersion.txt")

    File buildScript
    File simplePogo, simpleTestPogo

    def setup() {
        testProjectDir.create()
        buildScript = testProjectDir.newFile('build.gradle')
        buildScript << 
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
    }

    @Ignore('Must be run locally. As of Gradle 2.9, no way to prevent TestKit from spawning many daemons, which causes OOME on CircleCI')
    def 'End-to-end classpath test [Gradle #gradleVersion]'() {
        
        given:
        simplePogo = new File(testProjectDir.root, asPath('src', 'main', 'gosu', 'SimplePogo.gs'))
        simplePogo.getParentFile().mkdirs()
        simplePogo << 
            """
            public class SimplePogo {
          
              function sayHi() : String {
                return "Hello there"
              }
            }
            """
        
        simpleTestPogo = new File(testProjectDir.root, asPath('src', 'test', 'gosu', 'SimpleTestPogo.gs'))
        simpleTestPogo.getParentFile().mkdirs()
        simpleTestPogo <<   
            """
            uses org.junit.Assert
            uses org.junit.Test
            
            public class SimpleTestPogo {
            
              @Test
              function sayHiTest() {
                var x = new SimplePogo()
                Assert.assertNotNull(x)
                Assert.assertEquals("Hello there", x.sayHi())
              }
            }
            """

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('build', '-is')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result
        result = knownBreak ? runner.buildAndFail() 
                            : runner.build()

        then:
        
        if(knownBreak) {
            result.output.contains('BUILD FAILED')
        } else {
            result.output.contains('BUILD SUCCESSFUL')
            
            //GradleRunner was introduced in 2.6; don't try to get result#task for verisons prior to that 
            if(VersionNumber.parse(gradleVersion) >= VersionNumber.parse('2.6')) {
                result.task(":compileGosu").outcome == SUCCESS
                result.task(":compileTestGosu").outcome == SUCCESS
                result.task(":test").outcome == SUCCESS
                result.task(":build").outcome == SUCCESS
            }
    
            new File(testProjectDir.root, asPath('build', 'classes', 'main', 'SimplePogo.class')).exists()
            new File(testProjectDir.root, asPath('build', 'classes', 'test', 'SimpleTestPogo.class')).exists()
        }

        where:
        // These are the versions of gradle to iteratively test against
        gradleVersion | knownBreak
        '2.3-rc-3'    | false
        '2.3'         | false
        '2.4'         | false
        '2.5'         | true    // Use of gradleTestKit() dependency causes compile failure 
        '2.6'         | true    // FileCollection API changes break our plugin
        '2.7'         | true    // "
        '2.8'         | false
        '2.9'         | false
        
    }

    /**
     * @param An iterable of files and directories
     * @return Delimited String of the values, joined as suitable for use in a classpath statement
     */
    protected String asPath(String... values) {
        return String.join(FS, values);
    }

    protected String getGosuVersion() {
        return getGosuVersion(_gosuVersionResource)
    }

    protected String getGosuVersion(URL url) {
        return new BufferedReader(new FileReader(url.file)).lines().findFirst().get()
    }
    
    protected String getClasspath() throws IOException {
        return getClasspath(_pluginClasspathResource)
    }

    protected String getClasspath(URL url) throws IOException {
        List<String> pluginClasspathRaw = new BufferedReader(new FileReader(url.getFile())).lines().collect(Collectors.toList())
        return "'" + String.join("', '", pluginClasspathRaw) + "'" //wrap each entry in single quotes
    }
}
