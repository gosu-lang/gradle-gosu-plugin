package org.gosulang.gradle.functional.gosudoc

import org.gosulang.gradle.functional.AbstractGosuPluginSpecification
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import spock.lang.Unroll
import spock.lang.Ignore

@Unroll
@Ignore
class GosudocOptionsTest extends AbstractGosuPluginSpecification {

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

    def 'execute gosudoc with default options [Gradle #gradleVersion]'() {
        given:
        buildScript << getBasicBuildScriptForTesting()

        simplePogo = new File(srcMainGosu, asPath('example', 'gradle', 'SimplePogo.gs'))
        simplePogo.getParentFile().mkdirs()
        simplePogo << """
        package example.gradle
        
        /**
         * I can has gosudoc
         */
        class SimplePogo {
          
          function doIt(intArg : int) : String {
            var x = intArg
            return x as String
          }
        }"""

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('gosudoc', '-is')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()

        then:
        notThrown(UnexpectedBuildFailure)
        result.output.contains('Generating Documentation')
        result.output.contains('example.gradle.SimplePogo - document : true')

        // Verify presence of JAVA_TOOL_OPTIONS sent to stderr does not fail task execution
        // JAVA_TOOL_OPTIONS is echoed to stderr... amazing.
        result.output.contains('Picked up JAVA_TOOL_OPTIONS: -Duser.language=en')

        File gosudocOutputRoot = new File(testProjectDir.root, asPath('build', 'docs', 'gosudoc'))
        File simplePogoGosudoc = new File(gosudocOutputRoot, asPath('example', 'gradle', 'example.gradle.SimplePogo.html'))

        //validate the generated HTML
        simplePogoGosudoc.exists()
        simplePogoGosudoc.readLines().contains('<div class="block">I can has gosudoc</div>')

        where:
        gradleVersion << gradleVersionsToTest
    }

}
