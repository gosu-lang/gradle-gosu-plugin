package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Companion to {@link StaleOutputCleanupTest}, covering the synthetic classes a Gosu block
 * (lambda) compiles into.  A block does not correspond to a source file of its own, so removing
 * one leaves no removed <em>type</em> for anyone to key cleanup on -- the enclosing type is merely
 * modified.  Both compilation modalities must still prune the orphaned {@code $block} classes.
 */
@Unroll
class StaleBlockClassCleanupTest extends AbstractGosuPluginSpecification {

    File srcMainGosu, classA

    def setup() {
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
        classA = new File(srcMainGosu, 'ClassA.gs')
    }

    private static final String WITH_BLOCK = """
             class ClassA {
               static function foo() {
                 var r : Runnable = \\ -> { var g = 0 }
                 print(r)
               }
             }
             """

    private static final String WITHOUT_BLOCK = """
             class ClassA {
               static function foo() {
                 var r : Runnable = null
                 print(r)
               }
             }
             """

    private static List<String> syntheticClasses(String buildOutput) {
        File[] files = new File(buildOutput).listFiles()
        return (files == null ? [] : files.collect { it.name })
                .findAll { it.startsWith('ClassA$') && it.endsWith('.class') }
                .sort()
    }

    def 'removing a block prunes its synthetic classes [#modality, Gradle #gradleVersion]'() {
        given:
        buildScript << (incremental ? getIncrementalBuildScriptForTesting() : getBasicBuildScriptForTesting())
        classA << WITH_BLOCK

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()
        String buildOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')
        List<String> blocksAfterFirstPass = syntheticClasses(buildOutput)
        println("$modality: synthetic classes after first pass: $blocksAfterFirstPass")

        then: 'the block compiles into at least one synthetic class alongside ClassA'
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'ClassA.class').exists()
        !blocksAfterFirstPass.isEmpty()

        when: 'the block is replaced by null -- ClassA is modified, no type is removed'
        classA.text = WITHOUT_BLOCK
        result = runner.build()
        List<String> blocksAfterSecondPass = syntheticClasses(buildOutput)
        println("$modality: synthetic classes after second pass: $blocksAfterSecondPass")

        then:
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'ClassA.class').exists()

        and: 'no orphaned synthetic class survives'
        blocksAfterSecondPass.isEmpty()

        where:
        [modality, incremental, gradleVersion] << [
                ['full', false],
                ['incremental', true]
        ].collectMany { pair -> gradleVersionsToTest.collect { v -> pair + [v] } }
    }
}
