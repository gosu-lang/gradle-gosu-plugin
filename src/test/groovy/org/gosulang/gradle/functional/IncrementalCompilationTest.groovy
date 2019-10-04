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

    File srcMainGosu, srcMainJava, javaOutput, gosuOutput, A, B, C, J
    long cModTime

    /**
     * super#setup is invoked automatically
     * @return
     */
    def setup() {
        srcMainJava = testProjectDir.newFolder('src', 'main', 'java')
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
        A = new File(srcMainGosu, 'A.gs')
        B = new File(srcMainGosu, 'B.gs')
        C = new File(srcMainGosu, 'C.gs')
        J = new File(srcMainJava, "J.java")
        gosuOutput = new File(asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion).collect() + 'main'))
        javaOutput = new File(gosuOutput.getPath().replace("gosu/main", "java/main"))
    }
    
    def 'Will Gosu be recompiled if it does not change, but Java class\'s API does? [Gradle #gradleVersion]'() {
        given:
        buildScript << getBasicBuildScriptForTesting()
        
        A << """
             class A {
               static var whatIsB : String = J.Abc
             }
             """
        
        J << """
             public class J  {
               public static String getAbc() {
                 return "something";
               }
             }
             """
        
        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('clean', 'compileJava', '-i', 'compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()

        then:
        result.task(':compileJava').outcome == SUCCESS
        result.task(':compileGosu').outcome == SUCCESS
        gosuOutput.exists()
        new File(javaOutput, 'J.class').exists()
        new File(gosuOutput, 'A.class').exists()
        !new File(gosuOutput, 'B.class').exists()
        !new File(gosuOutput, 'C.class').exists()

        println('Done with first pass')
        
        and: // modify J in a way that invalidates A
        J.setText('') // truncates the file
        J << """
             public class J  {
               public String getXyz() {
                 return "something";
               }
             }
             """

        when:
        println('J is now:')
        println(J.getText())
        runner.withArguments('compileJava', 'compileGosu', '-d') // intentionally use debug logging

        result = runner.buildAndFail()
        
        then:
        notThrown(UnexpectedBuildSuccess)
        result.task(':compileGosu').outcome == FAILED
        result.output.matches(skipUpToDateTaskExecuterExpectedOutput(gradleVersion))
        result.output.contains('/src/main/java/J.java has changed.')
        result.output.contains('[3,46] error: No static property descriptor found for property, Abc, on class, Type<J>')
        
        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'Will Gosu be recompiled if it does not depend on java, but Java changes? [Gradle #gradleVersion]'() {
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

        J << """
             public class J  {
               public static String getAbc() {
                 return "something";
               }
             }
             """

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('clean', 'compileJava', 'compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()

        then:
        result.task(':compileJava').outcome == SUCCESS
        result.task(':compileGosu').outcome == SUCCESS
        gosuOutput.exists()
        new File(javaOutput, 'J.class').exists()
        new File(gosuOutput, 'A.class').exists()
        new File(gosuOutput, 'B.class').exists()
        !new File(gosuOutput, 'C.class').exists()

        println('Done with first pass')

        and: // modify J
        J.setText('') // truncates the file
        J << """
             public class J  {
               public String getXyz() {
                 return "something";
               }
             }
             """

        when:
        println('J is now:')
        println(J.getText())
        runner.withArguments('compileJava',  'compileGosu', '-d') // intentionally use debug logging

        result = runner.build()

        then:
        result.task(':compileJava').outcome == SUCCESS
        result.task(':compileGosu').outcome == SUCCESS
        result.output.matches(skipUpToDateTaskExecuterExpectedOutput(gradleVersion))
        result.output.contains('/src/main/java/J.java has changed.')
        new File(gosuOutput, 'A.class').lastModified() < new File(javaOutput, 'J.class').lastModified()
        new File(gosuOutput, 'B.class').lastModified() < new File(javaOutput, 'J.class').lastModified()

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'Will A be recompiled if it does not change, but B\'s API does? [Gradle #gradleVersion]'() {
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
        gosuOutput.exists()
        new File(gosuOutput, 'A.class').exists()
        new File(gosuOutput, 'B.class').exists()
        !new File(gosuOutput, 'C.class').exists()

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

    def 'Will A or C be recompiled if they do not change, but B does? [Gradle #gradleVersion]'() {
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

        C << """
             class C {
               static property get efg() : String {
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
        gosuOutput.exists()
        new File(gosuOutput, 'A.class').exists()
        new File(gosuOutput, 'B.class').exists()
        new File(gosuOutput, 'C.class').exists()

        println('Done with first pass')

        and: // modify B in a way that does not invalidate A, and does not affect C

        B.setText('') // truncates the file
        B << """
             class B  {
               static property get abc() : String {
                 return "something"
               }
               private static property get xyz() : String { // added to the public API
                 return "something else"
               }
             }
             """

        when:
        println('B is now:')
        println(B.getText())
        cModTime = new File(gosuOutput, 'C.class').lastModified()

        runner.withArguments('compileGosu', '-d') // intentionally use debug logging

        result = runner.build()

        then:
        result.task(':compileGosu').outcome == SUCCESS
        result.output.matches(skipUpToDateTaskExecuterExpectedOutput(gradleVersion))
        result.output.contains('/src/main/gosu/B.gs has changed.')
        !result.output.contains('/src/main/gosu/A.gs has changed.')
        !result.output.contains('/src/main/gosu/C.gs has changed.')
        // This passes, but should not
        // result.output.contains('Recompiled classes [B, A]') || result.output.contains('Recompiled classes [A, B]')
        // This fails, but should not
        result.output.contains("Recompiled classes [C]")
        new File(gosuOutput, 'B.class').lastModified() > new File(gosuOutput, 'C.class').lastModified()
        new File(gosuOutput, 'A.class').lastModified() > new File(gosuOutput, 'C.class').lastModified()
        new File(gosuOutput, 'C.class').lastModified() == cModTime

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'Will A or B be recompiled if they do not change, but C does? [Gradle #gradleVersion]'() {
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

        C << """
             class C {
               static property get efg() : String {
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
        gosuOutput.exists()
        new File(gosuOutput, 'A.class').exists()
        new File(gosuOutput, 'B.class').exists()
        new File(gosuOutput, 'C.class').exists()

        println('Done with first pass')

        and: // modify C

        C.setText('') // truncates the file
        C << """
             class C  {
               static property get efg() : String {
                 return "something"
               }
               static property get xyz() : String { // added to the public API
                 return "something else"
               }
             }
             """

        when:
        println('C is now:')
        println(C.getText())
        cModTime = new File(gosuOutput, 'C.class').lastModified()

        runner.withArguments('compileGosu', '-d') // intentionally use debug logging

        result = runner.build()

        then:
        result.task(':compileGosu').outcome == SUCCESS
        result.output.matches(skipUpToDateTaskExecuterExpectedOutput(gradleVersion))
        result.output.contains('/src/main/gosu/C.gs has changed.')
        !result.output.contains('/src/main/gosu/A.gs has changed.')
        !result.output.contains('/src/main/gosu/B.gs has changed.')
        result.output.contains('Recompiled classes [C]')
        new File(gosuOutput, 'B.class').lastModified() < new File(gosuOutput, 'C.class').lastModified()
        new File(gosuOutput, 'A.class').lastModified() < new File(gosuOutput, 'C.class').lastModified()
        new File(gosuOutput, 'C.class').lastModified() > cModTime

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'Will changing a constant cause all files to be recompiled? [Gradle #gradleVersion]'() {
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

        C << """
             class C {
               public static final var MY_STRING : String = "My String"
               
               static property get efg() : String {
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
        gosuOutput.exists()
        new File(gosuOutput, 'A.class').exists()
        new File(gosuOutput, 'B.class').exists()
        new File(gosuOutput, 'C.class').exists()

        println('Done with first pass')

        and: // modify C

        C.setText('') // truncates the file
        C << """
             class C  {
               public static final var MY_STRING : String = "My New String"
               
               static property get efg() : String {
                 return "something"
               }
             }
             """

        when:
        println('C is now:')
        println(C.getText())
        cModTime = new File(gosuOutput, 'C.class').lastModified()

        runner.withArguments('compileGosu', '-d') // intentionally use debug logging

        result = runner.build()

        then:
        result.task(':compileGosu').outcome == SUCCESS
        result.output.matches(skipUpToDateTaskExecuterExpectedOutput(gradleVersion))
        result.output.contains('/src/main/gosu/C.gs has changed.')
        !result.output.contains('/src/main/gosu/A.gs has changed.')
        !result.output.contains('/src/main/gosu/B.gs has changed.')
        // full recompile NOT required, because Gosu does not inline compile-time constants
        result.output.contains('Recompiled classes [C]')

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'Will need to be recompiled propagate? [Gradle #gradleVersion]'() {
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
                 return C.MY_STRING
               }
             }
             """

        C << """
             class C {
               public static final var MY_STRING : String = "My String"
               
               static property get efg() : String {
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
        gosuOutput.exists()
        new File(gosuOutput, 'A.class').exists()
        new File(gosuOutput, 'B.class').exists()
        new File(gosuOutput, 'C.class').exists()

        println('Done with first pass')

        and: // modify C

        C.setText('') // truncates the file
        C << """
             class C  {
               public static final var MY_STRING : String = "My New String"
               
               static property get efg() : String {
                 return "something"
               }
             }
             """

        when:
        println('C is now:')
        println(C.getText())
        cModTime = new File(gosuOutput, 'C.class').lastModified()

        runner.withArguments('compileGosu', '-d') // intentionally use debug logging

        result = runner.build()

        then:
        result.task(':compileGosu').outcome == SUCCESS
        result.output.matches(skipUpToDateTaskExecuterExpectedOutput(gradleVersion))
        result.output.contains('/src/main/gosu/C.gs has changed.')
        !result.output.contains('/src/main/gosu/A.gs has changed.')
        !result.output.contains('/src/main/gosu/B.gs has changed.')
        result.output.contains('Recompiled classes [C, B, A]') || result.output.contains('Recompiled classes [C, A, B]')

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
