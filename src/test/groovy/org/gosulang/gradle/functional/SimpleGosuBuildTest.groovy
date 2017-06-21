package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

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
                .withPluginClasspath()
                .withArguments('compileGosu', '-is')
                .withGradleVersion(gradleVersion)

        BuildResult result = runner.build()
        
        then:
        result.output.contains('Initializing gosuc compiler')
        result.task(":compileGosu").outcome == SUCCESS

        // Verify presence of JAVA_TOOL_OPTIONS sent to stderr does not fail task execution
        // JAVA_TOOL_OPTIONS is echoed to stderr... amazing.
        result.output.contains('Picked up JAVA_TOOL_OPTIONS: -Duser.language=en') 
        
        //did we actually compile anything?
        new File(testProjectDir.root, asPath(expectedOutputDir(gradleVersion) + ['main', 'example', 'gradle', 'SimplePogo.class'])).exists()

        where:
        gradleVersion << gradleVersionsToTest
    }

}
