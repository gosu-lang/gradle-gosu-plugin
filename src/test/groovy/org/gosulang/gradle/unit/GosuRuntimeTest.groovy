package org.gosulang.gradle.unit

import org.gosulang.gradle.GosuBasePlugin
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.file.collections.LazilyInitializedFileCollection
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.VersionNumber
import org.junit.Ignore
import spock.lang.Specification

class GosuRuntimeTest extends Specification {

    def project = ProjectBuilder.builder().build()

    def setup() {
        project.pluginManager.apply(GosuBasePlugin)
    }

    def 'inference fails if no repository declared'() {
        when:
        def gosuClasspath = project.gosuRuntime.inferGosuClasspath([new File('other.jar'), new File('gosu-core-api-1.8.jar')])
        gosuClasspath.call().files

        then:
        GradleException e = thrown()
        System.out.println(e.message)
        e.message.equals('Cannot infer Gosu classpath because no repository is declared in ' + project)
    }

    def 'test to find Gosu Jars on class path'() {
        when:
        def core = project.gosuRuntime.findGosuJar([new File('other.jar'), new File('gosu-core-1.7.jar'), new File('gosu-core-api-1.8.jar')], 'core')
        def core_api = project.gosuRuntime.findGosuJar([new File('other.jar'), new File('gosu-core-1.7.jar'), new File('gosu-core-api-1.8.jar')], 'core-api')

        then:
        core.name == 'gosu-core-1.7.jar'
        core_api.name == 'gosu-core-api-1.8.jar'
    }

    def 'returns null if Gosu Jar not found'() {
        when:
        def file = project.gosuRuntime.findGosuJar([new File('other.jar'), new File('gosu-core-1.7.jar'), new File('gosu-core-api-1.8.jar')], 'xml')

        then:
        file == null
    }

    def 'correctly determines version of a Gosu Jar'() {
        expect:
        with(project.gosuRuntime) {
            getGosuVersion(new File('gosu-core-1-spec-SNAPSHOT.jar')) == '1-spec-SNAPSHOT'
            getGosuVersion(new File('gosu-core-api-1.8.jar')) == '1.8'
            getGosuVersion(new File('gosu-xml-0.9-15-SNAPSHOT.jar')) == '0.9-15-SNAPSHOT'
            
            VersionNumber.parse(getGosuVersion(new File('gosu-core-1-spec-SNAPSHOT.jar'))).toString() == '1.0.0-spec-SNAPSHOT'
            VersionNumber.parse(getGosuVersion(new File('gosu-core-api-1.8.jar'))).toString() == '1.8.0'
            VersionNumber.parse(getGosuVersion(new File('gosu-xml-0.9-15-SNAPSHOT.jar'))).toString() == '0.9.0-15-SNAPSHOT'
        }
    }
    
}
