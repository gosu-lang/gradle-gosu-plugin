package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.gradle.testkit.runner.UnexpectedBuildSuccess
import spock.lang.Ignore
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

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

    @Ignore('Currently failing with worker API; all stdout from the child process is echoed')
    def 'does not log warning under default logging level [Gradle #gradleVersion]'() {
        given:
        buildScript << getBasicBuildScriptForTesting() /*<< '''
compileGosu.gosuOptions.fork = true
compileGosu.gosuOptions.forkOptions.executable = 'gosuc'
'''*/

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
                .withPluginClasspath()
                .withArguments('compileGosu') //intentionally using quiet/default mode here
                .withGradleVersion(gradleVersion)
                .forwardOutput()
        //.withDebug(true)

        BuildResult result = runner.build()
        
        then:
        notThrown(UnexpectedBuildFailure)
        !result.output.contains('Initializing Gosu compiler for :compileGosu') // this message requires info level and below
        !result.output.contains('gosuc completed with 1 warnings and 0 errors.')
        result.task(':compileGosu').outcome == SUCCESS

        //did we actually compile anything?
        new File(testProjectDir.root, asPath(expectedOutputDir(gradleVersion) + ['main', 'example', 'gradle', 'SimplePogo.class'])).exists()

        where:
        gradleVersion << gradleVersionsToTest
    }

    
    def 'log warning under info logging level [Gradle #gradleVersion]'() {
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
                .withPluginClasspath()
                .withArguments('compileGosu', '-i') //intentionally using info mode here
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()

        then:
        notThrown(UnexpectedBuildFailure)
        result.output.contains('Initializing gosuc compiler') // this message requires info level and below
        result.output.contains('gosuc completed with 1 warnings and 0 errors.')
        result.task(':compileGosu').outcome == SUCCESS

        //did we actually compile anything?
        new File(testProjectDir.root, asPath(expectedOutputDir(gradleVersion) + ['main', 'example', 'gradle', 'SimplePogo.class'])).exists()

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
                .withPluginClasspath()
                .withArguments('compileGosu')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.buildAndFail()

        then:
        notThrown(UnexpectedBuildSuccess)
        result.output.contains('BUILD FAILED')
        result.output.contains('gosuc completed with 0 warnings and 1 errors.')
        //result.output.contains('Compilation failed with exit code 1; see the compiler error output for details.') //TODO only when fork and executable == gosuc
        result.task(':compileGosu').outcome == FAILED

        //did we actually compile anything?
        !new File(testProjectDir.root, asPath(expectedOutputDir(gradleVersion) + ['main', 'example', 'gradle', 'SimplePogo.class'])).exists()

        where:
        gradleVersion << gradleVersionsToTest
    }
    
}
