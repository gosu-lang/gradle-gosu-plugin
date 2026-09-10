package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * The synthetic classes a Gosu block (lambda) compiles into.
 *
 * <p>A block has no source file of its own, so removing one leaves no removed <em>type</em> for
 * cleanup to key on -- the enclosing type is merely modified, and both
 * {@link NonIncrementalStaleOutputTest} and {@link IncrementalStaleOutputTest} turn on a source
 * disappearing. The orphaned {@code $block} classes must still go, by whatever means each modality
 * has: the flag-off path empties the destination wholesale, and gosuc handles it when the flag is
 * on.
 */
@Unroll
class BlockSyntheticClassCleanupTest extends AbstractGosuPluginSpecification {

    File srcMainGosu
    File classA

    /**
     * super#setup is invoked automatically
     * @return
     */
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

    /**
     * The synthetic names are gosuc's to choose and carry a hash, so they are matched by shape
     * rather than named outright.
     */
    private static List<String> syntheticClasses(String buildOutput) {
        File[] files = new File(buildOutput).listFiles()
        return (files == null ? [] : files.collect { it.name })
                .findAll { it.startsWith('ClassA$') && it.endsWith('.class') }
                .sort()
    }

    def 'Removing a block prunes its synthetic classes [#modality, Gradle #gradleVersion]'() {
        given:
        buildScript << (incremental ? getIncrementalBuildScriptForTesting() : getBasicBuildScriptForTesting())
        classA << WITH_BLOCK

        when: 'Initial compilation'
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('clean', 'compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()
        String buildOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')
        List<String> afterFirstPass = syntheticClasses(buildOutput)

        then: 'The block compiles into at least one synthetic class alongside ClassA'
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'ClassA.class').exists()
        !afterFirstPass.isEmpty()

        when: 'The block is replaced by null - ClassA is modified, no type is removed'
        classA.text = WITHOUT_BLOCK

        runner.withArguments('compileGosu', '-i')
        result = runner.build()

        then: 'ClassA recompiles'
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'ClassA.class').exists()

        and: 'And no orphaned synthetic class survives'
        syntheticClasses(buildOutput).isEmpty()

        where:
        [modality, incremental, gradleVersion] << [
                ['full', false],
                ['incremental', true]
        ].collectMany { pair -> gradleVersionsToTest.collect { v -> pair + [v] } }
    }
}
