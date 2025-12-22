package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildSuccess
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Unroll
class GosuRuntimeInferenceTest extends AbstractGosuPluginSpecification {

    File simplePogo
    File srcMainGosu
    File settingsFile
    
    def 'Build throws when gosu-core-api jar is not declared as a dependency [Gradle #gradleVersion]'() {
        given:
        buildScript << 
            """
            plugins {
                id 'org.gosu-lang.gosu'
            }
            repositories {
                mavenLocal()
                mavenCentral()
                maven {
                    url 'https://oss.sonatype.org/content/repositories/snapshots' //for Gosu snapshot builds
                }
            }
            dependencies {
                //implementation group: 'org.gosu-lang.gosu', name: 'gosu-core-api', version: '$gosuVersion' //intentionally commenting-out to cause build failure
                testImplementation group: 'junit', name: 'junit', version: '4.12'
            }
            """
        
        simplePogo = new File(testProjectDir.newFolder('src', 'main', 'gosu'), 'SimplePogo.gs')
        simplePogo << """
            public class SimplePogo {
            }"""
        
        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('clean', 'compileGosu', '-is')
                .withGradleVersion(gradleVersion)
                .forwardOutput()
        
        BuildResult result = runner.buildAndFail()
        
        then:
        notThrown(UnexpectedBuildSuccess)
        result.output.contains('Cannot infer Gosu classpath because the Gosu Core API Jar was not found.')
        !new File(testProjectDir.root, asPath(expectedOutputDir(gradleVersion) + ['main', 'SimplePogo.class'])).exists()

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'Repository declared in settings.gradle via dependencyResolutionManagement is recognized [Gradle #gradleVersion]'() {
        given:
        // Create settings.gradle with dependencyResolutionManagement
        settingsFile = testProjectDir.newFile('settings.gradle')
        settingsFile << """
            dependencyResolutionManagement {
                repositories {
                    mavenLocal()
                    mavenCentral()
                    maven {
                        url 'https://central.sonatype.com/repository/maven-snapshots/'
                    }
                }
            }

            rootProject.name = 'test-settings-repo'
        """

        // Create build.gradle WITHOUT repositories block
        buildScript << """
            plugins {
                id 'org.gosu-lang.gosu'
            }

            // Note: NO repositories block here - all repositories in settings.gradle

            dependencies {
                implementation group: 'org.gosu-lang.gosu', name: 'gosu-core-api', version: '$gosuVersion'
                testImplementation group: 'junit', name: 'junit', version: '4.12'
            }
        """

        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
        simplePogo = new File(srcMainGosu, 'SimplePogo.gs')
        simplePogo << """
            public class SimplePogo {
                function sayHello() : String {
                    return "Hello from Gosu"
                }
            }
        """

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('compileGosu', '-is')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()

        then:
        result.task(":compileGosu").outcome == SUCCESS
        new File(testProjectDir.root, asPath(expectedOutputDir(gradleVersion) + ['main', 'SimplePogo.class'])).exists()
        result.output.contains('Initializing gosuc compiler')

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'Build fails when no repositories are declared anywhere [Gradle #gradleVersion]'() {
        given:
        // Create settings.gradle WITHOUT repositories
        settingsFile = testProjectDir.newFile('settings.gradle')
        settingsFile << """
            rootProject.name = 'test-no-repo'
        """

        // Create build.gradle WITHOUT repositories block
        buildScript << """
            plugins {
                id 'org.gosu-lang.gosu'
            }

            // No repositories block

            dependencies {
                implementation group: 'org.gosu-lang.gosu', name: 'gosu-core-api', version: '$gosuVersion'
            }
        """

        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
        simplePogo = new File(srcMainGosu, 'SimplePogo.gs')
        simplePogo << """
            public class SimplePogo {}
        """

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('compileGosu', '-is')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.buildAndFail()

        then:
        // With no repositories, Gradle's dependency resolution will fail
        // The error message varies by Gradle version but typically mentions "no repositories"
        result.output.contains('gosu-doc') || result.output.contains('Could not resolve')

        where:
        gradleVersion << gradleVersionsToTest
    }

    def 'Repositories in both settings and project are recognized [Gradle #gradleVersion]'() {
        given:
        // Create settings.gradle with one repository
        settingsFile = testProjectDir.newFile('settings.gradle')
        settingsFile << """
            dependencyResolutionManagement {
                repositories {
                    mavenLocal()
                }
            }

            rootProject.name = 'test-both-repos'
        """

        // Create build.gradle with additional repositories
        buildScript << """
            plugins {
                id 'org.gosu-lang.gosu'
            }

            repositories {
                mavenCentral()
                maven {
                    url 'https://central.sonatype.com/repository/maven-snapshots/'
                }
            }

            dependencies {
                implementation group: 'org.gosu-lang.gosu', name: 'gosu-core-api', version: '$gosuVersion'
            }
        """

        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
        simplePogo = new File(srcMainGosu, 'SimplePogo.gs')
        simplePogo << """
            public class SimplePogo {}
        """

        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('compileGosu', '-is')
                .withGradleVersion(gradleVersion)
                .forwardOutput()

        BuildResult result = runner.build()

        then:
        result.task(":compileGosu").outcome == SUCCESS
        new File(testProjectDir.root, asPath(expectedOutputDir(gradleVersion) + ['main', 'SimplePogo.class'])).exists()

        where:
        gradleVersion << gradleVersionsToTest
    }

}
