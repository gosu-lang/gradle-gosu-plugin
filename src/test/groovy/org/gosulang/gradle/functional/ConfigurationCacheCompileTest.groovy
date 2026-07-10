package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Guards the configuration-cache compatibility of the {@code compileGosu} task (issue #68).
 *
 * <p>{@code GosuBasePlugin.configureCompileDefaults()} wires {@code gosuClasspath} via convention
 * mapping. On a CC-store build Gradle's {@code BeanPropertyWriter} evaluates the convention value
 * (calling {@code GosuRuntime.inferGosuClasspath()}) and serialises the resulting file collection;
 * on CC reuse the stored file list is restored directly, bypassing convention mapping entirely.</p>
 *
 * <p>For this to work the backing field in {@code GosuCompile} must be named {@code gosuClasspath}
 * (no underscore prefix). {@code BeanPropertyWriter} looks up convention mappings by the field
 * name; an underscore-prefixed name produces a key mismatch, so the convention is never found,
 * {@code null} is stored, and {@code gosuClasspath} is empty on every CC-reuse run.</p>
 *
 * <p>PR #93 regressed this by replacing convention mapping with
 * {@code ConfigurableFileCollection#from(Callable)} directly on the task property. CC then
 * attempted to serialise the lambda at store time; when that failed the property was left
 * unconfigured, causing a {@code WorkValidationException} ("property 'gosuClasspath' doesn't
 * have a configured value") before the task action ran.</p>
 *
 * <p>We run {@code compileGosu} (not {@code --dry-run}) because {@code --dry-run} skips task
 * execution and therefore never triggers task-property validation.</p>
 *
 * <p>We use {@code --configuration-cache-problems=warn} rather than
 * {@code STABLE_CONFIGURATION_CACHE}: convention mapping produces a {@code RUNTIME_DEFAULT_VALUE}
 * advisory that strict mode would promote to a build error.</p>
 *
 * <p><b>Regression coverage note:</b> the second run (CC reuse, identical inputs) is insufficient
 * on its own — the task is UP-TO-DATE so {@code gosuClasspath} being empty would go undetected.
 * The third run adds a new source file so the task re-executes under CC reuse; if
 * {@code gosuClasspath} was serialised empty the compiler cannot find {@code gosu-core-api}
 * and the build fails.</p>
 */
@Unroll
class ConfigurationCacheCompileTest extends AbstractGosuPluginSpecification {

    File srcMainGosu

    @Override
    def setup() {
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
    }

    def 'compileGosu is configuration-cache compatible — gosuClasspath is wired [Gradle #gradleVersion]'() {
        given:
        buildScript << getBasicBuildScriptForTesting()

        and: 'a minimal Gosu source file so compileGosu has work to do'
        File pogo = new File(srcMainGosu, asPath('example', 'gradle', 'SimplePogo.gs'))
        pogo.getParentFile().mkdirs()
        pogo << 'package example.gradle\n\nclass SimplePogo {}'

        when: 'the configuration cache entry is stored'
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('compileGosu', '--configuration-cache', '--configuration-cache-problems=warn')
                .withGradleVersion(gradleVersion)
        BuildResult storeResult = runner.build()

        then: 'compileGosu succeeds — gosuClasspath is wired via convention mapping so no WorkValidationException is thrown'
        storeResult.task(':compileGosu').outcome == SUCCESS
        storeResult.output.contains('Configuration cache entry stored.')
        !storeResult.output.contains("property 'gosuClasspath' doesn't have a configured value")

        when: 'the build runs again with identical inputs'
        BuildResult reuseResult = runner.build()

        then: 'the configuration cache entry is reused'
        reuseResult.output.contains('Reusing configuration cache.')
        reuseResult.output.contains('Configuration cache entry reused.')

        when: 'a new source file is added, forcing compileGosu to re-execute under CC reuse'
        File pogo2 = new File(srcMainGosu, asPath('example', 'gradle', 'AnotherPogo.gs'))
        pogo2.getParentFile().mkdirs()
        pogo2 << 'package example.gradle\n\nclass AnotherPogo {}'
        BuildResult rerunResult = runner.build()

        then: 'CC is still reused and the task executes successfully — gosuClasspath was correctly stored in the CC entry'
        rerunResult.output.contains('Reusing configuration cache.')
        rerunResult.task(':compileGosu').outcome == SUCCESS
        !rerunResult.output.contains("property 'gosuClasspath' doesn't have a configured value")

        where:
        gradleVersion << gradleVersionsToTest
    }
}
