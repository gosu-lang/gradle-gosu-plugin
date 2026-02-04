package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Tests JAR dependency tracking with ABI-level change detection.
 * Gradle uses @CompileClasspath which detects ABI changes in JARs (not implementation changes).
 * The dependency graph tracks which Gosu types use which JAR classes for selective recompilation.
 */
@Unroll
class JarLevelGranularityTest extends AbstractGosuPluginSpecification {

    File srcMainGosu, libDir, myJar
    File gosuClass
    File dependencyFile

    def setup() {
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
        libDir = testProjectDir.newFolder('lib')

        File gosuPackageDir = new File(srcMainGosu, 'com/example')
        gosuPackageDir.mkdirs()
        gosuClass = new File(gosuPackageDir, 'Consumer.gs')

        myJar = new File(libDir, 'my-library.jar')
        dependencyFile = new File(testProjectDir.root, 'build/tmp/gosuc-deps-compileGosu.json')
    }

    def 'JAR ABI changes trigger selective recompilation based on dependency graph [Gradle #gradleVersion]'() {
        given: 'A build script with a file dependency on a JAR'
        buildScript << """
            plugins {
                id 'org.gosu-lang.gosu'
            }
            repositories {
                mavenLocal()
                mavenCentral()
                maven {
                    url 'https://central.sonatype.com/repository/maven-snapshots/'
                }
            }
            dependencies {
                implementation group: 'org.gosu-lang.gosu', name: 'gosu-core-api', version: '$gosuVersion'
                implementation files('lib/my-library.jar')
            }

            compileGosu {
                gosuOptions.incrementalCompilation = true
            }
        """

        and: 'Create a JAR with a simple Java class'
        createJarWithClass(myJar, 'com/example/LibraryClass', '''
            package com.example;
            public class LibraryClass {
                public static String getMessage() {
                    return "v1";
                }
            }
        ''')

        and: 'A Gosu class that uses the JAR class'
        gosuClass << '''
            package com.example

            uses com.example.LibraryClass

            class Consumer {
                static function getLibraryMessage() : String {
                    return LibraryClass.getMessage()
                }
            }
        '''

        when: 'Initial build'
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('clean', 'compileGosu', '-i')
                .withGradleVersion(gradleVersion)

        BuildResult result = runner.build()

        then: 'Build succeeds'
        result.task(':compileGosu').outcome == SUCCESS
        dependencyFile.exists()

        and: 'Dependency file tracks JAR class usage (enables selective recompilation)'
        String actualJson = dependencyFile.text

        // Expected: Consumer.gs depends on LibraryClass from the JAR
        // This dependency tracking enables selective recompilation when JARs change
        // Note: All compiled types are registered (even with empty arrays)
        String expectedJson = """{
  "version": "1.0",
  "consumers": {
    "com.example.Consumer": [],
    "com.example.LibraryClass": [
      "com.example.Consumer"
    ]
  }
}"""

        actualJson == expectedJson

        when: 'Capture timestamps'
        File consumerClassFile = new File(testProjectDir.root, 'build/classes/gosu/main/com/example/Consumer.class')
        long consumerTimeBefore = consumerClassFile.lastModified()

        // Wait to ensure file timestamp will differ
        Thread.sleep(1000)

        and: 'Replace the JAR with a modified version (ABI change: add new method)'
        createJarWithClass(myJar, 'com/example/LibraryClass', '''
            package com.example;
            public class LibraryClass {
                public static String getMessage() {
                    return "v2";
                }
                public static String getNewMessage() {
                    return "new";
                }
            }
        ''')

        and: 'Rebuild'
        runner.withArguments('compileGosu', '-i')
        result = runner.build()

        then: 'compileGosu detects the JAR change and rebuilds'
        result.task(':compileGosu').outcome == SUCCESS

        and: 'The Gosu class was recompiled'
        consumerClassFile.lastModified() > consumerTimeBefore

        where:
        gradleVersion << gradleVersionsToTest
    }

    /**
     * Helper method to create a JAR file containing a compiled Java class
     */
    private void createJarWithClass(File jarFile, String classPath, String javaSource) {
        // Create a temporary directory for compilation
        File tempDir = File.createTempFile("jar-build", "")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            // Write the Java source file
            File javaFile = new File(tempDir, classPath.replace('/', File.separator) + ".java")
            javaFile.parentFile.mkdirs()
            javaFile.text = javaSource

            // Compile the Java source
            def javac = javax.tools.ToolProvider.getSystemJavaCompiler()
            def fileManager = javac.getStandardFileManager(null, null, null)
            def compilationUnits = fileManager.getJavaFileObjectsFromFiles([javaFile])
            def task = javac.getTask(null, fileManager, null, null, null, compilationUnits)
            if (!task.call()) {
                throw new RuntimeException("Failed to compile Java source for JAR: $javaFile")
            }

            // Create the JAR
            jarFile.parentFile.mkdirs()
            jarFile.withOutputStream { os ->
                def jos = new JarOutputStream(os)

                // Add the compiled .class file
                File classFile = new File(tempDir, classPath.replace('/', File.separator) + ".class")
                jos.putNextEntry(new JarEntry(classPath + ".class"))
                jos << classFile.bytes
                jos.closeEntry()

                jos.close()
            }
        } finally {
            tempDir.deleteDir()
        }
    }
}
