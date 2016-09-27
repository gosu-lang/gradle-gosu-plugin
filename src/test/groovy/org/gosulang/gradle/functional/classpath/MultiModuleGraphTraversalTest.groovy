package org.gosulang.gradle.functional.classpath

import org.gosulang.gradle.functional.AbstractGosuPluginSpecification
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.rules.TemporaryFolder
import spock.lang.Unroll

import java.util.regex.Matcher

import static org.fest.assertions.Assertions.assertThat
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * commons, base and app are three projects. On the file system, they are peers sitting at the same level on the tree
 */
@Unroll
class MultiModuleGraphTraversalTest extends AbstractGosuPluginSpecification {
    TemporaryFolder rootFolder
    File baseTestFolder
    File appFolder
    File appExtFolder
    File appTestFolder

    File rootProjectSettings

    File rootBuildScript
    File appExtBuildScript
    File appTestBuildScript

    File baseTestFoo
    File appFoo
    File fooTest

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

        baseTestFoo = new File(rootFolder.newFolder('baseTest', 'src', 'main', 'gosu'), 'Foo.gs')
        appFoo = new File(rootFolder.newFolder('app', 'src', 'main', 'gosu'), 'Foo.gs')
        fooTest = new File(rootFolder.newFolder('appTest', 'src', 'test', 'gosu'), 'FooTest.gs')

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
                    compile 'org.gosu-lang.gosu:gosu-core-api:$gosuVersion'
                    testCompile 'junit:junit:4.12'
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

        baseTestFoo <<
            """
            class Foo {
                construct() {
                    print("baseTest wins!")
                }
            }
            """

        appFoo <<
            """
            class Foo {
                construct() {
                    print("app wins!")
                }
            }
            """

        fooTest <<
            """
            uses org.junit.Test

            class FooTest {

                @Test
                function whoWins() {
                    new Foo()  //later we'll check the output of this
                }
            }
            """
    }

    def 'test breadth-first traversal (Gradle default behavior) [Gradle #gradleVersion]'() {
        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(rootFolder.root)
                .withPluginClasspath()
                .withArguments(':appTest:printClasspath', ':appTest:test', '-is')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()

        then:
        notThrown(UnexpectedBuildFailure)
        println(result.output)

        String[] expected = ['appExt.jar', 'baseTest.jar', 'app.jar']

        String[] orderedClasspath = getOrderedClasspath(result.output)

        println('orderedClasspath is: ' + orderedClasspath)

        //filter gosu dependencies not related to this test
        List<String> actual = orderedClasspath.findAll { expected.contains(it) }

        assertThat(actual).containsExactly(expected)

        result.task(':appTest:printClasspath').outcome == SUCCESS
        result.task(':appTest:test').outcome == SUCCESS

        result.output.contains('baseTest wins!')
        !result.output.contains('app wins!')

        where:
        gradleVersion << gradleVersionsToTest
    }

    /**
     * simulate depth-first traversal for top-level module 'appTest'
     * we want to order it like this: [gosu-core-api-1.x.jar, appExt.jar, app.jar, gw-asm-all-5.0.4.jar, baseTest.jar]
     * we'll do this by getting the list positions of app.jar and baseTest.jar and swapping them
     *
     * @return
     */
    def 'test depth-first traversal [Gradle #gradleVersion]'() {
        given:
        appTestBuildScript <<
            """
            def putAppAheadOfBaseTest(Configuration config) {
                FileCollection rearrangedClasspath // = []
                //outputs.files rearrangedClasspath

                List<File> defaultClasspath = config.files.asList()
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

            sourceSets {
                main {
                    compileClasspath = putAppAheadOfBaseTest(configurations.compile)
                }
                test {
                   runtimeClasspath = putAppAheadOfBaseTest(configurations.testRuntime) + sourceSets.test.runtimeClasspath
                }
            }
            """
        
        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(rootFolder.root)
                .withPluginClasspath()
                .withArguments(':appTest:printClasspath', ':appTest:test', '-is')
                .withGradleVersion(gradleVersion)
                .forwardOutput()
        
        BuildResult result = runner.build()

        then:
        notThrown(UnexpectedBuildFailure)
        println(result.output)

        String[] expected = ['appExt.jar', 'app.jar', 'baseTest.jar']

        String[] orderedClasspath = getOrderedClasspath(result.output)
        
        println('orderedClasspath is: ' + orderedClasspath)

        //filter gosu dependencies not related to this test
        List<String> actual = orderedClasspath.findAll { expected.contains(it) }

        assertThat(actual).containsExactly(expected)

        result.task(':appTest:printClasspath').outcome == SUCCESS
        result.task(':appTest:test').outcome == SUCCESS

        result.output.contains('app wins!')
        !result.output.contains('baseTest wins!')

        where:
        gradleVersion << gradleVersionsToTest
    }

    private static String[] getOrderedClasspath(String stdOut) {
        //get the line starting with 'The classpath is: '
        Matcher matcher = stdOut =~ /The classpath is: \[(.*)\]/
        matcher.find()
        matcher.groupCount() == 1
        return matcher.group(1).split(', ')
    }

}
