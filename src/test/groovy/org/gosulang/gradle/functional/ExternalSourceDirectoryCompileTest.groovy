package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Regression test for the source-root inference regression introduced in 8.1.4.
 *
 * <p>When an external plugin adds a source directory to {@code compileGosu} via
 * {@link org.gradle.api.tasks.SourceTask#source}, that directory must also appear
 * in the task's {@code sourceRoots} collection so the Gosu type system can resolve
 * type names from file paths.  Prior to 8.1.4 this was handled implicitly by the
 * internal {@code CompilationSourceDirs.inferSourceRoots}; 8.1.4 switched to an
 * explicit config-time wiring that only covered directories declared through the
 * Gosu {@link org.gradle.api.file.SourceDirectorySet}, missing any directory added
 * after configuration via {@code source()}.
 *
 * <p>The canonical caller pattern is a plugin that adds a pre-populated directory
 * (e.g. a {@code Provider<Directory>} from a download task) via:
 * <pre>
 *   tasks.named("compileGosu", SourceTask.class,
 *       t -&gt; t.source(downloadTask.map(t -&gt; t.getDestinationDir())));
 * </pre>
 * Without the fix, gosuc fails with
 * {@code "Cannot find type in the Gosu Type System"} at {@code [0,0]} for every
 * Gosu file in the externally-added directory, because the Gosu type system cannot
 * derive the fully-qualified type name without knowing the source root.
 */
@Unroll
class ExternalSourceDirectoryCompileTest extends AbstractGosuPluginSpecification {

    File srcMainGosu

    def setup() {
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
    }

    def 'source directory added via SourceTask.source() is compiled successfully [Gradle #gradleVersion]'() {
        given: 'a normal Gosu source file in the standard source set'
        buildScript << getBasicBuildScriptForTesting() + """
            // Simulate an external plugin adding a pre-populated directory via SourceTask.source()
            // rather than through the Gosu source set (the plugin cannot cast to GosuCompile).
            def externalDir = layout.projectDirectory.dir('src/external/gosu')
            tasks.named('compileGosu', org.gradle.api.tasks.SourceTask) {
                source externalDir
            }
        """
        File pogo = new File(srcMainGosu, asPath('example', 'gradle', 'SimplePogo.gs'))
        pogo.getParentFile().mkdirs()
        pogo << 'package example.gradle\n\nclass SimplePogo {}'

        and: 'a Gosu source file in the external directory — added via source(), not via the Gosu source set'
        File externalSrc = testProjectDir.newFolder('src', 'external', 'gosu', 'example', 'gradle')
        new File(externalSrc, 'ExternalPogo.gs') << 'package example.gradle\n\nclass ExternalPogo {}'

        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('compileGosu')
                .withGradleVersion(gradleVersion)
                .build()

        then: 'both source files compile — the externally-added directory is recognised as a source root'
        result.task(':compileGosu').outcome == SUCCESS

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'external sourceRoots survive configuration-cache serialisation [Gradle #gradleVersion]'() {
        given: 'a project with a standard and an externally-wired source directory'
        buildScript << getBasicBuildScriptForTesting() + """
            def externalDir = layout.projectDirectory.dir('src/external/gosu')
            tasks.named('compileGosu', org.gradle.api.tasks.SourceTask) {
                source externalDir
            }
        """
        File pogo = new File(srcMainGosu, asPath('example', 'gradle', 'SimplePogo.gs'))
        pogo.getParentFile().mkdirs()
        pogo << 'package example.gradle\n\nclass SimplePogo {}'

        File externalSrc = testProjectDir.newFolder('src', 'external', 'gosu', 'example', 'gradle')
        new File(externalSrc, 'ExternalPogo.gs') << 'package example.gradle\n\nclass ExternalPogo {}'

        when: 'the configuration cache entry is stored'
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('compileGosu', '--configuration-cache', '--configuration-cache-problems=warn')
                .withGradleVersion(gradleVersion)
        BuildResult storeResult = runner.build()

        then:
        storeResult.task(':compileGosu').outcome == SUCCESS
        storeResult.output.contains('Configuration cache entry stored.')

        when: 'the build runs again with identical inputs'
        BuildResult reuseResult = runner.build()

        then: 'the CC entry is reused'
        reuseResult.output.contains('Reusing configuration cache.')
        reuseResult.output.contains('Configuration cache entry reused.')

        when: 'a new file is added to the external directory, forcing compileGosu to re-execute under CC reuse'
        new File(externalSrc, 'AnotherExternalPogo.gs') << 'package example.gradle\n\nclass AnotherExternalPogo {}'
        BuildResult rerunResult = runner.build()

        then: 'CC is still reused and the external sourceRoot was correctly preserved in the CC entry'
        rerunResult.output.contains('Reusing configuration cache.')
        rerunResult.task(':compileGosu').outcome == SUCCESS

        where:
        gradleVersion << gradleVersionsToTest
    }
}
