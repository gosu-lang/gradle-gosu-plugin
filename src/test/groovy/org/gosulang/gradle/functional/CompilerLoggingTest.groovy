package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.gradle.testkit.runner.UnexpectedBuildSuccess
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.*

@Unroll
class CompilerLoggingTest extends AbstractGosuPluginSpecification {

    File srcMainGosu
    File simplePogo

    /**
     * super#setup is invoked automatically
     * @return
     */
    @Override
    def setup() {
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
    }
 
    def 'log quiet warning under default logging level [Gradle #gradleVersion]'() {
        given:
        buildScript << getBasicBuildScriptForTesting()

        simplePogo = new File(srcMainGosu, asPath('example', 'gradle', 'SimplePogo.gs'))
        simplePogo.getParentFile().mkdirs()
        simplePogo << """
            package example.gradle
            
            public class SimplePogo {
              function doIt() {
                var x = 1
                x = x //this line should generate a compiler warning
              }
            }"""
        
        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath(pluginClasspath)
                .withArguments('compileGosu') //intentionally using quiet/default mode here
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()
        
        then:
        notThrown(UnexpectedBuildFailure)
        !result.output.contains('Initializing Gosu compiler for :compileGosu') // this message requires info level and below
        result.output.contains(':compileGosu completed with 1 warning')
        result.task(':compileGosu').outcome == SUCCESS

        //did we actually compile anything?
        new File(testProjectDir.root, asPath('build', 'classes', 'main', 'example', 'gradle', 'SimplePogo.class')).exists()

        where:
        gradleVersion << gradleVersionsToTest
    }
    
    def 'log compilation error under default logging level [Gradle #gradleVersion]'() {
        given:
        buildScript << getBasicBuildScriptForTesting()

        simplePogo = new File(srcMainGosu, asPath('example', 'gradle', 'SimplePogo.gs'))
        simplePogo.getParentFile().mkdirs()
        simplePogo << """
            package example.gradle
            
            public class SimplePogo {
              var x : int = "Intentionally fail compilation"
            }"""

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath(pluginClasspath)
                .withArguments('compileGosu')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.buildAndFail()

        then:
        notThrown(UnexpectedBuildSuccess)
        result.output.contains('BUILD FAILED')
        result.output.contains(':compileGosu completed with 1 error')
        result.output.contains('Gosu compilation failed with errors; see compiler output for details.')
        result.task(':compileGosu').outcome == FAILED

        //did we actually compile anything?
        !new File(testProjectDir.root, asPath('build', 'classes', 'main', 'example', 'gradle', 'SimplePogo.class')).exists()

        where:
        gradleVersion << gradleVersionsToTest
    }
    
}
