package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.*

@Unroll
class SimpleGosuBuildTest extends AbstractGosuPluginSpecification {
    
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

    def 'apply gosu plugin and compile [Gradle #gradleVersion]'() {
        given:
        buildScript << getBasicBuildScriptForTesting()

        simplePogo = new File(srcMainGosu, asPath('example', 'gradle', 'SimplePogo.gs'))
        simplePogo.getParentFile().mkdirs()
        simplePogo << """
            package example.gradle
            
            class SimplePogo {}"""

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath(pluginClasspath)
                .withArguments('compileGosu', '-is')

        BuildResult result = runner.build()
        
        then:
        result.output.contains('Initializing Gosu compiler')
        result.task(":compileGosu").outcome == SUCCESS

        //did we actually compile anything?
        new File(testProjectDir.root, asPath('build', 'classes', 'main', 'example', 'gradle', 'SimplePogo.class')).exists()

        where:
        gradleVersion << gradleVersionsToTest
    }
}
