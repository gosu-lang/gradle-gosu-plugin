package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * The silent no-op full rebuild: Gradle asks for a full recompilation, the dep file survives into
 * the task action anyway, and gosuc -- whose only "compile everything" signal is that file's
 * absence -- recompiles nothing and reports success.
 *
 * <p>Gradle leaves the file behind whenever the dep file on disk does not match what the previous
 * execution recorded for the property: {@code RemovePreviousOutputsStep} then reads it as possibly
 * another task's and takes its {@code cleanupOverlappingOutputs} branch, which deletes only what
 * that previous execution recorded -- nothing at all when there was no such execution.  Both
 * feature methods below reach that state; {@code GosuCompile.compile} deleting the dep file itself
 * is what keeps them passing.  (Gradle names the file in a "does not know how file ... was created"
 * caching-disabled reason, but only when the build cache or a build scan is active -- see
 * {@code ResolveCachingStateStep} -- so these tests assert the outcome instead.)
 */
@Unroll
class FullRebuildStaleDepFileTest extends AbstractGosuPluginSpecification {

    File srcMainGosu
    File dependencyFile

    /**
     * super#setup is invoked automatically
     */
    def setup() {
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
        dependencyFile = new File(testProjectDir.root, 'build/tmp/gosuc-deps-compileGosu.json')
    }

    def 'A full rebuild recompiles every source when the task history is gone [Gradle #gradleVersion]'() {
        given: 'the incremental build script - gosuOptions.incrementalCompilation is on'
        buildScript << getIncrementalBuildScriptForTesting()

        File classA = new File(srcMainGosu, 'ClassA.gs')
        File classB = new File(srcMainGosu, 'ClassB.gs')

        classA << """
            class ClassA {
                static function value() : int {
                    return 1
                }
            }
            """

        classB << """
            class ClassB {
                static function consume() : int {
                    return ClassA.value() + 10
                }
            }
            """

        when: 'Initial incremental compilation writes the dep file'
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('clean', 'compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()
        String buildOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')

        then:
        result.task(':compileGosu').outcome == SUCCESS
        dependencyFile.exists()
        new File(buildOutput, 'ClassA.class').exists()
        new File(buildOutput, 'ClassB.class').exists()

        when: 'A third type is added, then the execution history is dropped and build/ left intact'
        File classC = new File(srcMainGosu, 'ClassC.gs')
        classC << """
            class ClassC {
                static function consume() : int {
                    return ClassA.value() + 20
                }
            }
            """

        List<File> historyDirs = []
        new File(testProjectDir.root, '.gradle').eachDirRecurse {
            if (it.name == 'executionHistory') {
                historyDirs << it
            }
        }
        assert !historyDirs.isEmpty() : 'no executionHistory cache found - the layout this test simulates has moved'
        historyDirs.each { assert it.deleteDir() }
        assert dependencyFile.exists() : 'precondition: the dep file outlives the history, as it does after a Gradle upgrade'

        runner.withArguments('compileGosu', '-i')
        result = runner.build()

        then: 'Gradle has no history for the task and asks for a full rebuild'
        result.task(':compileGosu').outcome == SUCCESS
        result.output.contains('No history is available')
        result.output.contains('Gosu full recompilation is required')

        and: 'GosuCompile deletes the leftover dep file, so gosuc compiles every source'
        result.output.contains('No existing dependency file found at')
        result.output.contains('compiling all 3 source files')
        new File(buildOutput, 'ClassA.class').exists()
        new File(buildOutput, 'ClassB.class').exists()
        new File(buildOutput, 'ClassC.class').exists()

        where:
        gradleVersion << gradleVersionsToTest
    }

    /**
     * The same end state reached by toggling {@code gosuOptions.incrementalCompilation} off and back on.
     */
    def 'A full rebuild recompiles every source when the dep file outlives an incremental-off build [Gradle #gradleVersion]'() {
        given: 'the incremental build script, with the flag switchable from the command line'
        buildScript << getIncrementalBuildScriptForTesting()
        buildScript << """
            compileGosu {
                gosuOptions.incrementalCompilation = !project.hasProperty('gosuIncrementalOff')
            }
            """

        File classA = new File(srcMainGosu, 'ClassA.gs')
        File classB = new File(srcMainGosu, 'ClassB.gs')

        classA << """
            class ClassA {
                static function value() : int {
                    return 1
                }
            }
            """

        classB << """
            class ClassB {
                static function consume() : int {
                    return ClassA.value() + 10
                }
            }
            """

        when: 'Initial incremental compilation writes the dep file'
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('clean', 'compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()
        String buildOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')

        then:
        result.task(':compileGosu').outcome == SUCCESS
        dependencyFile.exists()
        new File(buildOutput, 'ClassA.class').exists()
        new File(buildOutput, 'ClassB.class').exists()

        when: 'The flag goes off - gosuc is handed no -dependency-file, so nothing deletes the file'
        runner.withArguments('compileGosu', '-i', '-PgosuIncrementalOff')
        result = runner.build()

        then: 'Everything compiles, and the now-undeclared dep file is still on disk'
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'ClassA.class').exists()
        new File(buildOutput, 'ClassB.class').exists()
        dependencyFile.exists()

        when: 'A third type is added and the flag goes back on'
        File classC = new File(srcMainGosu, 'ClassC.gs')
        classC << """
            class ClassC {
                static function consume() : int {
                    return ClassA.value() + 20
                }
            }
            """

        runner.withArguments('compileGosu', '-i')
        result = runner.build()

        then: 'Gradle asks for a full rebuild, the flag having changed'
        result.task(':compileGosu').outcome == SUCCESS
        result.output.contains('Gosu full recompilation is required')

        and: 'GosuCompile deletes the leftover dep file, so gosuc compiles every source'
        result.output.contains('No existing dependency file found at')
        result.output.contains('compiling all 3 source files')
        new File(buildOutput, 'ClassA.class').exists()
        new File(buildOutput, 'ClassB.class').exists()
        new File(buildOutput, 'ClassC.class').exists()

        where:
        gradleVersion << gradleVersionsToTest
    }
}
