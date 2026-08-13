package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

@Unroll
class JavaInterfaceGosuImplementationTest extends AbstractGosuPluginSpecification {
    private static final long SLEEP_MS = 200;
    File srcMainJava, srcMainGosu, javaInterface, gosuImplementation, unrelatedClass
    File dependencyFile

    /**
     * super#setup is invoked automatically
     */
    def setup() {
        srcMainJava = testProjectDir.newFolder('src', 'main', 'java')
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')

        File javaPackageDir = new File(srcMainJava, 'com/example')
        javaPackageDir.mkdirs()
        javaInterface = new File(javaPackageDir, 'MyInterface.java')

        File gosuPackageDir = new File(srcMainGosu, 'com/example')
        gosuPackageDir.mkdirs()
        gosuImplementation = new File(gosuPackageDir, 'MyImpl.gs')
        unrelatedClass = new File(gosuPackageDir, 'UnrelatedClass.gs')

        dependencyFile = new File(testProjectDir.root, 'build/tmp/gosuc-deps-compileGosu.json')
    }

    def 'Gosu class recompiles when Java interface changes [Gradle #gradleVersion]'() {
        given:
        buildScript << """
            plugins {
                id 'org.gosu-lang.gosu'
            }
            repositories {
                mavenLocal()
                mavenCentral()
                maven {
                    url 'https://central.sonatype.com/repository/maven-snapshots/' //for Gosu snapshot builds
                }
            }
            dependencies {
                implementation group: 'org.gosu-lang.gosu', name: 'gosu-core-api', version: '$gosuVersion'
            }

            compileGosu {
                gosuOptions.incrementalCompilation = true
                gosuOptions.verbose = true
            }
            """

        javaInterface << """
            package com.example;

            public interface MyInterface {
                String fetchData();
            }
            """

        gosuImplementation << """
            package com.example

            class MyImpl implements MyInterface {
                override function fetchData() : String {
                    return "test"
                }
            }
            """

        unrelatedClass << """
            package com.example

            class UnrelatedClass {
                static function doSomething() : String {
                    return "unrelated"
                }
            }
            """

        when: 'Initial compilation'
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('clean', 'compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()

        then: 'All classes are compiled'
        result.task(':compileGosu').outcome == SUCCESS
        String gosuOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + ['main', 'com', 'example'])
        File implClassFile = new File(gosuOutput, 'MyImpl.class')
        File unrelatedClassFile = new File(gosuOutput, 'UnrelatedClass.class')
        implClassFile.exists()
        unrelatedClassFile.exists()
        dependencyFile.exists()

        when: 'Modify Java interface by adding a method'
        // Record initial modification times
        long implTimeBefore = implClassFile.lastModified()
        long unrelatedTimeBefore = unrelatedClassFile.lastModified()

        Thread.sleep(SLEEP_MS) // Ensure timestamp difference

        // Add a new method to the interface
        javaInterface.text = """
            package com.example;

            public interface MyInterface {
                String fetchData();
                String fetchOtherData();  // New method
            }
            """

        // Recreate runner for second build
        runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        result = runner.buildAndFail()

        then: 'Build fails because MyImpl does not implement the new method'
        result.task(':compileGosu').outcome == FAILED
        result.output.contains('function not implemented: fetchOtherData')

        and: 'Java interface change was detected and triggered selective recompilation'
        // Selective recompilation: Only MyImpl is recompiled (not UnrelatedClass)
        // because javaClassesDir is tracked separately with @Incremental
        result.output.contains('Java type changed: com.example.MyInterface')
        result.output.contains('Incremental compilation: recompiling')

        and: 'UnrelatedClass was NOT recompiled (selective recompilation works)'
        unrelatedClassFile.exists()
        unrelatedClassFile.lastModified() == unrelatedTimeBefore

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'ABI-only changes: Java implementation changes do not trigger Gosu recompilation [Gradle #gradleVersion]'() {
        given:
        buildScript << """
            plugins {
                id 'org.gosu-lang.gosu'
            }
            repositories {
                mavenLocal()
                mavenCentral()
                maven {
                    url 'https://central.sonatype.com/repository/maven-snapshots/' //for Gosu snapshot builds
                }
            }
            dependencies {
                implementation group: 'org.gosu-lang.gosu', name: 'gosu-core-api', version: '$gosuVersion'
            }

            compileGosu {
                gosuOptions.incrementalCompilation = true
                gosuOptions.verbose = true
            }
            """

        File javaBaseClass = new File(new File(srcMainJava, 'com/example'), 'BaseClass.java')
        javaBaseClass.parentFile.mkdirs()
        javaBaseClass << """
            package com.example;

            public class BaseClass {
                public String getMessage() {
                    return "original";
                }
            }
            """

        File gosuSubclass = new File(new File(srcMainGosu, 'com/example'), 'SubClass.gs')
        gosuSubclass.parentFile.mkdirs()
        gosuSubclass << """
            package com.example

            class SubClass extends BaseClass {
                function getFullMessage() : String {
                    return "Subclass: " + getMessage()
                }
            }
            """

        when: 'Initial compilation'
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('clean', 'compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()

        then: 'All classes are compiled'
        result.task(':compileGosu').outcome == SUCCESS
        String gosuOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + ['main', 'com', 'example'])
        File subClassFile = new File(gosuOutput, 'SubClass.class')
        subClassFile.exists()

        when: 'Change Java method IMPLEMENTATION only (not signature)'
        long subClassTimeBefore = subClassFile.lastModified()

        Thread.sleep(SLEEP_MS) // Ensure timestamp difference

        // Change implementation but not API
        javaBaseClass.text = """
            package com.example;

            public class BaseClass {
                public String getMessage() {
                    return "modified implementation";  // Changed implementation
                }
            }
            """

        // Recreate runner for second build
        runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        result = runner.build()

        then: 'Gosu subclass was NOT recompiled (ABI unchanged)'
        // Task should be UP_TO_DATE because @CompileClasspath provides ABI-level sensitivity
        // Implementation-only changes don't affect the ABI, so Gradle doesn't need to run the task
        result.task(':compileGosu').outcome == UP_TO_DATE
        // Note: This validates that @CompileClasspath on javaClassesDir provides ABI-level sensitivity
        subClassFile.lastModified() == subClassTimeBefore

        where:
        gradleVersion << gradleVersionsToTest
    }
}
