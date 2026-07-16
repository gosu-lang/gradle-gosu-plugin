package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Verifies that GosuPlugin correctly wires the Gosu classes directory into the
 * 'classes' secondary variant of apiElements and runtimeElements, and that Gradle
 * can infer the cross-project implicit dependency consumer:compileJava ->
 * producer:compileGosu without an explicit dependsOn.
 *
 * This test probes design.md hypotheses H1/H2/H3 (ISPL-16019):
 *   outgoingVariants pass  = Gosu dir is in the variant artifact list
 *   implicit-dep pass      = classesDirs.builtBy(compileGosu) propagates through
 *                            artifactsProvider; CompilePlugin workaround is deletable
 */
@Unroll
class GosuPluginVariantTest extends AbstractGosuPluginSpecification {

    // Build script used for single-project outgoingVariants tests.
    // java-library ensures withApi() is called, giving apiElements a 'classes' secondary variant.
    // No source files are needed: classesDirs is configured regardless of source existence.
    private static final String PRODUCER_BUILD = '''\
        plugins {
            id 'java-library'
            id 'org.gosu-lang.gosu'
        }
    '''

    // ── outgoingVariants assertions ──────────────────────────────────────────

    def 'gosu classes dir appears in classes secondary variant of runtimeElements [Gradle #gradleVersion]'() {
        given:
        buildScript << PRODUCER_BUILD

        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('outgoingVariants', '--variant', 'runtimeElements')
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result.task(':outgoingVariants').outcome == SUCCESS

        def classesVariantStart = result.output.indexOf('Secondary Variant classes')
        classesVariantStart >= 0

        result.output.substring(classesVariantStart).contains('''\
    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = 21
        - org.gradle.libraryelements     = classes
        - org.gradle.usage               = java-runtime
    Artifacts
        - build/classes/java/main (artifactType = java-classes-directory)
        - build/classes/gosu/main (artifactType = java-classes-directory)''')

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'gosu classes dir appears in classes secondary variant of apiElements [Gradle #gradleVersion]'() {
        given:
        buildScript << PRODUCER_BUILD

        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('outgoingVariants', '--variant', 'apiElements')
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result.task(':outgoingVariants').outcome == SUCCESS

        def classesVariantStart = result.output.indexOf('Secondary Variant classes')
        classesVariantStart >= 0

        result.output.substring(classesVariantStart).contains('''\
    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = 21
        - org.gradle.libraryelements     = classes
        - org.gradle.usage               = java-api
    Artifacts
        - build/classes/java/main (artifactType = java-classes-directory)
        - build/classes/gosu/main (artifactType = java-classes-directory)''')

        where:
        gradleVersion << gradleVersionsToTest
    }

    // ── cross-project implicit dependency ────────────────────────────────────

    def 'no implicit_dependency warning for compileGosu when consumer compileJava depends on Gosu producer [Gradle #gradleVersion]'() {
        given:
        File settingsFile = testProjectDir.newFile('settings.gradle')
        settingsFile << '''\
            include 'producer', 'consumer'
        '''

        File producerDir = testProjectDir.newFolder('producer')
        new File(producerDir, 'build.gradle') << """
            ${getBasicBuildScriptForTesting()}
        """
        File gosuSrcDir = new File(producerDir, 'src/main/gosu')
        gosuSrcDir.mkdirs()
        new File(gosuSrcDir, 'Greeter.gs') << '''\
            class Greeter {
              function greet() : String { return "hello" }
            }
        '''

        File consumerDir = testProjectDir.newFolder('consumer')
        new File(consumerDir, 'build.gradle') << '''\
            plugins {
                id 'java'
            }
            dependencies {
                implementation project(':producer')
            }
        '''
        File javaSrcDir = new File(consumerDir, 'src/main/java')
        javaSrcDir.mkdirs()
        new File(javaSrcDir, 'Consumer.java') << 'public class Consumer {}'

        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments(':consumer:compileJava', '--warning-mode', 'all')
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result.task(':producer:compileGosu').outcome == SUCCESS
        result.task(':consumer:compileJava').outcome == SUCCESS
        !result.output.contains('implicit_dependency')

        where:
        gradleVersion << gradleVersionsToTest
    }
}
