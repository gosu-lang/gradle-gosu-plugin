package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Ignore

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Ignore('This is known to fail when configuration-cache is enabled')
class ConfigurationCacheClasspathReorderTest extends AbstractGosuPluginSpecification {

    File srcMainGosu
    File simplePogo

    @Override
    def setup() {
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
    }

    def 'apply gosu plugin and reorder classpath with configuration cache in strict mode'() {
        given:
        buildScript << getBasicBuildScriptForTesting()
        buildScript.text +=
                '''
                |tasks.withType(org.gosulang.gradle.tasks.compile.GosuCompile).configureEach { t ->
                |  // this accepts a BiFunction<Project, Configuration, FileCollection>, which in this case reverses the classpath order
                |  t.orderClasspathFunction = (Project p, Configuration c) -> { 
                |    t.logger.quiet("Project name: {}", p.name)
                |    def originalList = c.getFiles().asList()
                |    def reversedList = []
                |    ListIterator li = originalList.listIterator(originalList.size())
                |    while(li.hasPrevious()) {
                |      reversedList.add(li.previous())
                |    }
                |    t.logger.quiet('Original list: {}', originalList)
                |    t.logger.quiet('Reversed list: {}', reversedList)
                |    return p.objects.fileCollection().from(reversedList)
                |  }
                |}
                |
                '''.stripMargin()
        testProjectDir.newFile('settings.gradle') << 'enableFeaturePreview "STABLE_CONFIGURATION_CACHE"'

        simplePogo = new File(srcMainGosu, asPath('example', 'gradle', 'SimplePogo.gs'))
        simplePogo.getParentFile().mkdirs()
        simplePogo << """
            package example.gradle
            
            class SimplePogo {}"""

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('compileGosu', '--no-configuration-cache')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()

        then:
        result.task(":compileGosu").outcome == SUCCESS
        result.output.contains('Configuration cache entry stored.')

        when:
        result = runner.build()

        then:
        result.task(":compileGosu").outcome == SUCCESS
        result.output.contains('Reusing configuration cache.')
        result.output.contains('Configuration cache entry reused.')

        where:
        gradleVersion << gradleVersionsToTest
    }
}
