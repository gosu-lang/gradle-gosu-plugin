package org.gosulang.gradle.functional.gosudoc

import org.gosulang.gradle.functional.AbstractGosuPluginSpecification
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure

class GosudocMultipleSourcesTest extends AbstractGosuPluginSpecification {

    File srcMainGosu
    File additionalSourceRoot
    File simplePogo
    File anotherPogo

    /**
     * super#setup is invoked automatically
     * @return
     */
    @Override
    def setup() {
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
        additionalSourceRoot = testProjectDir.newFolder('folder', 'containing', 'POGOs')

        //setup sample files
        simplePogo = new File(srcMainGosu, asPath('normal', 'SimplePogo.gs'))
        simplePogo.getParentFile().mkdirs()
        simplePogo << 
            """
            package normal
            
            /**
             * This is SimplePogo
             */
            class SimplePogo {}
            """
        
        anotherPogo = new File(additionalSourceRoot, asPath('alt', 'AnotherPogo.gs'))
        anotherPogo.getParentFile().mkdirs()
        anotherPogo <<
            """
            package alt
            
            uses java.math.BigDecimal
            
            /**
             * This is AnotherPogo...
             */
            class AnotherPogo {
                //adding non-default constructors just for fun
                construct(str : String) {}
                construct(i : Integer, bd : BigDecimal) {}
            }
            """
        
    }

    def 'generate Gosudoc from multiple source roots'() {
        given:
        buildScript << getBasicBuildScriptForTesting() +
        """
        sourceSets {
                main {
                    gosu {
                        srcDirs = ['src/main/gosu', 'folder/containing/POGOs']
                    }
                }
        }"""
        
        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath(pluginClasspath)
                .withArguments('gosudoc', '-is')

        BuildResult result = runner.build()
        
        then:
        notThrown(UnexpectedBuildFailure)
        result.standardOutput.contains('Generating Documentation')
        result.standardOutput.contains('normal.SimplePogo - document : true')
        result.standardOutput.contains('alt.AnotherPogo - document : true')

        File gosudocOutputRoot = new File(testProjectDir.root, asPath('build', 'docs', 'gosudoc'))
        File simplePogoGosudoc = new File(gosudocOutputRoot, asPath('normal', 'normal.SimplePogo.html'))
        File anotherPogoGosudoc = new File(gosudocOutputRoot, asPath('alt', 'alt.AnotherPogo.html'))

        //validate the generated HTML
        simplePogoGosudoc.exists()
        simplePogoGosudoc.readLines().contains('<div class="block">This is SimplePogo</div>')
        anotherPogoGosudoc.exists()
        anotherPogoGosudoc.readLines().contains('<div class="block">This is AnotherPogo...</div>')
        
    }

}
