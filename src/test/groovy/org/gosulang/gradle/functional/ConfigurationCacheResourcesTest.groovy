package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Guards the configuration-cache compatibility of the resources filter that {@code GosuBasePlugin} installs on
 * every source set (issue #68).
 *
 * <p>Before the fix, the resources {@code exclude} was a plain (non-serializable) lambda capturing the gosu
 * {@code SourceDirectorySet}; it serialized into every consumer's {@code ProcessResources} task and produced the
 * dominant block of configuration-cache problems this plugin imposed on consumers. The fix wraps it in
 * {@code SerializableLambdas.spec(...)} capturing only a {@code FileCollection}, mirroring Gradle's own
 * {@code GroovyBasePlugin}/{@code ScalaBasePlugin}.</p>
 *
 * <p>We request {@code processResources} specifically: it pulls {@code ProcessResources} (the task that carried
 * the captured filter) into the task graph WITHOUT pulling in {@code compileGosu}, whose own configuration-cache
 * hazards are addressed separately. Running under {@code STABLE_CONFIGURATION_CACHE} makes any CC problem fail
 * the build.</p>
 */
@Unroll
class ConfigurationCacheResourcesTest extends AbstractGosuPluginSpecification {

    File srcMainGosu
    File srcMainResources

    @Override
    def setup() {
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
        srcMainResources = testProjectDir.newFolder('src', 'main', 'resources')
    }

    def 'processResources is configuration-cache compatible [Gradle #gradleVersion]'() {
        given:
        buildScript << getBasicBuildScriptForTesting()
        testProjectDir.newFile('settings.gradle') << 'enableFeaturePreview "STABLE_CONFIGURATION_CACHE"'

        and: 'a gosu source file (so the resources filter has gosu sources to exclude) and a real resource'
        File pogo = new File(srcMainGosu, asPath('example', 'gradle', 'SimplePogo.gs'))
        pogo.getParentFile().mkdirs()
        pogo << 'package example.gradle\n\nclass SimplePogo {}'
        new File(srcMainResources, 'application.properties') << 'foo=bar'

        when: 'the configuration cache entry is stored'
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('processResources', '--configuration-cache')
                .withGradleVersion(gradleVersion)
        BuildResult storeResult = runner.build()

        then: 'the store succeeds with no CC problems (strict mode would fail the build otherwise)'
        storeResult.task(':processResources').outcome == SUCCESS
        storeResult.output.contains('Configuration cache entry stored.')

        when: 'the build runs again'
        BuildResult reuseResult = runner.build()

        then: 'the entry round-trips and is reused'
        reuseResult.output.contains('Reusing configuration cache.')
        reuseResult.output.contains('Configuration cache entry reused.')

        where:
        gradleVersion << gradleVersionsToTest
    }
}
