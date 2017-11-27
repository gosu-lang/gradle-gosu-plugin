package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.gradle.testkit.runner.UnexpectedBuildSuccess
import org.gradle.util.VersionNumber
import spock.lang.Unroll

import java.util.regex.Pattern

import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Unroll
class IncrementalCompilationTest extends AbstractGosuPluginSpecification {

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

    Closure<Pattern> skipUpToDateTaskExecuterExpectedOutput = { String gradleVersion ->
        String regex = '.*Executing task \':compileGosu\'.*'
        if(VersionNumber.parse(gradleVersion) >= VersionNumber.parse('4.3')) {
            regex = '.*Up-to-date check for task \':compileGosu\' took \\d+.\\d+ secs. It is not up-to-date because:.*'
        }
        return Pattern.compile(regex, Pattern.DOTALL)
    }

}
