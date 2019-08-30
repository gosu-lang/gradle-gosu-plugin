package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.VersionNumber
import org.junit.Ignore
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Unroll
@Ignore
class LocalBuildCacheTest extends AbstractGosuPluginSpecification {

    /**
     * For this test only, we override GradleRunner's TestKitDir property with a randomly generated folder.
     * This ensures the local cache does not linger for subsequent test runs.
     */
    @Rule
    TemporaryFolder testKitDir = new TemporaryFolder()

    File srcMainGosu
    File simplePogo

    /**
     * super#setup is invoked automatically
     * @return
     */
    @Override
    def setup() {
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
    }

    def 'apply gosu plugin and compile [Gradle #gradleVersion]'() {
        given:
        buildScript << getBasicBuildScriptForTesting()

        simplePogo = new File(srcMainGosu, asPath('example', 'gradle', 'SimplePogo.gs'))
        simplePogo.getParentFile().mkdirs()
        simplePogo << """
            package example.gradle
            
            class SimplePogo {}"""

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withTestKitDir(testKitDir.root)
                .withPluginClasspath()
                .withArguments('gosudoc', '--build-cache')

        BuildResult result = runner.build()

        then:
        result.task(":compileGosu").outcome == SUCCESS
        result.task(":gosudoc").outcome == SUCCESS

        if(VersionNumber.parse(gradleVersion) >= VersionNumber.parse('4.0')) {
            result.output.contains('2 actionable tasks: 2 executed')
        } else {
            result.output.contains("""
5 tasks in build, out of which 3 (60%) were executed
2  (40%) no-source
2  (40%) cache miss
1  (20%) not cacheable
""")
        }

        assertTaskOutputs()
        
        when:
        runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withTestKitDir(testKitDir.root)
                .withPluginClasspath()
                .withArguments('clean', 'gosudoc', '--build-cache')

        result = runner.build()

        then:
        result.task(":compileGosu").outcome == FROM_CACHE
        result.task(":gosudoc").outcome == FROM_CACHE

        if(VersionNumber.parse(gradleVersion) >= VersionNumber.parse('4.0')) {
            result.output.contains('2 actionable tasks: 0 executed')
        } else {
            result.output.contains("""
6 tasks in build, out of which 1 (17%) were executed
1  (17%) up-to-date
2  (33%) no-source
2  (33%) loaded from cache
1  (17%) not cacheable
""")
        }

        assertTaskOutputs()
        
        where:
        gradleVersion << gradleVersionsToTest.findAll { VersionNumber.parse(it) >= VersionNumber.parse('3.5') } // build caching only available since Gradle 3.5
    }

    private boolean assertTaskOutputs() {
        //did we actually compile anything?
        return new File(testProjectDir.root, asPath(expectedOutputDir(gradleVersion) + ['main', 'example', 'gradle', 'SimplePogo.class'])).exists() &&
        //did we actually doc anything?
        new File(testProjectDir.root, asPath('build', 'docs', 'gosudoc', 'index.html')).exists()

    }
    
}
