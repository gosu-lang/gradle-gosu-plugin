package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Stale-output removal when {@code gosuOptions.incrementalCompilation} is on -- the sibling of
 * {@link NonIncrementalStaleOutputTest}, which covers the flag-off path.
 *
 * <p>Here gosuc does most of the work: a deleted source reaches it as {@code -removed-types} and it
 * deletes the class file itself, so {@code GosuCompile} deliberately stands aside. What gosuc does
 * not do is remove the package directory it just emptied, which would leave an incremental build
 * and a full build producing different output trees from the same sources -- and put an empty
 * directory entry in the jar, since {@code Jar} inherits {@code includeEmptyDirs = true} from
 * {@code AbstractCopyTask}. {@code GosuCompile.pruneEmptyPackageDirs} closes that, after the
 * compiler has run.
 */
@Unroll
class IncrementalStaleOutputTest extends AbstractGosuPluginSpecification {

    File srcMainGosu

    /**
     * super#setup is invoked automatically
     * @return
     */
    def setup() {
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
    }

    def 'Emptying a package prunes its output directory [Gradle #gradleVersion]'() {
        given: 'the incremental build script - gosuOptions.incrementalCompilation is on'
        buildScript << getIncrementalBuildScriptForTesting()

        // Doomed is the only type in com.example, and com holds nothing else, so both directories
        // are emptied by its removal and the walk upwards has to take them both.
        File packageDir = new File(srcMainGosu, 'com/example')
        assert packageDir.mkdirs()

        File doomed = new File(packageDir, 'Doomed.gs')
        File survivor = new File(srcMainGosu, 'Survivor.gs')

        doomed << """
            package com.example

            class Doomed {
                static function value() : int {
                    return 1
                }
            }
            """

        survivor << """
            class Survivor {
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
        new File(buildOutput, 'com/example/Doomed.class').exists()
        new File(buildOutput, 'Survivor.class').exists()

        when: 'The only type in the package is deleted and the build re-runs'
        assert doomed.delete()

        runner.withArguments('compileGosu', '-i')
        result = runner.build()

        then: 'gosuc removes the class file off -removed-types'
        result.task(':compileGosu').outcome == SUCCESS
        !new File(buildOutput, 'com/example/Doomed.class').exists()

        and: 'And the directories it emptied are pruned, parent included, not left as husks'
        !new File(buildOutput, 'com/example').exists()
        !new File(buildOutput, 'com').exists()

        and: 'The destination root survives, and so does the untouched type'
        new File(buildOutput).isDirectory()
        new File(buildOutput, 'Survivor.class').exists()

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'A package that still holds a type is left alone [Gradle #gradleVersion]'() {
        given: 'the incremental build script - gosuOptions.incrementalCompilation is on'
        buildScript << getIncrementalBuildScriptForTesting()

        // Two types in one package: removing one empties nothing, so the walk must stop at once.
        File packageDir = new File(srcMainGosu, 'com/example')
        assert packageDir.mkdirs()

        File doomed = new File(packageDir, 'Doomed.gs')
        File neighbour = new File(packageDir, 'Neighbour.gs')

        doomed << """
            package com.example

            class Doomed {
                static function value() : int {
                    return 1
                }
            }
            """

        neighbour << """
            package com.example

            class Neighbour {
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

        then: 'Both classes land in the package directory'
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'com/example/Doomed.class').exists()
        new File(buildOutput, 'com/example/Neighbour.class').exists()

        when: 'One of the two is deleted and the build re-runs'
        assert doomed.delete()

        runner.withArguments('compileGosu', '-i')
        result = runner.build()

        then: 'Its class file goes'
        result.task(':compileGosu').outcome == SUCCESS
        !new File(buildOutput, 'com/example/Doomed.class').exists()

        and: 'But the package directory stays, because the neighbour is still in it'
        new File(buildOutput, 'com/example').exists()
        new File(buildOutput, 'com/example/Neighbour.class').exists()

        where:
        gradleVersion << gradleVersionsToTest
    }
}
