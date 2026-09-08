package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Stale-output removal when {@code gosuOptions.incrementalCompilation} is off.
 *
 * <p>Neither gosuc nor Gradle prunes here on its own. gosuc gets the full source list and no change
 * set, so a class file whose source is gone is invisible to it. Gradle skips its own cleanup
 * because {@code @SkipWhenEmpty} on {@code GosuCompile.getStableSources()} keeps execution
 * incremental in its eyes, and {@code RemovePreviousOutputsStep} only empties output directories
 * when it is not.
 *
 * <p>{@code GosuCompile} closes that gap by emptying the destination itself, matching what
 * {@code CleaningJavaCompiler} does for {@code JavaCompile}: the stale class files go, and so do the
 * package directories they leave empty. Both methods below go red without that cleanup -- one for a
 * deleted source, one for a source moved to another package.
 *
 * <p>Counterpart to the deletion case in {@link IncrementalCompilationWithDependencyTrackingTest},
 * which covers the flag-on path: there gosuc removes the stale class off {@code -removed-types}.
 */
@Unroll
class NonIncrementalStaleOutputTest extends AbstractGosuPluginSpecification {
    private static final long SLEEP_MS = 200

    File srcMainGosu
    File dependencyFile

    /**
     * super#setup is invoked automatically
     * @return
     */
    def setup() {
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
        dependencyFile = new File(testProjectDir.root, 'build/tmp/gosuc-deps-compileGosu.json')
    }

