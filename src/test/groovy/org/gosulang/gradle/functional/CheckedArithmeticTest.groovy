package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.FAILED

class CheckedArithmeticTest extends AbstractGosuPluginSpecification {

    File simplePogo, simplePogoTest
    
    /**
     * super#setup is invoked automatically
     * @return
     */
    def setup() {
        simplePogo = new File(testProjectDir.newFolder('src', 'main', 'gosu'), 'SimplePogo.gs')
        simplePogoTest = new File(testProjectDir.newFolder('src', 'test', 'gosu'), 'SimplePogoTest.gs')
    }

    def 'Checked arithmetic should cause failure [Gradle #gradleVersion]'() {
        given:
        buildScript << getBasicBuildScriptForTesting() + """
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
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.buildAndFail()

        then:
        result.output.contains('java.lang.ArithmeticException: integer overflow')
        result.task(':compileGosu').outcome == SUCCESS
        result.task(':compileTestGosu').outcome == SUCCESS
        result.task(':test').outcome == FAILED

        where:
        gradleVersion << gradleVersionsToTest
    }

}