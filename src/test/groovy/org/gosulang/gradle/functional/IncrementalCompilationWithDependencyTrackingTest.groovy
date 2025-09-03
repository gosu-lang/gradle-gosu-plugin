package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

@Unroll
class IncrementalCompilationWithDependencyTrackingTest extends AbstractGosuPluginSpecification {

    File srcMainGosu, baseClass, derivedClass, independentClass
    File dependencyFile

    /**
     * super#setup is invoked automatically
     * @return
     */
    def setup() {
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
        baseClass = new File(srcMainGosu, 'BaseClass.gs')
        derivedClass = new File(srcMainGosu, 'DerivedClass.gs')
        independentClass = new File(srcMainGosu, 'IndependentClass.gs')
        dependencyFile = new File(testProjectDir.root, 'build/tmp/gosuc-deps-compileGosu.json')
    }
    
    def 'Incremental compilation with dependency tracking [Gradle #gradleVersion]'() {
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
        
        then: 'Deleted file\'s output is removed'
        result.task(':compileGosu').outcome == SUCCESS
        !new File(buildOutput, 'DerivedClass.class').exists()
        new File(buildOutput, 'BaseClass.class').exists()
        new File(buildOutput, 'IndependentClass.class').exists()
        
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
                .withArguments('clean', 'compileGosu')
                .withGradleVersion(gradleVersion)

        BuildResult result = runner.build()
        
        then: 'Compilation succeeds but no dependency file is created'
        result.task(':compileGosu').outcome == SUCCESS
        !dependencyFile.exists()
        
        where:
        gradleVersion << gradleVersionsToTest
    }
}