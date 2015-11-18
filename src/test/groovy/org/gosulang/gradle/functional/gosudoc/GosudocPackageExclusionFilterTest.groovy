package org.gosulang.gradle.functional.gosudoc

import org.gosulang.gradle.functional.AbstractGosuPluginSpecification
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure

class GosudocPackageExclusionFilterTest extends AbstractGosuPluginSpecification {

    File srcMainGosu, includedPkg1, includedPkg2, excludedPkg
    File simplePogo
    File anotherPogo
    File excludedPogo

    /**
     * super#setup is invoked automatically
     * @return
     */
    @Override
    def setup() {
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')

        //setup sample files
        simplePogo = new File(srcMainGosu, asPath('included1', 'SimplePogo.gs'))
        simplePogo.getParentFile().mkdirs()
        simplePogo <<
            """
            package included1
            
            /**
             * This is SimplePogo
             */
            class SimplePogo {}
            """

        anotherPogo = new File(srcMainGosu, asPath('included2', 'AnotherPogo.gs'))
        anotherPogo.getParentFile().mkdirs()
        anotherPogo <<
            """
            package included2
            
            /**
             * This is AnotherPogo...
             */
            class AnotherPogo {}
            """

        excludedPogo = new File(srcMainGosu, asPath('donotwant', 'ExcludedPogo.gs'))
        excludedPogo.getParentFile().mkdirs()
        excludedPogo <<
            """
            package donotwant
            
            /**
             * This is ExcludedPogo
             */
            class ExcludedPogo {}
            """

    }

    /**
     * Gosudoc extends SourceTask, so we get includes/excludes filtering for free
     * @return
     */
    def 'test pattern-based package exclusion'() {
        given:
        buildScript << getBasicBuildScriptForTesting() +
            """
            gosudoc {
                exclude '**/*not*/*'
            }
            """

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath(pluginClasspath)
                .withArguments('gosudoc', '-is')

        BuildResult result = runner.build()

        then:
        notThrown(UnexpectedBuildFailure)
        result.standardOutput.contains('Generating Documentation')
        result.standardOutput.contains('included1.SimplePogo - document : true')
        result.standardOutput.contains('included2.AnotherPogo - document : true')
        !result.standardOutput.contains('donotwant.ExcludedPogo - document : true')

        File gosudocOutputRoot = new File(testProjectDir.root, asPath('build', 'docs', 'gosudoc'))
        File simplePogoGosudoc = new File(gosudocOutputRoot, asPath('included1', 'included1.SimplePogo.html'))
        File anotherPogoGosudoc = new File(gosudocOutputRoot, asPath('included2', 'included2.AnotherPogo.html'))
        File excludedPogoGosudoc = new File(gosudocOutputRoot, asPath('donotwant', 'donotwant.ExcludedPogo.html'))

        //validate the generated HTML
        simplePogoGosudoc.exists()
        simplePogoGosudoc.readLines().contains('<div class="block">This is SimplePogo</div>')
        anotherPogoGosudoc.exists()
        anotherPogoGosudoc.readLines().contains('<div class="block">This is AnotherPogo...</div>')
        !excludedPogoGosudoc.exists()

    }
    
}
