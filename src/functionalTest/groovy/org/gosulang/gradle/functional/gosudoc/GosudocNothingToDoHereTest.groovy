package org.gosulang.gradle.functional.gosudoc

import org.gosulang.gradle.functional.AbstractGosuPluginSpecification
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

@Unroll
class GosudocNothingToDoHereTest extends AbstractGosuPluginSpecification {

    File srcMainGosu
    File simpleStringTemplate

    /**
     * super#setup is invoked automatically
     * @return
     */
    @Override
    def setup() {
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
    }

    /**
     * String templates themselves are not gosudoc-ed. So while the source set is non-empty, 
     * gosudoc should throw as there are no eligible inputs. 
     */
    def 'execute gosudoc with no sources to doc [Gradle #gradleVersion]'() {
        given:
        buildScript << getBasicBuildScriptForTesting()
        
        simpleStringTemplate = new File(srcMainGosu, asPath('example', 'gradle', 'SimpleTemplate.gst'))
        simpleStringTemplate.getParentFile().mkdirs()
        simpleStringTemplate << '''<%@ params(p1 : String) %>One Arg Template: <%= p1 %>'''

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('gosudoc', '-is')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.buildAndFail()

        then:
        result.output.contains('Generating Documentation')
        result.output.contains('example.gradle.SimpleTemplate - document : false')
        result.output.contains('ERROR: No public or protected classes found to document.}')

        File gosudocOutputRoot = new File(testProjectDir.root, asPath('build', 'docs', 'gosudoc'))
        File simpleStringTemplateGosudoc = new File(gosudocOutputRoot, asPath('example', 'gradle', 'example.gradle.SimpleTemplate.html'))

        !simpleStringTemplateGosudoc.exists()

        where:
        gradleVersion << gradleVersionsToTest
    }
}
