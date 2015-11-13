package org.gosulang.gradle.functional.classpath

import org.gosulang.gradle.functional.AbstractGosuPluginSpecification
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.rules.TemporaryFolder

import java.util.regex.Matcher

import static org.fest.assertions.Assertions.*
import static org.gradle.testkit.runner.TaskOutcome.*

/**
 * commons, base and app are three projects. On the file system, they are peers sitting at the same level on the tree
 */
class MultiModuleGraphTraversalTest extends AbstractGosuPluginSpecification {
    TemporaryFolder rootFolder
    File baseTestFolder
    File appFolder
    File appExtFolder
    File appTestFolder

    File rootProjectSettings

    GString rootBuildScriptText
    
    File rootBuildScript
    File appExtBuildScript
    File appTestBuildScript

    /**
     * super#setup is invoked automatically
     * @return
     */
    @Override
    def setup() {
        rootFolder = testProjectDir
        baseTestFolder = rootFolder.newFolder('baseTest')
        appFolder = rootFolder.newFolder('app')
        appExtFolder = rootFolder.newFolder('appExt')
        appTestFolder = rootFolder.newFolder('appTest')
        
        rootProjectSettings = rootFolder.newFile('settings.gradle')
        
        rootBuildScript = buildScript //from superclass
        appExtBuildScript = new File(appExtFolder, 'build.gradle')
        appTestBuildScript = new File(appTestFolder, 'build.gradle')
        
        rootBuildScript <<
            """
            plugins {
                id 'org.gosu-lang.gosu'
            }

            allprojects {
                apply plugin: 'org.gosu-lang.gosu'
                repositories {
                    mavenLocal()
                    mavenCentral()
                    maven {
                        url 'https://oss.sonatype.org/content/repositories/snapshots' //for Gosu snapshot builds
                    }
                }
                dependencies {
                    compile group: 'org.gosu-lang.gosu', name: 'gosu-core-api', version: '$gosuVersion'
                }
                task printClasspath << {
                    println 'The classpath is: ' + compileGosu.classpath.files.collect { it.name } //configurations.compile.files.collect { it.name }
                }
            }
            """
        
        rootProjectSettings <<
            """
            include ':baseTest'
            include ':app'
            include ':appExt'
            include ':appTest'
            """

        appExtBuildScript << 
            """
            dependencies {
                compile project(':app')
            }
            """

        appTestBuildScript <<
            """
            dependencies {
                compile project(':appExt')
                compile project(':baseTest')
            }
            """
    }

    def 'test breadth-first traversal (Gradle default behavior)'() {
        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(rootFolder.root)
                .withPluginClasspath(pluginClasspath)
                .withArguments(':appTest:printClasspath', ':appTest:test', '-is')

        BuildResult result = runner.build()

        then:
        notThrown(UnexpectedBuildFailure)
        println(result.standardOutput)
        result.standardError.empty

        String[] expected = ['appExt.jar', 'baseTest.jar', 'app.jar']

        String[] orderedClasspath = getOrderedClasspath(result.standardOutput)

        println('orderedClasspath is: ' + orderedClasspath)

        //filter gosu dependencies not related to this test
        List<String> actual = orderedClasspath.findAll { expected.contains(it) }

        assertThat(actual).containsExactly(expected)

        result.task(':appTest:printClasspath').outcome == SUCCESS
//        result.task(':appTest:test').outcome == SUCCESS

//        result.standardOutput.contains('baseTest wins!') //TODO enable
    }

    /**
     * simulate depth-first traversal for top-level module 'appTest'
     * we want to order it like this: [gosu-core-api-1.9.1.jar, appExt.jar, app.jar, gw-asm-all-5.0.4.jar, baseTest.jar]
     * we'll do this by getting the list positions of app.jar and baseTest.jar and swapping them
     *
     * @return
     */
    def 'test depth-first traversal'() {
        given:
        appTestBuildScript <<
            """
            compileGosu {
                classpath = putAppAheadOfBaseTest()
            }

            def putAppAheadOfBaseTest() {
                FileCollection rearrangedClasspath // = []
                //outputs.files rearrangedClasspath

                List<File> defaultClasspath = configurations.compile.files.asList()
                println 'Got defaultClasspath: ' + defaultClasspath.collect { it.name }
                int baseTestPosition = defaultClasspath.findIndexOf { it.name.equals('baseTest.jar') }
                int appPosition = defaultClasspath.findIndexOf { it.name.equals('app.jar') }
                def tmp = defaultClasspath.getAt(baseTestPosition)
                defaultClasspath[baseTestPosition] = defaultClasspath.getAt(appPosition)
                defaultClasspath[appPosition] = tmp
                rearrangedClasspath = project.files(defaultClasspath)
                println 'Rearranged to ' + rearrangedClasspath.collect { it.name }
                return rearrangedClasspath
            }
            """
        
        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(rootFolder.root)
                .withPluginClasspath(pluginClasspath)
                .withArguments(':appTest:printClasspath', ':appTest:test', '-is')
        
        BuildResult result = runner.build()

        then:
        notThrown(UnexpectedBuildFailure)
        println(result.standardOutput)
        result.standardError.empty

        String[] expected = ['appExt.jar', 'app.jar', 'baseTest.jar']

        String[] orderedClasspath = getOrderedClasspath(result.standardOutput)
        
        println('orderedClasspath is: ' + orderedClasspath)

        //filter gosu dependencies not related to this test
        List<String> actual = orderedClasspath.findAll { expected.contains(it) }

        assertThat(actual).containsExactly(expected)

        result.task(':appTest:printClasspath').outcome == SUCCESS
//        result.task(':appTest:test').outcome == SUCCESS

//        result.standardOutput.contains('app wins!') //TODO enable
    }

    private static String[] getOrderedClasspath(String stdOut) {
        //get the line starting with 'The classpath is: '
        Matcher matcher = stdOut =~ /The classpath is: \[(.*)\]/
        matcher.find()
        matcher.groupCount() == 1
        return matcher.group(1).split(', ')
    }

}
