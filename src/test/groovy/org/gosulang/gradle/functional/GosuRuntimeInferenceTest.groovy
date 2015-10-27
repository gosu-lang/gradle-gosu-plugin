package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.gradle.testkit.runner.UnexpectedBuildSuccess
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.util.stream.Collectors

class GosuRuntimeInferenceTest extends AbstractGosuPluginSpecification {

    File simplePogo
    
    def 'Build throws when gosu-core-api jar is not declared as a dependency'() {
        given:
        buildScript << 
            """
            plugins {
                id 'org.gosu-lang.gosu'
            }
            repositories {
                mavenLocal()
                mavenCentral()
                maven {
                    url 'https://oss.sonatype.org/content/repositories/snapshots' //for Gosu snapshot builds
                }
            }
            dependencies {
                //compile group: 'org.gosu-lang.gosu', name: 'gosu-core-api', version: '$gosuVersion' //intentionally commenting-out to cause build failure
                testCompile group: 'junit', name: 'junit', version: '4.11'
            }
            """
        
        simplePogo = new File(testProjectDir.newFolder('src', 'main', 'gosu'), 'SimplePogo.gs')
        simplePogo << """
            public class SimplePogo {
            }"""
        
        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath(pluginClasspath)
                .withArguments('clean', 'compileGosu', '-is')
        
        BuildResult result = runner.buildAndFail()

        println('--- Dumping stdout ---')
        println(result.getStandardOutput())
        println('--- Done dumping stdout ---')
        println()
        println('--- Dumping stderr ---')
        println(result.getStandardError())
        println('--- Done dumping stderr ---')
                
        then:
        notThrown(UnexpectedBuildSuccess)
        result.getStandardError().contains('Cannot infer Gosu classpath because the Gosu Core API Jar was not found.')
        !new File(testProjectDir.root, asPath('build', 'classes', 'main', 'SimplePogo.class')).exists()
    }


}
