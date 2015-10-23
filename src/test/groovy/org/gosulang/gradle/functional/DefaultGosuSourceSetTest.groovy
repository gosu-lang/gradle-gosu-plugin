package org.gosulang.gradle.functional

import org.gosulang.gradle.tasks.DefaultGosuSourceSet
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.nativeintegration.services.NativeServices
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.hamcrest.Matchers.*
import static spock.util.matcher.HamcrestSupport.expect

class DefaultGosuSourceSetTest extends Specification {

    @Rule
    public final TemporaryFolder _testProjectDir = new TemporaryFolder()

    def setup() {
        NativeServices.initialize(_testProjectDir.root)
    }

    def "verify sourceset default values"() {
        when:
        final DefaultGosuSourceSet sourceSet = new DefaultGosuSourceSet("<set-display-name>", [resolve: { it as File }] as FileResolver)

        then:
        sourceSet.gosu instanceof DefaultSourceDirectorySet
        expect sourceSet.gosu, emptyIterable()
        expect sourceSet.gosu.displayName, equalTo('<set-display-name> Gosu source')
        expect sourceSet.gosu.filter.includes, equalTo(['**/*.gs', '**/*.gsx', '**/*.gst'] as Set)
        expect sourceSet.gosu.filter.excludes, empty()
    }

    def "can configure gosu source"() {
        given:
        final DefaultGosuSourceSet sourceSet = new DefaultGosuSourceSet("<set-display-name>", [resolve: { it as File }] as FileResolver)

        when:
        sourceSet.gosu {
            srcDir 'src/somepathtogosu'
        }

        then:
        expect sourceSet.gosu.srcDirs, equalTo([new File('src/somepathtogosu').canonicalFile] as Set)
    }

}
