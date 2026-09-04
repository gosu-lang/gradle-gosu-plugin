package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.NO_SOURCE
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

/**
 * Pins the stale-output cleanup performed by {@code GosuCompile} on a full (non-gosuc-incremental)
 * compile.
 *
 * <p>gosuc only ever writes to the destination directory, and Gradle removes a task's previous
 * outputs only when it cannot describe the change per-file.  Deleting a source file is a change
 * Gradle describes perfectly well, so before the fix for
 * <a href="https://github.com/gosu-lang/gradle-gosu-plugin/issues/105">#105</a> neither party
 * cleaned up and the deleted type's {@code .class} (plus the {@code .gs} gosuc copies beside it)
 * survived every subsequent build.
 */
@Unroll
class StaleOutputCleanupTest extends AbstractGosuPluginSpecification {

    File srcMainGosu, A, B

    def setup() {
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
        A = new File(srcMainGosu, 'A.gs')
        B = new File(srcMainGosu, 'B.gs')
    }

    private static String classA = """
             class A {
               static function a() : String { return "a" }
             }
             """

    private static String classB = """
             class B {
               static function b() : String { return "b" }
             }
             """

    def 'deleting a source removes its stale output on a full recompile [Gradle #gradleVersion]'() {
        given:
        buildScript << getBasicBuildScriptForTesting()
        A << classA
        B << classB

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()
        String buildOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')

        then: 'both types are compiled, and gosuc copies both sources alongside them'
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'A.class').exists()
        new File(buildOutput, 'B.class').exists()
        new File(buildOutput, 'B.gs').exists()

        when: 'B is deleted from the source tree and the project is rebuilt'
        assert B.delete()
        result = runner.build()

        then: 'the task re-runs -- this is the incremental execution Gradle does not clean for'
        result.task(':compileGosu').outcome == SUCCESS

        and: 'B leaves nothing behind'
        !new File(buildOutput, 'B.class').exists()
        !new File(buildOutput, 'B.gs').exists()

        and: 'A is untouched'
        new File(buildOutput, 'A.class').exists()
        new File(buildOutput, 'A.gs').exists()

        when: 'nothing else changes'
        result = runner.build()

        then: 'cleaning did not leave the task perpetually out of date'
        result.task(':compileGosu').outcome == UP_TO_DATE

