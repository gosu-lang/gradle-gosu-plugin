package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.VersionNumber
import org.junit.Ignore
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Unroll
class LocalBuildCacheTest extends AbstractGosuPluginSpecification {

    /**
     * For this test only, we override GradleRunner's TestKitDir property with a randomly generated folder.
     * This ensures the local cache does not linger for subsequent test runs.
     */
    @Rule
    TemporaryFolder testKitDir = new TemporaryFolder()

    File srcMainGosu
    File simplePogo

    /**
     * super#setup is invoked automatically
     * @return
     */
    @Override
    def setup() {
        testKitDir.create()
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
    }

    def 'apply gosu plugin and compile [Gradle #gradleVersion]'() {
        given:
        buildScript << getBasicBuildScriptForTesting()
        testProjectDir.newFile('settings.gradle') << 'enableFeaturePreview "STABLE_CONFIGURATION_CACHE"'

        simplePogo = new File(srcMainGosu, asPath('example', 'gradle', 'SimplePogo.gs'))
        simplePogo.getParentFile().mkdirs()
        simplePogo << """
            package example.gradle
            
            class SimplePogo {}"""

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withTestKitDir(testKitDir.root)
                .withPluginClasspath()
                .withArguments('gosudoc', '--build-cache', '--configuration-cache')
//                .forwardOutput()
//                .withDebug(true)

        BuildResult result = runner.build()

        then:
        result.task(":compileGosu").outcome == SUCCESS
        result.task(":gosudoc").outcome == SUCCESS

        result.output.contains('Calculating task graph as no cached configuration is available for tasks: gosudoc')
        result.output.contains('2 actionable tasks: 2 executed')
        result.output.contains('Configuration cache entry stored.')

        assertTaskOutputs()

        when:
        GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withTestKitDir(testKitDir.root)
            .withPluginClasspath()
            .withArguments('clean')
            .build()

        and:
        runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withTestKitDir(testKitDir.root)
                .withPluginClasspath()
                .withArguments('gosudoc', '--build-cache', '--configuration-cache')
//                .forwardOutput()
//                .withDebug(true)

        result = runner.build()

        then:
        result.task(":compileGosu").outcome == FROM_CACHE
        result.task(":gosudoc").outcome == FROM_CACHE

        result.output.contains('Reusing configuration cache.')
        result.output.contains('2 actionable tasks: 2 from cache')
        result.output.contains('Configuration cache entry reused.')

        assertTaskOutputs()
        
        where:
        gradleVersion << gradleVersionsToTest
    }

    private boolean assertTaskOutputs() {
        //did we actually compile anything?
        return new File(testProjectDir.root, asPath(expectedOutputDir(gradleVersion) + ['main', 'example', 'gradle', 'SimplePogo.class'])).exists() &&
        //did we actually doc anything?
        new File(testProjectDir.root, asPath('build', 'docs', 'gosudoc', 'index.html')).exists()

    }
    
}
