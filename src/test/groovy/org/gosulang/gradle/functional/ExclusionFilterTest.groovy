package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

@Unroll
class ExclusionFilterTest extends AbstractGosuPluginSpecification {

    File srcMainGosu

    /**
     * super#setup is invoked automatically
     * @return
     */
    @Override
    def setup() {
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
    }

        def 'test pattern-based exclusion [Gradle #gradleVersion]'() {
        given:
        File foo = new File(srcMainGosu, 'Foo.gs')
        File bar = new File(srcMainGosu, 'Bar.gs')
        File errant = new File(srcMainGosu, 'Errant_Class.gs')

        buildScript << getBasicBuildScriptForTesting() +
            """
            sourceSets {
                    main {
                        gosu {
                            exclude '**/Errant_*'
                        }
                    }
            }
            """

        foo <<
        """
        class Foo extends Bar {}
        """

        bar <<
        """
        class Bar {}
        """

        errant <<
        """
        This is an errant class; the content shouldn't matter as it won't ever be compiled.
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
        result.task(':compileTestGosu').outcome == UP_TO_DATE //no tests to compile
        result.task(':test').outcome == UP_TO_DATE //no tests to compile

        File buildOutputRoot = new File(testProjectDir.root, asPath('build', 'classes', 'main'))
        new File(buildOutputRoot, 'Foo.class').exists()
        new File(buildOutputRoot, 'Bar.class').exists()
        !new File(buildOutputRoot, 'Errant_Class.class').exists()

        where:
        gradleVersion << gradleVersionsToTest
    }

}