        where:
        gradleVersion << gradleVersionsToTest
    }

    /**
     * Runs in both modalities because the two reach the sweeper by different routes: a full compile
     * prunes from {@code cleanStaleOutputs()} before gosuc runs, while an incremental one prunes
     * after gosuc has deleted the removed type, driven off the {@code -removed-types} FQCNs.  gosuc
     * removes the class and its copied source either way but leaves the package directory standing,
     * so without the incremental wiring this husk survives every subsequent build.
     */
    def 'emptying a package prunes its output directory [#modality, Gradle #gradleVersion]'() {
        given:
        buildScript << (incremental ? getIncrementalBuildScriptForTesting() : getBasicBuildScriptForTesting())
        A << classA

        File pkg = new File(srcMainGosu, 'com/example')
        pkg.mkdirs()
        File doomed = new File(pkg, 'Doomed.gs')
        doomed << """
                  package com.example

                  class Doomed {
                    static function d() : String { return "d" }
                  }
                  """

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()
        String buildOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')

        then:
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'com/example/Doomed.class').exists()

        when: 'the package\'s only type is deleted'
        assert doomed.delete()
        result = runner.build()

        then: 'the now-empty package directories are pruned, not left as husks'
        result.task(':compileGosu').outcome == SUCCESS
        !new File(buildOutput, 'com/example').exists()
        !new File(buildOutput, 'com').exists()

        and: 'the destination directory itself survives, along with the surviving type'
        new File(buildOutput).isDirectory()
        new File(buildOutput, 'A.class').exists()

        where:
        [modality, incremental, gradleVersion] << [
                ['full', false],
                ['incremental', true]
        ].collectMany { pair -> gradleVersionsToTest.collect { v -> pair + [v] } }
    }

    def 'the output root survives when every previous output leaves the default package [Gradle #gradleVersion]'() {
        given: 'a lone type in the default package -- its outputs are all the output directory holds'
        buildScript << getBasicBuildScriptForTesting()
        A << classA

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()
        String buildOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')

        then:
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'A.class').exists()

        when: 'A moves into a package, so the sweep empties the output root completely'
        assert A.delete()
        File pkg = new File(srcMainGosu, 'com/example')
        pkg.mkdirs()
        new File(pkg, 'A.gs') << """
             package com.example

             class A {
               static function a() : String { return "a" }
             }
             """

        result = runner.build()

        then: 'the build still succeeds -- the root is a declared @OutputDirectory, not a casualty'
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput).isDirectory()

        and: 'the type is compiled under its new package, and nothing is left at the root'
        new File(buildOutput, 'com/example/A.class').exists()
        !new File(buildOutput, 'A.class').exists()
        !new File(buildOutput, 'A.gs').exists()

        where:
        gradleVersion << gradleVersionsToTest
    }

    /**
     * The simplest {@code NO-SOURCE} case: the plugin is applied but {@code src/main/gosu} holds
     * nothing, so the task has never produced anything.
     *
     * <p>This is the first branch of {@code SkipEmptyMutableWorkStep#performSkip} -- no source
     * files <em>and</em> no previous output files -- which short-circuits without touching the
     * disk.  Worth pinning separately from the delete-everything case below, because it is the
     * state every consumer of this plugin starts in: applying the plugin to a project with no
     * Gosu sources at all must be a no-op, not a failure and not an empty output directory.
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
                .withArguments('compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()
        String buildOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')

        then: 'the task is short-circuited rather than run, and nothing is created'
        result.task(':compileGosu').outcome == NO_SOURCE
        !new File(buildOutput).exists()

        when: 'the build runs again'
        result = runner.build()

        then: 'still NO-SOURCE -- there is nothing to become up to date about'
        result.task(':compileGosu').outcome == NO_SOURCE

        where:
        gradleVersion << gradleVersionsToTest
    }

    /**
     * The one cleanup path on which no plugin code runs at all.
     *
     * <p>{@code getStableSources()} carries {@code @SkipWhenEmpty}, so when the last Gosu source
     * goes the task is never executed: Gradle removes the previous outputs itself and neither
     * {@code cleanStaleOutputs()} nor the incremental prune is reached.
     *
     * <p>The outcome is {@code SUCCESS}, not {@code NO-SOURCE}, on the build that does the
     * removing.  {@code SkipEmptyMutableWorkStep#performSkip} reports
     * {@code EXECUTED_NON_INCREMENTALLY} when there were previous outputs and cleaning them did
     * work, and {@code SHORT_CIRCUITED} -- which surfaces as {@code NO-SOURCE} -- only once there
     * is nothing left to clean.  Both phases are asserted: the first proves the outputs go, the
     * second proves the task then settles instead of re-cleaning forever.
     *
     * <p>Note Gradle removes the output root itself here, unlike this plugin's own sweeper, which
     * always preserves it.  Gradle is entitled to: the task is not running, so nothing needs a
     * directory to write into.
     *
     * <p>Run in both modalities because the incremental one declares an extra output, the gosuc
     * dependency file, which must go the same way; leaving it behind would seed the next build's
     * graph from a compilation whose classes no longer exist.
     *
     * <p>The source tree deliberately keeps an empty {@code com/example} directory afterwards:
     * {@code @IgnoreEmptyDirectories} is what stops that counting as surviving source.
     */
    def 'deleting every source cleans the outputs, then settles to NO-SOURCE [#modality, Gradle #gradleVersion]'() {
        given:
        buildScript << (incremental ? getIncrementalBuildScriptForTesting() : getBasicBuildScriptForTesting())
        A << classA
        B << classB

        File pkg = new File(srcMainGosu, 'com/example')
        pkg.mkdirs()
        File nested = new File(pkg, 'Nested.gs')
        nested << """
                 package com.example

                 class Nested {
                   static function n() : String { return "n" }
                 }
                 """

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()
        String buildOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')
        File dependencyFile = new File(testProjectDir.root, 'build/tmp/gosuc-deps-compileGosu.json')

        then: 'a populated output tree, plus the dep file when gosuc is running incrementally'
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'A.class').exists()
        new File(buildOutput, 'com/example/Nested.class').exists()
        dependencyFile.exists() == incremental

        when: 'every Gosu source is deleted, leaving an empty package directory in the source tree'
        assert nested.delete()
        assert A.delete()
        assert B.delete()
        assert pkg.isDirectory()

        result = runner.build()

        then: 'the task is skipped, but the build reports SUCCESS because cleaning is work'
        result.task(':compileGosu').outcome == SUCCESS

        and: 'every previous output goes -- classes, copied sources, package directories, the root'
        !new File(buildOutput, 'A.class').exists()
        !new File(buildOutput, 'A.gs').exists()
        !new File(buildOutput, 'B.class').exists()
        !new File(buildOutput, 'com').exists()
        !new File(buildOutput).exists()

        and: 'and the dependency file, so no stale graph survives into the next build'
        !dependencyFile.exists()

        when: 'the build runs again with still no sources'
        result = runner.build()

        then: 'nothing is left to clean, so now it really is NO-SOURCE'
        result.task(':compileGosu').outcome == NO_SOURCE
        !new File(buildOutput).exists()

        where:
        [modality, incremental, gradleVersion] << [
                ['full', false],
                ['incremental', true]
        ].collectMany { pair -> gradleVersionsToTest.collect { v -> pair + [v] } }
    }

    def 'stale output cleanup is configuration cache compatible [Gradle #gradleVersion]'() {
        given:
        buildScript << getBasicBuildScriptForTesting()
        A << classA
        B << classB

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('compileGosu', '--configuration-cache', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()
        String buildOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')

        then: 'the configuration cache entry is stored'
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'B.class').exists()

        when: 'B is deleted and the build re-runs against the reused entry'
        assert B.delete()
        result = runner.build()

        then:
        result.output.contains('Reusing configuration cache')
        result.task(':compileGosu').outcome == SUCCESS
        !new File(buildOutput, 'B.class').exists()
        !new File(buildOutput, 'B.gs').exists()
        new File(buildOutput, 'A.class').exists()

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'deleting a source removes its stale output in incremental mode too [Gradle #gradleVersion]'() {
        given: 'gosuOptions.incrementalCompilation = true, where gosuc is told about removals'
        buildScript << getIncrementalBuildScriptForTesting()
        A << classA
        B << classB

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()
        String buildOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')

        then:
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'A.class').exists()
        new File(buildOutput, 'B.class').exists()

        when:
        assert B.delete()
        result = runner.build()

        then:
        result.task(':compileGosu').outcome == SUCCESS
        result.output.contains('Gosu incremental compilation started')
        !new File(buildOutput, 'B.class').exists()
        !new File(buildOutput, 'B.gs').exists()
        new File(buildOutput, 'A.class').exists()

        where:
        gradleVersion << gradleVersionsToTest
    }
}
