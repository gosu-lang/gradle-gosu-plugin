package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.FAILED
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
            gosuOptions.failOnError = false
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

    def 'fail build fast when warning threshold exceeded [Gradle #gradleVersion]'() {
        given:
        buildScript << getBasicBuildScriptForTesting() + """
        compileGosu {
            gosuOptions.maxWarns = 1
            //gosuOptions.forkOptions.jvmArgs += ['-Xdebug', '-Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=y']
        }
        """

        errantPogo = new File(srcMainGosu, asPath('example', 'gradle', 'ErrantPogo.gs'))
        errantPogo.getParentFile().mkdirs()
        errantPogo << """
        package example.gradle
        
        class ErrantPogo {
          function doIt() {
            var x : int = 42
            var y : int = 74
            var z : int
            x = x //silly assignment warning 1
            y = y //silly assignment warning 2, exceeding threshold
            z = z //silly assignment warning 3
          }
        }"""

        simplePogo = new File(srcMainGosu, asPath('example', 'gradle', 'SimplePogo.gs'))
        simplePogo.getParentFile().mkdirs()
        simplePogo << """
        package example.gradle
        
        class SimplePogo {
          function doIt() {
            var x = 1
            var y = 2
            x = x //silly assignment warning 1
            y = y //silly assignment warning 2, exceeding threshold

          }
        }"""

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('compileGosu', '-is')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.buildAndFail()

        then:
        result.output.contains('Initializing gosuc compiler')
        result.output.contains('Warning threshold exceeded; aborting compilation.')
        result.output.contains('gosuc completed with 2 warnings and 0 errors.')
        result.output.contains('warning: An unnecessary assignment from x to itself occurs here.')
        result.task(':compileGosu').outcome == FAILED

        new File(testProjectDir.root, asPath('build', 'classes', 'main', 'example', 'gradle', 'SimplePogo.class')).exists() //SimplePogo is successfully compiled, but warnings exceed the threshold
        !new File(testProjectDir.root, asPath('build', 'classes', 'main', 'example', 'gradle', 'ErrantPogo.class')).exists() //Compilation aborts before attempting to compile ErrantPogo 

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'fail build fast when error threshold exceeded [Gradle #gradleVersion]'() {
        given:
        buildScript << getBasicBuildScriptForTesting() + """
        compileGosu {
            gosuOptions.maxErrs = 1
            //gosuOptions.forkOptions.jvmArgs += ['-Xdebug', '-Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=y']
        }
        """

        errantPogo = new File(srcMainGosu, asPath('example', 'gradle', 'ErrantPogo.gs'))
        errantPogo.getParentFile().mkdirs()
        errantPogo << """
        package example.gradle
        
        class ErrantPogo {
          function doIt() {
            fail // Intentional error
          }
        }"""

        simplePogo = new File(srcMainGosu, asPath('example', 'gradle', 'SimplePogo.gs'))
        simplePogo.getParentFile().mkdirs()
        simplePogo << """
        package example.gradle
        
        class SimplePogo {
          function doIt() {
            fail // Intentional error
          }
        }"""

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('compileGosu', '-is')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.buildAndFail()

        then:
        result.output.contains('Initializing gosuc compiler')
        result.output.contains('Error threshold exceeded; aborting compilation.')
        result.output.contains('gosuc completed with 0 warnings and 2 errors.')
        result.output.contains('src/main/gosu/example/gradle/SimplePogo.gs:[6,13] error: Not a statement.')
        result.output.contains('src/main/gosu/example/gradle/SimplePogo.gs:[6,13] error: Could not resolve symbol for : fail')
        result.task(':compileGosu').outcome == FAILED

        !new File(testProjectDir.root, asPath('build', 'classes', 'main', 'example', 'gradle', 'SimplePogo.class')).exists() //SimplePogo compilation is attempted and has errors exceeding the threshold
        !new File(testProjectDir.root, asPath('build', 'classes', 'main', 'example', 'gradle', 'ErrantPogo.class')).exists() //Compilation aborts before attempting to compile ErrantPogo 

        where:
        gradleVersion << gradleVersionsToTest
    }

}
