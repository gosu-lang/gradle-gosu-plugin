package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.VersionNumber
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.NO_SOURCE
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

@Unroll
class SourceSetsModificationTest extends AbstractGosuPluginSpecification {
    
    File srcMainGosu

    /**
     * super#setup is invoked automatically
     * @return
     */
    @Override
    def setup() {
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
    }
    
    def 'non-standard source roots [Gradle #gradleVersion]'() {
        given:
        String[] rootOne = ['folder', 'containing', 'POGOs']
        String[] rootTwo = ['foo', 'bar']
        String[] rootThree = ['baz']
        String[] ignoredRoot = ['ignored', 'source', 'root']

        File configuredSourceRootOne = testProjectDir.newFolder(rootOne)
        File configuredSourceRootTwo = testProjectDir.newFolder(rootTwo)
        File configuredSourceRootThree = testProjectDir.newFolder(rootThree)
        File ignoredSourceRoot = testProjectDir.newFolder(ignoredRoot)

        String buildScriptContent = getBasicBuildScriptForTesting()
        buildScriptContent += """
        sourceSets {
                main {
                    gosu {
                        srcDir '${asPath(rootOne)}'
                        srcDirs '${asPath(rootTwo)}', '${asPath(rootThree)}'
                    }
                }
        }"""

        System.out.println("--- Dumping build.gradle ---");
        System.out.println(buildScriptContent);
        System.out.println("--- Done dumping build.gradle ---");
        
        buildScript << buildScriptContent
        
        File pogoOne = new File(configuredSourceRootOne, asPath('one', 'ConfiguredPogoOne.gs'))
        pogoOne.getParentFile().mkdirs()
        pogoOne << """package one
                      public class ConfiguredPogoOne {}
                   """

        File pogoTwo = new File(configuredSourceRootTwo, asPath('two', 'ConfiguredPogoTwo.gs'))
        pogoTwo.getParentFile().mkdirs()
        pogoTwo << """package two
                      public class ConfiguredPogoTwo {}
                   """

        File pogoThree = new File(configuredSourceRootThree, asPath('three', 'ConfiguredPogoThree.gs'))
        pogoThree.getParentFile().mkdirs()
        pogoThree << """package three
                        public class ConfiguredPogoThree {}
                     """


        File ignoredPogo = new File(ignoredSourceRoot, asPath('four', 'IgnoredPogo.gs'))
        ignoredPogo.getParentFile().mkdirs()
        ignoredPogo << """package four
                          public class IgnoredPogo {}
                       """

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('build', '-is')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()

        then:
        result.output.contains('Initializing gosuc compiler')
        result.task(':compileGosu').outcome == SUCCESS
        
        if(VersionNumber.parse(gradleVersion) < VersionNumber.parse('3.4.0')) {
            result.task(':compileTestGosu').outcome == UP_TO_DATE //no tests to compile
            result.task(':test').outcome == UP_TO_DATE //no tests to run
        } else {
            result.task(':compileTestGosu').outcome == NO_SOURCE //no tests to compile
            result.task(':test').outcome == NO_SOURCE //no tests to run
        }

        File buildOutputRoot = new File(testProjectDir.root, asPath(expectedOutputDir(gradleVersion) + 'main'))
        new File(buildOutputRoot, asPath('one', 'ConfiguredPogoOne.class')).exists()
        new File(buildOutputRoot, asPath('two', 'ConfiguredPogoTwo.class')).exists()
        new File(buildOutputRoot, asPath('three', 'ConfiguredPogoThree.class')).exists()
        !new File(buildOutputRoot, asPath('four', 'IgnoredPogo.class')).exists()

        where:
        gradleVersion << gradleVersionsToTest
    }
}
