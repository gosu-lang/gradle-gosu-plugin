package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

@Unroll
class IncrementalCompilationWithDependencyTrackingTest extends AbstractGosuPluginSpecification {

    File srcMainGosu, baseClass, derivedClass, independentClass
    File dependencyFile

    /**
     * Isolated Gradle user-home for tests that exercise the build cache, so
     * cache entries created here don't leak into other tests or other runs.
     * Mirrors the pattern in LocalBuildCacheTest.
     */
    @Rule
    TemporaryFolder testKitDir = new TemporaryFolder()

    /**
     * super#setup is invoked automatically
     * @return
     */
    def setup() {
        testKitDir.create()
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
        baseClass = new File(srcMainGosu, 'BaseClass.gs')
        derivedClass = new File(srcMainGosu, 'DerivedClass.gs')
        independentClass = new File(srcMainGosu, 'IndependentClass.gs')
        dependencyFile = new File(testProjectDir.root, 'build/tmp/gosuc-deps-compileGosu.json')
    }
    
    def 'Incremental compilation with dependency tracking [Gradle #gradleVersion]'() {
        given:
        buildScript << getIncrementalBuildScriptForTesting()
        
        baseClass << """
            class BaseClass {
                static var value : int = 42
                
                static function getValue() : int {
                    return value
                }
            }
            """
        
        derivedClass << """
            class DerivedClass extends BaseClass {
                static function getDoubleValue() : int {
                    return getValue() * 2
                }
            }
            """
        
        independentClass << """
            class IndependentClass {
                static function getConstant() : int {
                    return 100
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
        String buildOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')
        new File(buildOutput, 'BaseClass.class').exists()
        new File(buildOutput, 'DerivedClass.class').exists()
        new File(buildOutput, 'IndependentClass.class').exists()
        dependencyFile.exists()
        
        when: 'Modify BaseClass (which DerivedClass depends on)'
        // Record initial modification times
        long derivedClassTime = new File(buildOutput, 'DerivedClass.class').lastModified()
        long independentClassTime = new File(buildOutput, 'IndependentClass.class').lastModified()
        
        Thread.sleep(1100) // Ensure timestamp difference
        
        baseClass.setText('') // truncate
        baseClass << """
            class BaseClass {
                static var value : int = 42
                
                static function getValue() : int {
                    return value
                }
                
                static function getTripleValue() : int {
                    return value * 3
                }
            }
            """
        
        runner.withArguments('compileGosu', '-i')
        result = runner.build()
        
        then: 'Only BaseClass and DerivedClass are recompiled'
        result.task(':compileGosu').outcome == SUCCESS
        
        // BaseClass should be recompiled (newer timestamp)
        new File(buildOutput, 'BaseClass.class').lastModified() > derivedClassTime
        
        // DerivedClass should be recompiled due to dependency
        new File(buildOutput, 'DerivedClass.class').lastModified() > derivedClassTime
        
        // IndependentClass should NOT be recompiled (same timestamp)
        new File(buildOutput, 'IndependentClass.class').lastModified() == independentClassTime
        
        when: 'Delete a file'
        derivedClass.delete()

        runner.withArguments('compileGosu', '-i')
        result = runner.build()

        then: 'Build succeeds and stale class file is deleted'
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'BaseClass.class').exists()
        new File(buildOutput, 'IndependentClass.class').exists()

        and: 'DerivedClass.class is deleted (no stale class files)'
        !new File(buildOutput, 'DerivedClass.class').exists()
        
        where:
        gradleVersion << gradleVersionsToTest
    }
    
    def 'Incremental compilation disabled by default [Gradle #gradleVersion]'() {
        given:
        buildScript << getBasicBuildScriptForTesting()
        
        baseClass << """
            class BaseClass {
                protected static var value : int = 42
            }
            """
        
        derivedClass << """
            class DerivedClass extends BaseClass {
                static function getDoubleValue() : int {
                    return BaseClass.value * 2
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
        
        then: 'Compilation succeeds but no dependency file is created'
        result.task(':compileGosu').outcome == SUCCESS
        !dependencyFile.exists()
        
        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'Transitive dependency chain does not stop after one hop [Gradle #gradleVersion]'() {
        given:
        buildScript << getIncrementalBuildScriptForTesting()

        // Chain: ClassA <- ClassB <- ClassC, every edge on the public API
        File classA = new File(srcMainGosu, 'ClassA.gs')
        File classB = new File(srcMainGosu, 'ClassB.gs')
        File classC = new File(srcMainGosu, 'ClassC.gs')

        classA << """
            class ClassA {
                static function value() : int {
                    return 1
                }
            }
            """

        classB << """
            class ClassB {
                // ClassB re-exposes ClassA's value on its public API
                static function transitive() : int {
                    return ClassA.value() + 10
                }
            }
            """

        classC << """
            class ClassC {
                static function entry() : int {
                    return ClassB.transitive() + 100
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
        String buildOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')

        then:
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'ClassA.class').exists()
        new File(buildOutput, 'ClassB.class').exists()
        new File(buildOutput, 'ClassC.class').exists()

        when: 'Modify ClassA (head of the chain)'
        long classATime = new File(buildOutput, 'ClassA.class').lastModified()
        long classBTime = new File(buildOutput, 'ClassB.class').lastModified()
        long classCTime = new File(buildOutput, 'ClassC.class').lastModified()
        Thread.sleep(1100)

        classA.setText('') // truncate
        classA << """
            class ClassA {
                static function value() : int {
                    return 2  // changed
                }
            }
            """

        runner.withArguments('compileGosu', '-i')
        result = runner.build()

        then: 'ClassA is recompiled'
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'ClassA.class').lastModified() > classATime

        and: 'ClassB is recompiled (direct consumer of ClassA)'
        new File(buildOutput, 'ClassB.class').lastModified() > classBTime

        and: 'ClassC is recompiled, as it transitively depends on ClassA'
        new File(buildOutput, 'ClassC.class').lastModified() > classCTime

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'Dependency file is restored from the Gradle build cache for incremental compilation [Gradle #gradleVersion]'() {
        given:
        buildScript << getIncrementalBuildScriptForTesting()

        // Layout: ClassA <- ClassB (B consumes A), plus an UnrelatedC that
        // has no edge to either. The test exercises a clean+cache-restore
        // round trip and then changes ClassA's ABI. If the dep file rode
        // through the cache, the post-restore compileGosu uses the restored
        // graph and recompiles only ClassA and ClassB - UnrelatedC's
        // .class file stays untouched. If the dep file is NOT restored, the
        // compiler has no baseline graph and falls back to a full rebuild,
        // which would also recompile UnrelatedC and fail the negative
        // assertion below. UnrelatedC is the load-bearing signal.
        File classA = new File(srcMainGosu, 'ClassA.gs')
        File classB = new File(srcMainGosu, 'ClassB.gs')
        File unrelatedC = new File(srcMainGosu, 'UnrelatedC.gs')

        classA << """
            class ClassA {
                static function value() : int {
                    return 1
                }
            }
            """

        classB << """
            class ClassB {
                static function transitive() : int {
                    return ClassA.value() + 10
                }
            }
            """

        unrelatedC << """
            class UnrelatedC {
                static function standalone() : int {
                    return 42
                }
            }
            """

        when: 'Initial compilation populates the local build cache'
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withTestKitDir(testKitDir.root)
                .withPluginClasspath()
                .withArguments('compileGosu', '--build-cache', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()
        String buildOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')

        then: 'All classes compile and the dep file is written'
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'ClassA.class').exists()
        new File(buildOutput, 'ClassB.class').exists()
        new File(buildOutput, 'UnrelatedC.class').exists()
        dependencyFile.exists()
        String depFileContentBeforeWipe = dependencyFile.text

        when: 'Wipe the build directory to simulate a fresh checkout, then restore via the build cache'
        runner.withArguments('clean', '--build-cache', '-i')
        runner.build()

        runner.withArguments('compileGosu', '--build-cache', '-i')
        result = runner.build()

        then: 'compileGosu is restored from the cache, including the dep file'
        result.task(':compileGosu').outcome == FROM_CACHE
        new File(buildOutput, 'ClassA.class').exists()
        new File(buildOutput, 'ClassB.class').exists()
        new File(buildOutput, 'UnrelatedC.class').exists()

        and: 'The dep file is restored with the same content it had before the wipe'
        dependencyFile.exists()
        dependencyFile.text == depFileContentBeforeWipe

        when: 'Change ClassA ABI so the next compileGosu drives an incremental recompile off the restored dep file'
        long classATime = new File(buildOutput, 'ClassA.class').lastModified()
        long classBTime = new File(buildOutput, 'ClassB.class').lastModified()
        long unrelatedCTime = new File(buildOutput, 'UnrelatedC.class').lastModified()
        Thread.sleep(1100)

        classA.setText('') // truncate
        classA << """
            class ClassA {
                static function value() : int {
                    return 1
                }
                static function value2() : int { // new method - ABI change
                    return 2
                }
            }
            """

        runner.withArguments('compileGosu', '--build-cache', '-i')
        result = runner.build()

        then: 'ClassA is recompiled'
        result.task(':compileGosu').outcome == SUCCESS
        new File(buildOutput, 'ClassA.class').lastModified() > classATime

        and: 'ClassB is recompiled (direct consumer, edge survived the cache round trip)'
        new File(buildOutput, 'ClassB.class').lastModified() > classBTime

        and: 'UnrelatedC is NOT recompiled - the cache-restored dep file correctly excludes it from the cascade'
        new File(buildOutput, 'UnrelatedC.class').lastModified() == unrelatedCTime

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'Deleting the dep file forces gosuc to re-run and regenerate it [Gradle #gradleVersion]'() {
        given:
        buildScript << getIncrementalBuildScriptForTesting()

        // Two classes with a real dep edge so the regenerated dep file has
        // something non-trivial to record (not just an empty consumers map).
        File classA = new File(srcMainGosu, 'ClassA.gs')
        File classB = new File(srcMainGosu, 'ClassB.gs')

        classA << """
            class ClassA {
                static function value() : int {
                    return 1
                }
            }
            """

        classB << """
            class ClassB {
                static function consume() : int {
                    return ClassA.value() + 10
                }
            }
            """

        when: 'Initial compilation writes the dep file'
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()
        String buildOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')

        then:
        result.task(':compileGosu').outcome == SUCCESS
        dependencyFile.exists()
        new File(buildOutput, 'ClassA.class').exists()
        new File(buildOutput, 'ClassB.class').exists()

        when: 'Re-running with nothing changed is UP-TO-DATE (sanity check)'
        runner.withArguments('compileGosu', '-i')
        result = runner.build()

        then:
        result.task(':compileGosu').outcome == UP_TO_DATE

        when: 'Manually delete the dep file, simulating a stray rm or IDE clean'
        long classATime = new File(buildOutput, 'ClassA.class').lastModified()
        long classBTime = new File(buildOutput, 'ClassB.class').lastModified()
        Thread.sleep(1100) // ensure observable mtime difference on coarse filesystems

        assert dependencyFile.delete()

        runner.withArguments('compileGosu', '-i')
        result = runner.build()

        then: 'Gradle detects the missing @OutputFile and re-runs the task (not UP_TO_DATE)'
        result.task(':compileGosu').outcome == SUCCESS

        and: 'The dep file is regenerated'
        dependencyFile.exists()

        and: 'ClassA.class was rewritten - full rebuild path'
        new File(buildOutput, 'ClassA.class').lastModified() > classATime

        and: 'ClassB.class was also rewritten - confirms every source went through gosuc, not just the ones with a known dep edge'
        new File(buildOutput, 'ClassB.class').lastModified() > classBTime

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'Annotation reference on class header is tracked as a dependency [Gradle #gradleVersion]'() {
        given:
        buildScript << getIncrementalBuildScriptForTesting()

        // Java annotation type co-located with the Gosu consumer
        File srcMainJavaPkg = new File(testProjectDir.root, 'src/main/java/com/example')
        srcMainJavaPkg.mkdirs()
        File annoFile = new File(srcMainJavaPkg, 'MyAnno.java')
        annoFile << """
            package com.example;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.TYPE)
            public @interface MyAnno {
                String tag() default "v1";
            }
            """

        // Gosu class annotated with @com.example.MyAnno - note: no `uses` import,
        // so the annotation type appears ONLY inside an annotation expression.
        File consumerFile = new File(srcMainGosu, 'Consumer.gs')
        consumerFile << """
            @com.example.MyAnno("v1")
            class Consumer {
                static function id() : String {
                    return "consumer"
                }
            }
            """

        when: 'Initial compilation (compileJava + compileGosu)'
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('clean', 'compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()

        String gosuOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')
        String javaOutput = asPath([testProjectDir.root.absolutePath, 'build', 'classes', 'java', 'main'])

        then:
        result.task(':compileGosu').outcome == SUCCESS
        new File(javaOutput, 'com/example/MyAnno.class').exists()
        new File(gosuOutput, 'Consumer.class').exists()

        when: 'Modify the annotation type - add a new attribute (ABI change)'
        long annoTime = new File(javaOutput, 'com/example/MyAnno.class').lastModified()
        long consumerTime = new File(gosuOutput, 'Consumer.class').lastModified()
        Thread.sleep(1100)

        annoFile.setText('')
        annoFile << """
            package com.example;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.TYPE)
            public @interface MyAnno {
                String tag() default "v1";
                String extra() default "added";   // new attribute - ABI change
            }
            """

        runner.withArguments('compileGosu', '-i')
        result = runner.build()

        then: 'MyAnno was recompiled by javac'
        new File(javaOutput, 'com/example/MyAnno.class').lastModified() > annoTime

        and: 'Consumer is recompiled as it carries @com.example.MyAnno'
        new File(gosuOutput, 'Consumer.class').lastModified() > consumerTime

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'Nested Java annotation type change is detected by javaClassesDir tracking [Gradle #gradleVersion]'() {
        given:
        buildScript << getIncrementalBuildScriptForTesting()

        // Java file containing a top-level class AND a nested annotation
        // type. Compilation produces TWO .class files:
        //   build/classes/java/main/com/example/Outer.class
        //   build/classes/java/main/com/example/Outer\$NestedAnno.class
        File srcMainJavaPkg = new File(testProjectDir.root, 'src/main/java/com/example')
        srcMainJavaPkg.mkdirs()
        File outerFile = new File(srcMainJavaPkg, 'Outer.java')
        outerFile << """
            package com.example;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            public class Outer {
                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.TYPE)
                public @interface NestedAnno {
                    String tag() default "v1";
                }
            }
            """

        File consumerFile = new File(srcMainGosu, 'Consumer.gs')
        consumerFile << """
            @com.example.Outer.NestedAnno("v1")
            class Consumer {
                static function id() : String {
                    return "consumer"
                }
            }
            """

        when: 'Initial compilation (compileJava emits both Outer and Outer\$NestedAnno)'
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('clean', 'compileGosu', '-i')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()

        String gosuOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')
        String javaOutput = asPath([testProjectDir.root.absolutePath, 'build', 'classes', 'java', 'main'])

        then: 'Both class files exist after the first compile'
        result.task(':compileGosu').outcome == SUCCESS
        new File(javaOutput, 'com/example/Outer.class').exists()
        new File(javaOutput, 'com/example/Outer$NestedAnno.class').exists()
        new File(gosuOutput, 'Consumer.class').exists()

        when: 'Modify the nested annotation - add a new attribute (ABI change)'
        // Editing the nested annotation rewrites BOTH Outer.class and
        // Outer$NestedAnno.class because javac recompiles the whole .java unit.
        long outerTime = new File(javaOutput, 'com/example/Outer.class').lastModified()
        long nestedAnnoTime = new File(javaOutput, 'com/example/Outer$NestedAnno.class').lastModified()
        long consumerTime = new File(gosuOutput, 'Consumer.class').lastModified()
        Thread.sleep(1100)

        outerFile.setText('')
        outerFile << """
            package com.example;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            public class Outer {
                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.TYPE)
                public @interface NestedAnno {
                    String tag() default "v1";
                    String extra() default "added";   // new attribute - ABI change
                }
            }
            """

        runner.withArguments('compileGosu', '-i')
        result = runner.build()

        then: 'javac re-emits BOTH Outer.class and Outer\$NestedAnno.class'
        result.task(':compileGosu').outcome == SUCCESS
        new File(javaOutput, 'com/example/Outer.class').lastModified() > outerTime
        new File(javaOutput, 'com/example/Outer$NestedAnno.class').lastModified() > nestedAnnoTime

        and: 'Consumer is recompiled as it carries @Outer.NestedAnno'
        new File(gosuOutput, 'Consumer.class').lastModified() > consumerTime

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'Nested Java class change is detected by javaClassesDir tracking [Gradle #gradleVersion]'() {
        given:
        buildScript << getIncrementalBuildScriptForTesting()

        File srcMainJavaPkg = new File(testProjectDir.root, 'src/main/java/com/example')
        srcMainJavaPkg.mkdirs()
        File outerFile = new File(srcMainJavaPkg, 'Outer.java')
        outerFile << """
            package com.example;

