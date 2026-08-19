package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Tests how the Gosu plugin reacts to ABI changes in an external JAR.
 *
 * Two-layer model used by this plugin for "did some dependency change?":
 *
 *   1. Within-project Java types (in this task's javaClassesDir output)
 *      are tracked in the dep graph. A change there propagates to Gosu
 *      consumers via the gosuc BFS, recompiling only the affected sources.
 *
 *   2. External JAR / cross-subproject classes are NOT tracked in the dep
 *      graph. They live in compileClasspath, which the plugin annotates
 *      @CompileClasspath (ABI-sensitive, not @Incremental). When the
 *      classpath's ABI fingerprint changes, Gradle cannot supply per-file
 *      changes, so it deletes the task's declared outputs -- the dep file
 *      among them -- and gosuc reads that absence as "compile everything".
 *      Every Gosu source is recompiled, regardless of whether it actually
 *      used anything from the JAR.
 *
 * This split is intentional: tracking external-JAR types in the dep graph
 * would be dead weight (the FQCNs from a JAR never appear in changedTypes
 * because changedTypes is sourced from javaClassesDir only), so the BFS
 * could never query those edges. The two-layer split also matches what
 * Gradle's Java incremental compiler does -- per-class dep tracking
 * within a subproject, ABI fingerprinting across subprojects.
 */
@Unroll
class JarLevelGranularityTest extends AbstractGosuPluginSpecification {
    private static final long SLEEP_MS = 200;
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

    def 'JAR ABI change triggers full compileGosu rebuild via @CompileClasspath, not dep-graph cascade [Gradle #gradleVersion]'() {
        given: 'A build script with a file dependency on a JAR'
        // Standard incremental setup, plus a file-system JAR dependency that
        // this test will swap to simulate an ABI change.
        buildScript << getIncrementalBuildScriptForTesting()
        buildScript << """
            dependencies {
                implementation files('lib/my-library.jar')
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

        and: 'A Gosu class that uses the JAR class, plus a second unrelated Gosu class'
        gosuClass << '''
            package com.example

            uses com.example.LibraryClass

            class Consumer {
                static function getLibraryMessage() : String {
                    return LibraryClass.getMessage()
                }
            }
        '''

        // Unrelated has no reference to LibraryClass or to the JAR at all.
        // Under a hypothetical "selective recompile via dep graph" model it
        // would NOT be touched by a JAR ABI change. Under the real model
        // (full task rebuild driven by @CompileClasspath fingerprint change)
        // it IS recompiled alongside Consumer -- this test pins that
        // behavior so the two-layer model stays explicit.
        File unrelatedClass = new File(srcMainGosu, 'com/example/Unrelated.gs')
        unrelatedClass << '''
            package com.example

            class Unrelated {
                static function id() : String {
                    return "unrelated"
                }
            }
        '''

        when: 'Initial build'
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('clean', 'compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()

        then: 'Build succeeds'
        result.task(':compileGosu').outcome == SUCCESS
        dependencyFile.exists()

        and: 'Dependency file omits external JAR types (the dep graph only tracks project-local Java types)'
        String actualJson = dependencyFile.text

        // The dep graph records edges only between Gosu types and Java types
        // that live in this project's javaClassesDir. com.example.LibraryClass
        // is from an external JAR (lib/my-library.jar), so it does NOT appear
        // as a producer here. IncrementalCompilationManager.shouldTrackJavaType
        // filters such types out -- recording them would be dead weight because
        // their FQCNs never enter changedTypes (the plugin sources changedTypes
        // from javaClassesDir only).
        //
        // Both Gosu types ARE present in the dep graph (registered via
        // ensureTypeRegistered during their own compile), each with an empty
        // consumer list because nothing local depends on them.
        String expectedJson = """
  "consumers": {
    "com.example.Consumer": [],
    "com.example.Unrelated": []
  }
}"""

        actualJson.contains(expectedJson)

        when: 'Capture timestamps for both Gosu outputs'
        // Use expectedOutputDir() helper rather than hardcoding 'build/classes/gosu/main',
        // so the path stays correct across Gradle versions (older versions used a
        // different output-directory layout).
        String gosuOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')
        File consumerClassFile = new File(gosuOutput, 'com/example/Consumer.class')
        File unrelatedClassFile = new File(gosuOutput, 'com/example/Unrelated.class')
        long consumerTimeBefore = consumerClassFile.lastModified()
        long unrelatedTimeBefore = unrelatedClassFile.lastModified()

        // Wait to ensure file timestamps will differ
        Thread.sleep(SLEEP_MS)

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

        then: 'compileGosu re-executes and succeeds'
        result.task(':compileGosu').outcome == SUCCESS

        and: 'Consumer was recompiled (it references the changed JAR class)'
        consumerClassFile.lastModified() > consumerTimeBefore

        and: 'Unrelated was ALSO recompiled, even though it has no JAR reference -- the cross-classpath path is full task rebuild, not selective cascade'
        // This is the deliberate consequence of the @CompileClasspath +
        // not-@Incremental annotation on getClasspath(): when the JAR ABI
        // fingerprint changes, Gradle cannot supply per-file changes, wipes
        // the declared outputs including the dep file, and gosuc recompiles
        // every Gosu source. If anyone in the future tries to make this
        // selective via the dep graph for external JAR types, this
        // assertion will fail and the failure should be a deliberate
        // architectural decision, not an accidental one.
        unrelatedClassFile.lastModified() > unrelatedTimeBefore

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
