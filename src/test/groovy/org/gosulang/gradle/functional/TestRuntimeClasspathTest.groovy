package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Unroll
class TestRuntimeClasspathTest extends AbstractGosuPluginSpecification {

    File simplePogo, simpleTestPogo
    
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
    def 'End-to-end classpath test [Gradle #gradleVersion]'() {
        given:
        buildScript << getBasicBuildScriptForTesting()
        
        simplePogo = new File(testProjectDir.root, asPath('src', 'main', 'gosu', 'SimplePogo.gs'))
        simplePogo.getParentFile().mkdirs()
        simplePogo << """
                      public class SimplePogo {
                      
                        function sayHi() : String {
                          return "Hello there"
                        }
                      }
                      """

        simpleTestPogo = new File(testProjectDir.root, asPath('src', 'test', 'gosu', 'SimpleTestPogo.gs'))
        simpleTestPogo.getParentFile().mkdirs()
        simpleTestPogo <<   """
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
                .withPluginClasspath(pluginClasspath)
                .withArguments('clean', 'test', '-is')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(":test").outcome == SUCCESS
        
        new File(testProjectDir.root, asPath('build', 'classes', 'main', 'SimplePogo.class')).exists()
        new File(testProjectDir.root, asPath('build', 'classes', 'test', 'SimpleTestPogo.class')).exists()

        where:
        gradleVersion << gradleVersionsToTest
    }


}