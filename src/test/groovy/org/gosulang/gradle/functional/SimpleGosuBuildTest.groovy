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
                .withPluginClasspath()
                .withArguments('compileGosu', '-is')

        BuildResult result = runner.build()
        
        then:
        result.output.contains('Initializing gosuc compiler')
        result.task(":compileGosu").outcome == SUCCESS

        //did we actually compile anything?
        new File(testProjectDir.root, asPath('build', 'classes', 'main', 'example', 'gradle', 'SimplePogo.class')).exists()

        where:
        gradleVersion << gradleVersionsToTest
    }

/*    @Requires({ 
        Boolean.valueOf(System.getProperty('gosuc.available')) //properties['gosuc.available']) 
    })
    def 'apply gosu plugin and compile using gosuc [Gradle #gradleVersion]'() {
        given:
        buildScript << getBasicBuildScriptForTesting() + """
        compileGosu {
            options.warnings = false
            gosuOptions.forkOptions.with {
                memoryInitialSize = '128m'
                memoryMaximumSize = '1g'
                //jvmArgs += ['-Xdebug', '-Xrunjdwp:transport=dt_shmem,address=gosuc,server=y,suspend=y'] //debug on windows
                //jvmArgs += ['-Xdebug', '-Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=y'] //debug on linux/OS X
            }
        }
        """
        
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
                .withDebug(true)
                .forwardOutput()

        BuildResult result = runner.build()

        then:
        result.output.contains('Initializing gosuc compiler')
        result.output.contains(' completed successfully.')
        result.task(":compileGosu").outcome == SUCCESS

        //did we actually compile anything?
        new File(testProjectDir.root, asPath('build', 'classes', 'main', 'example', 'gradle', 'SimplePogo.class')).exists()

        where:
        gradleVersion << gradleVersionsToTest
    }*/
}