    def 'The plugin removes a stale class file on a non-incremental recompile [Gradle #gradleVersion]'() {
        given: 'the basic build script - gosuOptions.incrementalCompilation is off by default'
        buildScript << getBasicBuildScriptForTesting()

        // ClassA and ClassB are independent, so nothing about recompiling ClassA can account for
        // ClassB.class disappearing. ClassB is packaged so the cleanup has to reach into a
        // subdirectory and prune it once empty, not merely clear the destination root.
        File packageDir = new File(srcMainGosu, 'com/example')
        assert packageDir.mkdirs()

        File classA = new File(srcMainGosu, 'ClassA.gs')
        File classB = new File(packageDir, 'ClassB.gs')

        classA << """
            class ClassA {
                static function value() : int {
                    return 1
                }
            }
            """

        classB << """
            package com.example

            class ClassB {
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

        then: 'Both classes are compiled, the packaged one into its own directory'
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'ClassA.class').exists()
        new File(buildOutput, 'com/example/ClassB.class').exists()

        and: 'No dep file is written - gosuc is never passed -dependency-file on this path'
        !dependencyFile.exists()

        when: 'ClassB.gs is deleted and the build re-runs'
        long classATime = new File(buildOutput, 'ClassA.class').lastModified()
        Thread.sleep(SLEEP_MS) // ensure an observable mtime difference on coarse filesystems

        assert classB.delete()

        runner.withArguments('compileGosu', '-i')
        result = runner.build()

        then: 'The task re-executes and recompiles the surviving source'
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'ClassA.class').exists()
        new File(buildOutput, 'ClassA.class').lastModified() > classATime

        and: 'ClassB.class is gone, and so is the package directory it left empty'
        !new File(buildOutput, 'com/example/ClassB.class').exists()
        !new File(buildOutput, 'com/example').exists()

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'The plugin removes the class file a moved source leaves behind [Gradle #gradleVersion]'() {
        given: 'the basic build script - gosuOptions.incrementalCompilation is off by default'
        buildScript << getBasicBuildScriptForTesting()

        // A move leaves com.example.Movable behind while creating com.other.Movable: a stale class
        // sharing its simple name with a live one, which resolves ambiguously rather than simply
        // going missing. A plain deletion cannot produce that shape.
        File fromDir = new File(srcMainGosu, 'com/example')
        assert fromDir.mkdirs()

        File movable = new File(fromDir, 'Movable.gs')

        movable << """
            package com.example

            class Movable {
                static function value() : int {
                    return 1
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

        then: 'The class lands in its package directory'
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'com/example/Movable.class').exists()

        when: 'The source moves to com.other and the build re-runs'
        assert movable.delete()

        File toDir = new File(srcMainGosu, 'com/other')
        assert toDir.mkdirs()

        // Same type, same body, new package - only the FQCN changes.
        new File(toDir, 'Movable.gs') << """
            package com.other

            class Movable {
                static function value() : int {
                    return 1
                }
            }
            """

        runner.withArguments('compileGosu', '-i')
        result = runner.build()

        then: 'The task re-executes and compiles the type at its new FQCN'
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'com/other/Movable.class').exists()

        and: 'Nothing is left behind at the old one, nor the directory that held it'
        !new File(buildOutput, 'com/example/Movable.class').exists()
        !new File(buildOutput, 'com/example').exists()

        and: 'The shared parent package survives - pruning stops where output remains'
        new File(buildOutput, 'com').exists()

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'The output root survives when every previous output leaves the default package [Gradle #gradleVersion]'() {
        given: 'a lone type in the default package - its outputs are all the output directory holds'
        buildScript << getBasicBuildScriptForTesting()

        File loner = new File(srcMainGosu, 'Loner.gs')
        loner << """
            class Loner {
                static function value() : int {
                    return 1
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

        then: 'The class lands at the root'
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'Loner.class').exists()

        when: 'It moves into a package, so the sweep empties the output root completely'
        assert loner.delete()

        File packageDir = new File(srcMainGosu, 'com/example')
        assert packageDir.mkdirs()
        new File(packageDir, 'Loner.gs') << """
            package com.example

            class Loner {
                static function value() : int {
                    return 1
                }
            }
            """

        runner.withArguments('compileGosu', '-i')
        result = runner.build()

        then: 'The build still succeeds - the root is a declared @OutputDirectory, not a casualty'
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput).isDirectory()

        and: 'The type is compiled under its new package, and nothing is left at the root'
        new File(buildOutput, 'com/example/Loner.class').exists()
        !new File(buildOutput, 'Loner.class').exists()
        !new File(buildOutput, 'Loner.gs').exists()

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'Stale output cleanup is configuration cache compatible [Gradle #gradleVersion]'() {
        given: 'the basic build script - gosuOptions.incrementalCompilation is off by default'
        buildScript << getBasicBuildScriptForTesting()

        File keeper = new File(srcMainGosu, 'Keeper.gs')
        File doomed = new File(srcMainGosu, 'Doomed.gs')

        keeper << """
            class Keeper {
                static function value() : int {
                    return 1
                }
            }
            """

        doomed << """
            class Doomed {
                static function value() : int {
                    return 2
                }
            }
            """

        // No 'clean' here, unlike the other methods: the requested task names are part of the
        // configuration cache key, so both builds have to ask for the same ones or the second
        // recalculates the task graph instead of reusing the entry. The project directory is fresh
        // per iteration, so there is nothing to clean anyway.
        when: 'Initial compilation stores a configuration cache entry'
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('compileGosu', '--configuration-cache', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()
        String buildOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')

        then: 'Both classes are compiled'
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'Doomed.class').exists()

        when: 'A source is deleted and the build re-runs against the reused entry'
        assert doomed.delete()

        runner.withArguments('compileGosu', '--configuration-cache', '-i')
        result = runner.build()

        // The cleanup reaches FileSystemOperations and ObjectFactory through injected services
        // rather than getProject(), so it survives serialisation of the task graph.
        then: 'The entry is reused, and the cleanup still runs'
        result.output.contains('Reusing configuration cache')
        result.task(':compileGosu').outcome == SUCCESS
        !new File(buildOutput, 'Doomed.class').exists()
        !new File(buildOutput, 'Doomed.gs').exists()
        new File(buildOutput, 'Keeper.class').exists()

        where:
        gradleVersion << gradleVersionsToTest
    }
}
