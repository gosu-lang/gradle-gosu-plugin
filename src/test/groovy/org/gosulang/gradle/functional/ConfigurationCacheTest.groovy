package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class ConfigurationCacheTest extends AbstractGosuPluginSpecification {

    def 'apply gosu plugin and run help with configuration cache in strict mode'() {
        given:
        buildScript << getBasicBuildScriptForTesting()
        testProjectDir.newFile('settings.gradle') << 'enableFeaturePreview "STABLE_CONFIGURATION_CACHE"'

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('help', '--configuration-cache')
                .withGradleVersion(gradleVersion)

        BuildResult result = runner.build()

        then:
        result.task(":help").outcome == SUCCESS
        result.output.contains('Configuration cache entry stored.')

        when:
        result = runner.build()

        then:
        result.task(":help").outcome == SUCCESS
        result.output.contains('Reusing configuration cache.')
        result.output.contains('Configuration cache entry reused.')

        where:
        gradleVersion << gradleVersionsToTest
    }
}
