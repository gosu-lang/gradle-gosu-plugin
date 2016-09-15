package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.gradle.testkit.runner.UnexpectedBuildSuccess
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.FAILED

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
        String buildOutput = asPath(testProjectDir.root.absolutePath, 'build', 'classes', 'main')
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
        result.output.contains('Executing task \':compileGosu\'')
        result.output.contains('/src/main/gosu/B.gs has changed.')
        result.output.contains('src/main/gosu/A.gs:[3,46] error: No static property descriptor found for property, abc, on class, Type<B>')
        
        where:
        gradleVersion << gradleVersionsToTest
    }

}
