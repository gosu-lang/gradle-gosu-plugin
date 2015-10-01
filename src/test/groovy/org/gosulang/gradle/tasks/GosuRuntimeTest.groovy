package org.gosulang.gradle.tasks

import org.gosulang.gradle.GosuBasePlugin
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.file.collections.LazilyInitializedFileCollection
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Ignore
import spock.lang.Specification

class GosuRuntimeTest extends Specification {

    def project = ProjectBuilder.builder().build()

    def setup() {
        project.pluginManager.apply(GosuBasePlugin)
    }

//    @Ignore("Plugin requires explicit declaration of gosu-core-api and gosu-core; inference is not required")
//    def "inferred Gosu class path contains 'gosu-core' repository dependency matching 'gosu-core-api' Jar found on class path"() {
//        project.repositories {
//            mavenCentral()
//        }
//
//        when:
//        def classpath = project.gosuRuntime.inferGosuClasspath ([new File("other.jar"), new File("gosu-core-api-1.8.jar")])
//
//        then:
//        classpath instanceof LazilyInitializedFileCollection
//        with(classpath.delegate) {
//            it instanceof Configuration
//            it.state == Configuration.State.UNRESOLVED
//            it.dependencies.size() == 1
//            with(it.dependencies.iterator().next()) {
//                group == "org.gosu-lang.gosu"
//                name == "gosu-core"
//                version == "1.8"
//            }
//        }
//    }

    def "inference fails if no repository declared"() {
        when:
        def gosuClasspath = project.gosuRuntime.inferGosuClasspath([new File("other.jar"), new File("gosu-core-api-1.8.jar")])
        gosuClasspath.files

        then:
        GradleException e = thrown()
        e.message == "Cannot infer Gosu class path because no repository is declared in $project"
    }

    def "test to find Gosu Jar on class path"() {
        when:
        def file = project.gosuRuntime.findGosuJar([new File("other.jar"), new File("gosu-core-1.7.jar"), new File("gosu-core-api-1.8.jar")], "core")

        then:
        file.name == "gosu-core-1.7.jar"
    }

    def "returns null if Gosu Jar not found"() {
        when:
        def file = project.gosuRuntime.findGosuJar([new File("other.jar"), new File("gosu-core-1.7.jar"), new File("gosu-core-api-1.8.jar")], "xml")

        then:
        file == null
    }

    def "allows to determine version of Scala Jar"() {
        expect:
        with(project.gosuRuntime) {
            getGosuVersion(new File("gosu-core-1-spec-SNAPSHOT.jar")) == "1-spec-SNAPSHOT"
            getGosuVersion(new File("gosu-core-api-1.8.jar")) == "1.8"
            getGosuVersion(new File("gosu-xml-0.9-15-SNAPSHOT.jar")) == "0.9-15-SNAPSHOT"
        }
    }
    
}
