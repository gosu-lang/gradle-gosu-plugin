package org.gosulang.gradle.functional.gosudoc

import org.gosulang.gradle.functional.AbstractGosuPluginSpecification
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Guards the configuration-cache compatibility of the {@code gosudoc} task (issue #68).
 *
 * <p>See {@code ConfigurationCacheCompileTest} for a detailed explanation of the CC model,
 * the convention-mapping approach, and the field-naming requirement.</p>
 *
 * <p>We use {@code --configuration-cache-problems=warn} rather than
 * {@code STABLE_CONFIGURATION_CACHE}: convention mapping (the default wiring) produces a
 * {@code RUNTIME_DEFAULT_VALUE} advisory that strict mode would promote to a build error.</p>
 *
 * <p><b>Regression coverage note:</b> the second run (CC reuse, identical inputs) is insufficient
 * on its own — the task is UP-TO-DATE so {@code gosuClasspath} being empty would go undetected.
 * The third run adds a new source file so the task re-executes under CC reuse; if
 * {@code gosuClasspath} was serialised empty the gosudoc tool cannot find {@code gosu-core-api}
 * and the build fails.</p>
 */
@Unroll
class ConfigurationCacheGosudocTest extends AbstractGosuPluginSpecification {

    File srcMainGosu

    @Override
    def setup() {
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
    }

    def 'gosudoc is configuration-cache compatible [Gradle #gradleVersion]'() {
        given:
        buildScript << getBasicBuildScriptForTesting()

        and: 'a minimal Gosu source file so gosudoc has work to do'
        File pogo = new File(srcMainGosu, asPath('example', 'gradle', 'SimplePogo.gs'))
        pogo.getParentFile().mkdirs()
        pogo << 'package example.gradle\n\nclass SimplePogo {}'

        when: 'the configuration cache entry is stored'
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('gosudoc', '--configuration-cache', '--configuration-cache-problems=warn')
                .withGradleVersion(gradleVersion)
        BuildResult storeResult = runner.build()

        then:
        storeResult.task(':gosudoc').outcome == SUCCESS
        storeResult.output.contains('Configuration cache entry stored.')
        !storeResult.output.contains("property 'gosuClasspath' doesn't have a configured value")

        when: 'the build runs again with identical inputs'
        BuildResult reuseResult = runner.build()

        then: 'the configuration cache entry is reused'
        reuseResult.output.contains('Reusing configuration cache.')
        reuseResult.output.contains('Configuration cache entry reused.')

        when: 'a new source file is added, forcing gosudoc to re-execute under CC reuse'
        File pogo2 = new File(srcMainGosu, asPath('example', 'gradle', 'AnotherPogo.gs'))
        pogo2.getParentFile().mkdirs()
        pogo2 << 'package example.gradle\n\nclass AnotherPogo {}'
        BuildResult rerunResult = runner.build()

        then: 'CC is still reused and the task executes successfully — gosuClasspath was correctly stored in the CC entry'
        rerunResult.output.contains('Reusing configuration cache.')
        rerunResult.task(':gosudoc').outcome == SUCCESS
        !rerunResult.output.contains("property 'gosuClasspath' doesn't have a configured value")

        where:
        gradleVersion << gradleVersionsToTest
    }
}
