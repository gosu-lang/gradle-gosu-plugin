package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.gradle.testkit.runner.UnexpectedBuildSuccess
import org.gosulang.gradle.util.VersionNumber
import spock.lang.Unroll

import java.util.regex.Pattern

import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

@Unroll
class CompileInputChangeDetectionTest extends AbstractGosuPluginSpecification {

    File srcMainGosu, A, B

    /**
     * super#setup is invoked automatically
     * @return
     */
    def setup() {
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
        A = new File(srcMainGosu, 'A.gs')
        B = new File(srcMainGosu, 'B.gs')
    }
    
    def 'A references B; will A be recompiled if it does not change, but B\'s API does? [Gradle #gradleVersion]'() {
        given:
        buildScript << getBasicBuildScriptForTesting()
        
        A << """
             class A {
               static var whatIsB : String = B.abc
             }
             """
        
        B << """
             class B  {
               static property get abc() : String {
                 return "something"
               }
             }
             """
        
        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('clean', 'compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()
        
        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':compileGosu').outcome == SUCCESS
        String buildOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')
        new File(buildOutput).exists()
        new File(buildOutput, 'A.class').exists()
        new File(buildOutput, 'B.class').exists()
        
        println('Done with first pass')
        
        and: // modify B in a way that invalidates A
        B.setText('') // truncates the file
        B << """
             class B  {
               static property get xyz() : String { //changed the public API!
                 return "something"
               }
             }
             """
        
        when:
        println('B is now:')
        println(B.getText())
        runner.withArguments('compileGosu', '-d') // intentionally use debug logging

        result = runner.buildAndFail()
        
        then:
        notThrown(UnexpectedBuildSuccess)
        result.task(':compileGosu').outcome == FAILED
        result.output.matches(skipUpToDateTaskExecuterExpectedOutput(gradleVersion))
        result.output.contains('/src/main/gosu/B.gs has changed.')
        result.output.contains('[3,46] error: No static property descriptor found for property, abc, on class, Type<B>')
        
        where:
        gradleVersion << gradleVersionsToTest
    }

    /**
     * Pins {@code @PathSensitive(RELATIVE)} on {@code GosuCompile.getStableSources()}.
     *
     * <p>A Gosu type's FQCN is its path relative to the source root, so a file's directory is
     * semantically significant input.  Under {@code NAME_ONLY} every source normalises to its bare
     * filename, and a move that leaves the bytes untouched produces an identical fingerprint --
     * the task stays UP-TO-DATE and the type is never compiled under its new name.
     *
     * <p>A {@code .gst} template is the vehicle because it carries no in-file {@code package}
     * declaration: unlike a {@code .gs}/{@code .gr} class, whose declaration must agree with its
     * directory and therefore changes content whenever the file moves, a template can be relocated
     * byte-for-byte.  That isolates path sensitivity from content hashing, which is the whole point
     * -- with {@code NAME_ONLY} this test fails at the "is not UP-TO-DATE" assertion.
     */
    def 'moving a template between packages is not UP-TO-DATE [Gradle #gradleVersion]'() {
        given: 'a template whose type name derives solely from its directory'
        buildScript << getBasicBuildScriptForTesting()

        // A .gs class is needed alongside it only so the source set is never empty mid-move.
        A << """
             class A {
               static function id() : String {
                 return "a"
               }
             }
             """

        File oldPackage = new File(srcMainGosu, 'com/example')
        oldPackage.mkdirs()
        File template = new File(oldPackage, 'Greeting.gst')
        String templateContent = 'Hello, world\n'
        template.text = templateContent

        when: 'initial compilation'
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()
        String buildOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')

        then: 'the template compiles under com.example'
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'com/example/Greeting.class').exists()

        when: 'nothing changes'
        result = runner.build()

        then: 'sanity check -- the task really is up to date, so the next assertion means something'
        result.task(':compileGosu').outcome == UP_TO_DATE

        when: 'the template moves to another package, byte-for-byte identical'
        assert template.delete()
        File newPackage = new File(srcMainGosu, 'com/other')
        newPackage.mkdirs()
        File movedTemplate = new File(newPackage, 'Greeting.gst')
        movedTemplate.text = templateContent

        result = runner.build()

        then: 'the move is visible to Gradle -- under @PathSensitive(NAME_ONLY) this is UP-TO-DATE'
        result.task(':compileGosu').outcome == SUCCESS

        and: 'the template is compiled under its new package'
        new File(buildOutput, 'com/other/Greeting.class').exists()

        where:
        gradleVersion << gradleVersionsToTest
    }

    Closure<Pattern> skipUpToDateTaskExecuterExpectedOutput = { String gradleVersion ->
        String regex = '.*Executing task \':compileGosu\'.*'
        if(VersionNumber.parse(gradleVersion) >= VersionNumber.parse('4.5')) {
            regex = '.*Task \':compileGosu\' is not up-to-date because:.*'
        } else if(VersionNumber.parse(gradleVersion) >= VersionNumber.parse('4.3')) {
            regex = '.*Up-to-date check for task \':compileGosu\' took \\d+.\\d+ secs. It is not up-to-date because:.*'
        }
        return Pattern.compile(regex, Pattern.DOTALL)
    }

}
