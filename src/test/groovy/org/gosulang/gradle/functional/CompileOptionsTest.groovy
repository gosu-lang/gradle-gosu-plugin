package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Unroll
class CompileOptionsTest extends AbstractGosuPluginSpecification {

    File srcMainGosu
    File errantPogo, simplePogo
    
    /**
     * super#setup is invoked automatically
     * @return
     */
    @Override
    def setup() {
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
    }
    
    def 'pass build even with compile errors if failOnError is true [Gradle #gradleVersion]'() {
        given:
        buildScript << getBasicBuildScriptForTesting() + """
        compileGosu {
            gosuCompileOptions.failOnError = false
        }
        """
       
        errantPogo = new File(srcMainGosu, asPath('example', 'gradle', 'ErrantPogo.gs'))
        errantPogo.getParentFile().mkdirs()
        errantPogo << """
        package example.gradle
        
        class ErrantPogo {
          function doIt() {
            var x : int = "Intentional error"
          }
        }"""

        simplePogo = new File(srcMainGosu, asPath('example', 'gradle', 'SimplePogo.gs'))
        simplePogo.getParentFile().mkdirs()
        simplePogo << """
        package example.gradle
        
        class SimplePogo {
          function doIt() {
            var x = 1
          }
        }"""
        
        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('compileGosu', '-is')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()
        
        then:
        notThrown(UnexpectedBuildFailure)
        result.output.contains('Initializing gosuc compiler')
        result.output.contains('gosuc completed with 0 warnings and 1 errors.')
        result.output.contains(':compileGosu completed with errors, but ignoring as \'gosuOptions.failOnError = false\' was specified.')
        result.output.contains('src/main/gosu/example/gradle/ErrantPogo.gs:[6,27] error: The type "java.lang.String" cannot be converted to "int"')
        result.task(':compileGosu').outcome == SUCCESS

        new File(testProjectDir.root, asPath('build', 'classes', 'main', 'example', 'gradle', 'SimplePogo.class')).exists()
        !new File(testProjectDir.root, asPath('build', 'classes', 'main', 'example', 'gradle', 'ErrantPogo.class')).exists()

        where:
        gradleVersion << gradleVersionsToTest
    }
}
