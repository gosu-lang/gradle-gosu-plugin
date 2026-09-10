package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.NO_SOURCE
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Cleanup when no Gosu source is left -- the one path on which no plugin code runs at all.
 *
 * <p>{@code getStableSources()} carries {@code @SkipWhenEmpty}, so once the last source goes the
 * task is never executed: Gradle removes the previous outputs itself, and neither the flag-off
 * sweep nor the incremental prune is reached. Gradle removes the output root here too, unlike
 * either of ours, and is entitled to -- the task is not running, so nothing needs somewhere to
 * write.
 *
 * <p>Siblings: {@link NonIncrementalStaleOutputTest} and {@link IncrementalStaleOutputTest}, which
 * cover the paths where the task does run.
 */
@Unroll
class SourcelessCompileCleanupTest extends AbstractGosuPluginSpecification {

    File srcMainGosu

    /**
     * super#setup is invoked automatically
     * @return
     */
    def setup() {
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
    }

    /**
     * The simplest case: no sources and no previous outputs, which short-circuits without touching
     * the disk. Worth pinning separately from the delete-everything case below, because it is the
     * state every consumer starts in -- applying the plugin to a project with no Gosu sources must
     * be a no-op, not a failure and not an empty output directory.
     */
    def 'compileGosu with no sources at all is NO-SOURCE [Gradle #gradleVersion]'() {
        given: 'the plugin is applied, but src/main/gosu is empty'
        buildScript << getBasicBuildScriptForTesting()
        assert srcMainGosu.isDirectory()
        assert srcMainGosu.list().length == 0

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('clean', 'compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()
        String buildOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')

        then: 'The task is short-circuited rather than run, and nothing is created'
        result.task(':compileGosu').outcome == NO_SOURCE
        !new File(buildOutput).exists()

        when: 'The build runs again'
        runner.withArguments('compileGosu', '-i')
        result = runner.build()

        then: 'Still NO-SOURCE - there is nothing to become up to date about'
        result.task(':compileGosu').outcome == NO_SOURCE

        where:
        gradleVersion << gradleVersionsToTest
    }

    /**
     * Both phases are asserted: the first proves the outputs go, the second proves the task then
     * settles instead of re-cleaning forever.
     *
     * <p>The removing build reports {@code SUCCESS}, not {@code NO-SOURCE} -- cleaning previous
     * outputs counts as work, and only once there is nothing left to clean does the task
     * short-circuit. Run in both modalities because the incremental one declares the gosuc
     * dependency file as an extra output, which must go the same way; leaving it would seed the
     * next build's graph from a compilation whose classes no longer exist.
     *
     * <p>The source tree deliberately keeps an empty {@code com/example} afterwards:
     * {@code @IgnoreEmptyDirectories} is what stops that counting as surviving source.
     */
    def 'Deleting every source cleans the outputs, then settles to NO-SOURCE [#modality, Gradle #gradleVersion]'() {
        given:
        buildScript << (incremental ? getIncrementalBuildScriptForTesting() : getBasicBuildScriptForTesting())

        File packageDir = new File(srcMainGosu, 'com/example')
        assert packageDir.mkdirs()

        File rootType = new File(srcMainGosu, 'RootType.gs')
        File nested = new File(packageDir, 'Nested.gs')

        rootType << """
            class RootType {
                static function value() : int {
                    return 1
                }
            }
            """

        nested << """
            package com.example

            class Nested {
                static function value() : int {
                    return 2
                }
            }
            """

        when: 'Initial compilation'
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('clean', 'compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()
        String buildOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')
        File dependencyFile = new File(testProjectDir.root, 'build/tmp/gosuc-deps-compileGosu.json')

        then: 'A populated output tree, plus the dep file when gosuc is running incrementally'
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'RootType.class').exists()
        new File(buildOutput, 'com/example/Nested.class').exists()
        dependencyFile.exists() == incremental

        when: 'Every Gosu source is deleted, leaving an empty package directory in the source tree'
        assert nested.delete()
        assert rootType.delete()
        assert packageDir.isDirectory()

        runner.withArguments('compileGosu', '-i')
        result = runner.build()

        then: 'The task is skipped, but the build reports SUCCESS because cleaning is work'
        result.task(':compileGosu').outcome == SUCCESS

        and: 'Every previous output goes - classes, the sources gosuc copied, directories, the root'
        !new File(buildOutput, 'RootType.class').exists()
        !new File(buildOutput, 'RootType.gs').exists()
        !new File(buildOutput, 'com').exists()
        !new File(buildOutput).exists()

        and: 'And the dependency file, so no stale graph survives into the next build'
        !dependencyFile.exists()

        when: 'The build runs again with still no sources'
        result = runner.build()

        then: 'Nothing is left to clean, so now it really is NO-SOURCE'
        result.task(':compileGosu').outcome == NO_SOURCE
        !new File(buildOutput).exists()

        where:
        [modality, incremental, gradleVersion] << [
                ['full', false],
                ['incremental', true]
        ].collectMany { pair -> gradleVersionsToTest.collect { v -> pair + [v] } }
    }
}