            public class Outer {
                public static class Inner {
                    public static String tag() {
                        return "v1";
                    }
                }
            }
            """

        File consumerFile = new File(srcMainGosu, 'Consumer.gs')
        consumerFile << """
            uses com.example.Outer.Inner

            class Consumer {
                static function id() : String {
                    return Inner.tag()
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

        String gosuOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')
        String javaOutput = asPath([testProjectDir.root.absolutePath, 'build', 'classes', 'java', 'main'])

        then: 'Both class files exist after the first compile'
        result.task(':compileGosu').outcome == SUCCESS
        new File(javaOutput, 'com/example/Outer.class').exists()
        new File(javaOutput, 'com/example/Outer$Inner.class').exists()
        new File(gosuOutput, 'Consumer.class').exists()

        when: 'Modify the nested class - new public method (ABI change)'
        long outerTime = new File(javaOutput, 'com/example/Outer.class').lastModified()
        long innerTime = new File(javaOutput, 'com/example/Outer$Inner.class').lastModified()
        long consumerTime = new File(gosuOutput, 'Consumer.class').lastModified()
        Thread.sleep(1100)

        outerFile.setText('')
        outerFile << """
            package com.example;

            public class Outer {
                public static class Inner {
                    public static String tag() {
                        return "v2";
                    }
                    public static int newApi() {     // new public method - ABI change
                        return 42;
                    }
                }
            }
            """

        runner.withArguments('compileGosu', '-i')
        result = runner.build()

        then: 'javac re-emits BOTH Outer.class and Outer\$Inner.class'
        result.task(':compileGosu').outcome == SUCCESS
        new File(javaOutput, 'com/example/Outer.class').lastModified() > outerTime
        new File(javaOutput, 'com/example/Outer$Inner.class').lastModified() > innerTime

        and: 'Consumer is recompiled as it uses Outer.Inner.tag()'
        new File(gosuOutput, 'Consumer.class').lastModified() > consumerTime

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'Nested Java class change must not recompile unrelated Gosu sources [Gradle #gradleVersion]'() {
        given:
        buildScript << getIncrementalBuildScriptForTesting()

        File srcMainJavaPkg = new File(testProjectDir.root, 'src/main/java/com/example')
        srcMainJavaPkg.mkdirs()
        File outerFile = new File(srcMainJavaPkg, 'Outer.java')
        outerFile << """
            package com.example;

            public class Outer {
                public static class Inner {
                    public static String tag() {
                        return "v1";
                    }
                }
            }
            """

        File referringConsumer = new File(srcMainGosu, 'ReferringConsumer.gs')
        referringConsumer << """
            uses com.example.Outer.Inner

            class ReferringConsumer {
                static function id() : String {
                    return Inner.tag()
                }
            }
            """

        // UnrelatedConsumer does NOT reference Outer or Outer.Inner.
        // With a correct dep graph its .class file should remain untouched
        // when Outer.Inner changes.
        File unrelatedConsumer = new File(srcMainGosu, 'UnrelatedConsumer.gs')
        unrelatedConsumer << """
            class UnrelatedConsumer {
                static function id() : String {
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

        String gosuOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')
        String javaOutput = asPath([testProjectDir.root.absolutePath, 'build', 'classes', 'java', 'main'])

        then: 'All class files exist after the first compile'
        result.task(':compileGosu').outcome == SUCCESS
        new File(javaOutput, 'com/example/Outer.class').exists()
        new File(javaOutput, 'com/example/Outer$Inner.class').exists()
        new File(gosuOutput, 'ReferringConsumer.class').exists()
        new File(gosuOutput, 'UnrelatedConsumer.class').exists()

        when: 'Modify the nested class - new public method (ABI change)'
        long referringTime = new File(gosuOutput, 'ReferringConsumer.class').lastModified()
        long unrelatedTime = new File(gosuOutput, 'UnrelatedConsumer.class').lastModified()
        Thread.sleep(1100)

        outerFile.setText('')
        outerFile << """
            package com.example;

            public class Outer {
                public static class Inner {
                    public static String tag() {
                        return "v2";
                    }
                    public static int newApi() {
                        return 42;
                    }
                }
            }
            """

        runner.withArguments('compileGosu', '-i')
        result = runner.build()

        then: 'ReferringConsumer IS recompiled'
        result.task(':compileGosu').outcome == SUCCESS
        new File(gosuOutput, 'ReferringConsumer.class').lastModified() > referringTime

        and: 'UnrelatedConsumer should not br recompiled, as it has no edge to Outer.Inner'
        new File(gosuOutput, 'UnrelatedConsumer.class').lastModified() == unrelatedTime

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'Nested Java annotation type change must not recompile unrelated Gosu sources [Gradle #gradleVersion]'() {
        given:
        buildScript << getIncrementalBuildScriptForTesting()

        File srcMainJavaPkg = new File(testProjectDir.root, 'src/main/java/com/example')
        srcMainJavaPkg.mkdirs()
        File outerFile = new File(srcMainJavaPkg, 'Outer.java')
        outerFile << """
            package com.example;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            public class Outer {
                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.TYPE)
                public @interface NestedAnno {
                    String tag() default "v1";
                }
            }
            """

        File annotatedConsumer = new File(srcMainGosu, 'AnnotatedConsumer.gs')
        annotatedConsumer << """
            @com.example.Outer.NestedAnno("v1")
            class AnnotatedConsumer {
                static function id() : String {
                    return "annotated"
                }
            }
            """

        File unrelatedConsumer = new File(srcMainGosu, 'UnrelatedConsumer.gs')
        unrelatedConsumer << """
            class UnrelatedConsumer {
                static function id() : String {
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

        String gosuOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')
        String javaOutput = asPath([testProjectDir.root.absolutePath, 'build', 'classes', 'java', 'main'])

        then:
        result.task(':compileGosu').outcome == SUCCESS
        new File(javaOutput, 'com/example/Outer.class').exists()
        new File(javaOutput, 'com/example/Outer$NestedAnno.class').exists()
        new File(gosuOutput, 'AnnotatedConsumer.class').exists()
        new File(gosuOutput, 'UnrelatedConsumer.class').exists()

        when: 'Modify the nested annotation - add a new attribute (ABI change)'
        long annotatedTime = new File(gosuOutput, 'AnnotatedConsumer.class').lastModified()
        long unrelatedTime = new File(gosuOutput, 'UnrelatedConsumer.class').lastModified()
        Thread.sleep(1100)

        outerFile.setText('')
        outerFile << """
            package com.example;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            public class Outer {
                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.TYPE)
                public @interface NestedAnno {
                    String tag() default "v1";
                    String extra() default "added";
                }
            }
            """

        runner.withArguments('compileGosu', '-i')
        result = runner.build()

        then: 'AnnotatedConsumer IS recompiled (correct outcome, exercised today via fallback)'
        result.task(':compileGosu').outcome == SUCCESS
        new File(gosuOutput, 'AnnotatedConsumer.class').lastModified() > annotatedTime

        and: 'UnrelatedConsumer is not recompiled'
        new File(gosuOutput, 'UnrelatedConsumer.class').lastModified() == unrelatedTime

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'Top-level Java type change does not over-recompile unrelated Gosu sources [Gradle #gradleVersion]'() {
        given:
        buildScript << getIncrementalBuildScriptForTesting()

        File srcMainJavaPkg = new File(testProjectDir.root, 'src/main/java/com/example')
        srcMainJavaPkg.mkdirs()
        File targetFile = new File(srcMainJavaPkg, 'Target.java')
        targetFile << """
            package com.example;

            public class Target {
                public static String tag() {
                    return "v1";
                }
            }
            """

        File referringConsumer = new File(srcMainGosu, 'ReferringConsumer.gs')
        referringConsumer << """
            uses com.example.Target

            class ReferringConsumer {
                static function id() : String {
                    return Target.tag()
                }
            }
            """

        File unrelatedConsumer = new File(srcMainGosu, 'UnrelatedConsumer.gs')
        unrelatedConsumer << """
            class UnrelatedConsumer {
                static function id() : String {
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

        String gosuOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')

        then:
        result.task(':compileGosu').outcome == SUCCESS
        new File(gosuOutput, 'ReferringConsumer.class').exists()
        new File(gosuOutput, 'UnrelatedConsumer.class').exists()

        when: 'Modify the target Java type - new public method (ABI change)'
        long referringTime = new File(gosuOutput, 'ReferringConsumer.class').lastModified()
        long unrelatedTime = new File(gosuOutput, 'UnrelatedConsumer.class').lastModified()
        Thread.sleep(1100)

        targetFile.setText('')
        targetFile << """
            package com.example;

            public class Target {
                public static String tag() {
                    return "v2";
                }
                public static int newApi() {
                    return 42;
                }
            }
            """

        runner.withArguments('compileGosu', '-i')
        result = runner.build()

        then: 'ReferringConsumer IS recompiled'
        result.task(':compileGosu').outcome == SUCCESS
        new File(gosuOutput, 'ReferringConsumer.class').lastModified() > referringTime

        and: 'UnrelatedConsumer is NOT recompiled - dep graph correctly excludes it'
        new File(gosuOutput, 'UnrelatedConsumer.class').lastModified() == unrelatedTime

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'Nested Java class producer is recorded in dep file using $ separator [Gradle #gradleVersion]'() {
        given:
        buildScript << getIncrementalBuildScriptForTesting()

        File srcMainJavaPkg = new File(testProjectDir.root, 'src/main/java/com/example')
        srcMainJavaPkg.mkdirs()
        File outerFile = new File(srcMainJavaPkg, 'Outer.java')
        outerFile << """
            package com.example;

            public class Outer {
                public static class Inner {
                    public String tag() {
                        return "v1";
                    }
                }
            }
            """

        File consumerPkgDir = new File(srcMainGosu, 'com/example')
        consumerPkgDir.mkdirs()
        File consumerFile = new File(consumerPkgDir, 'Consumer.gs')
        consumerFile << """
            package com.example

            class Consumer {
                static function id() : String {
                    return new Outer.Inner().tag()
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

        String gosuOutput = asPath([testProjectDir.root.absolutePath] + expectedOutputDir(gradleVersion) + 'main')
        String javaOutput = asPath([testProjectDir.root.absolutePath, 'build', 'classes', 'java', 'main'])

        then: 'Both Java class files and the Gosu consumer compile, and the dep file exists'
        result.task(':compileGosu').outcome == SUCCESS
        new File(javaOutput, 'com/example/Outer.class').exists()
        new File(javaOutput, 'com/example/Outer$Inner.class').exists()
        new File(gosuOutput, 'com/example/Consumer.class').exists()
        dependencyFile.exists()

        and: 'Dep file matches the golden form - inner-class producer uses $, not .'
        String actualJson = dependencyFile.text

        String expectedJson = """
  "consumers": {
    "com.example.Consumer": [],
    "com.example.Outer": [
      "com.example.Consumer"
    ],
    "com.example.Outer\$Inner": [
      "com.example.Consumer"
    ]
  }
}"""

        actualJson.contains(expectedJson)

        when: 'Modify the nested class - new public method (ABI change)'
        long outerTime = new File(javaOutput, 'com/example/Outer.class').lastModified()
        long innerTime = new File(javaOutput, 'com/example/Outer$Inner.class').lastModified()
        long consumerTime = new File(gosuOutput, 'com/example/Consumer.class').lastModified()
        Thread.sleep(1100)

        outerFile.setText('')
        outerFile << """
            package com.example;

            public class Outer {
                public static class Inner {
                    public String tag() {
                        return "v2";
                    }
                    public int newApi() {
                        return 42;
                    }
                }
            }
            """

        runner.withArguments('compileGosu', '-i')
        result = runner.build()

        then: 'Consumer is recompiled because the Outer$Inner edge survived in the dep graph'
        result.task(':compileGosu').outcome == SUCCESS
        new File(javaOutput, 'com/example/Outer.class').lastModified() > outerTime
        new File(javaOutput, 'com/example/Outer$Inner.class').lastModified() > innerTime
        new File(gosuOutput, 'com/example/Consumer.class').lastModified() > consumerTime

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'Dependency file format uses FQCNs not file paths [Gradle #gradleVersion]'() {
        given: 'A build script with incremental compilation enabled'
        buildScript << getIncrementalBuildScriptForTesting()

        and: 'Create Gosu classes with a dependency relationship in a package'
        File packageDir = new File(srcMainGosu, 'com/example')
        packageDir.mkdirs()

        File producer = new File(packageDir, 'Producer.gs')
        File consumer1 = new File(packageDir, 'Consumer1.gs')
        File consumer2 = new File(packageDir, 'Consumer2.gs')

        producer << """
            package com.example

            class Producer {
                static function getMessage() : String {
                    return "hello"
                }
            }
            """

        consumer1 << """
            package com.example

            uses com.example.Producer

            class Consumer1 {
                static function use() : String {
                    return Producer.getMessage()
                }
            }
            """

        consumer2 << """
            package com.example

            uses com.example.Producer

            class Consumer2 {
                static function use() : String {
                    return Producer.getMessage()
                }
            }
            """

        when: 'Compile the project'
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('clean', 'compileGosu')
                .withGradleVersion(gradleVersion)

        BuildResult result = runner.build()

        then: 'Build succeeds and dependency file is created'
        result.task(':compileGosu').outcome == SUCCESS
        dependencyFile.exists()

        and: 'Dependency file has correct JSON format with FQCNs'
        String actualJson = dependencyFile.text

        // The exact format we expect (all types are registered, even with empty arrays)
        // Common types like java.lang.Object, java.lang.String, etc. are omitted as noise
        // Note: All compiled types are registered to ensure proper tracking
        String expectedJson = """
  "consumers": {
    "com.example.Consumer1": [],
    "com.example.Consumer2": [],
    "com.example.Producer": [
      "com.example.Consumer1",
      "com.example.Consumer2"
    ]
  }
}"""

        actualJson.contains(expectedJson)

        where:
        gradleVersion << gradleVersionsToTest
    }
}
