package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.VersionNumber
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Unroll
class LocalBuildCacheTest extends AbstractGosuPluginSpecification {

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
                .withPluginClasspath()
                .withArguments('gosudoc', '--build-cache')

        BuildResult result = runner.build()

        then:
        result.task(":compileGosu").outcome == SUCCESS
        result.task(":gosudoc").outcome == SUCCESS

        result.output.contains("""
5 tasks in build, out of which 3 (60%) were executed
2  (40%) no-source
2  (40%) cache miss
1  (20%) not cacheable
""")
        
        assertOutputs()
        
        when:
        runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('clean', 'gosudoc', '--build-cache')

        result = runner.build()

        then:
        result.task(":compileGosu").outcome == FROM_CACHE
        result.task(":gosudoc").outcome == FROM_CACHE

        result.output.contains("""
6 tasks in build, out of which 1 (17%) were executed
1  (17%) up-to-date
2  (33%) no-source
2  (33%) loaded from cache
1  (17%) not cacheable
""")

        assertOutputs()        
        
        where:
        gradleVersion << gradleVersionsToTest.findAll { VersionNumber.parse(it) >= VersionNumber.parse('3.5') } // build caching only available since Gradle 3.5
    }

    private boolean assertOutputs() {
        //did we actually compile anything?
        return new File(testProjectDir.root, asPath('build', 'classes', 'main', 'example', 'gradle', 'SimplePogo.class')).exists() &&
        //did we actually doc anything?
        new File(testProjectDir.root, asPath('build', 'docs', 'gosudoc', 'index.html')).exists()

    }
    
}
