package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.gradle.testkit.runner.UnexpectedBuildSuccess

import static org.gradle.testkit.runner.TaskOutcome.*

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
 
    def 'log quiet warning under default logging level'() {
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

        BuildResult result = runner.build()
        
        then:
        notThrown(UnexpectedBuildFailure)
        !result.standardOutput.contains('Initializing Gosu compiler...') // this message requires info level and below
        result.standardOutput.contains('Gosu compilation completed with 1 warning')
        result.standardError.empty
        result.task(':compileGosu').outcome == SUCCESS

        //did we actually compile anything?
        new File(testProjectDir.root, asPath('build', 'classes', 'main', 'example', 'gradle', 'SimplePogo.class')).exists()
    }
    
    def 'log compilation error under default logging level'() {
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

        BuildResult result = runner.buildAndFail()

        then:
        notThrown(UnexpectedBuildSuccess)
        result.standardOutput.contains('BUILD FAILED')
        result.standardOutput.contains('Gosu compilation completed with 1 error')
        result.standardError.contains('Gosu compilation failed with errors; see compiler output for details.')
        result.task(':compileGosu').outcome == FAILED

        //did we actually compile anything?
        !new File(testProjectDir.root, asPath('build', 'classes', 'main', 'example', 'gradle', 'SimplePogo.class')).exists()
    }
    
}
