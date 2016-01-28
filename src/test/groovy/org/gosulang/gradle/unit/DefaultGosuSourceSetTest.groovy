package org.gosulang.gradle.unit

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

    private DefaultGosuSourceSet sourceSet

    @Rule
    public final TemporaryFolder _testProjectDir = new TemporaryFolder()

    def setup() {
        NativeServices.initialize(_testProjectDir.root)
    }

    def 'verify the default values'() {
        when:
        sourceSet = new DefaultGosuSourceSet("<set-display-name>", [resolve: { it as File }] as FileResolver)
        
        then:
        sourceSet.gosu instanceof DefaultSourceDirectorySet
        expect sourceSet.gosu, emptyIterable()
        sourceSet.gosu.displayName == '<set-display-name> Gosu source'
        expect sourceSet.gosu.filter.includes, equalTo(['**/*.gs', '**/*.gsx', '**/*.gst', '**/*.gsp'] as Set)
        expect sourceSet.gosu.filter.excludes, empty()
    }
    
    def 'can configure Gosu source'() {
        given:
        sourceSet = new DefaultGosuSourceSet("<set-display-name>", [resolve: { it as File }] as FileResolver)

        when:
        sourceSet.gosu {
            srcDir 'src/somepathtogosu'
        }
        
        then:
        expect sourceSet.gosu.srcDirs, equalTo([new File('src/somepathtogosu').canonicalFile] as Set)
    }

    def 'can exclude a file pattern'() {
        given:
        sourceSet = new DefaultGosuSourceSet("<set-display-name>", [resolve: { it as File }] as FileResolver)

        when:
        sourceSet.gosu {
            exclude '**/Errant_*'
        }

        then:
        expect sourceSet.gosu.excludes, equalTo(['**/Errant_*'] as Set)
    }

    def 'can specify additional source extensions'() {
        given:
        sourceSet = new DefaultGosuSourceSet("<set-display-name>", [resolve: { it as File }] as FileResolver)

        when:
        sourceSet.gosu {
            filter.include '**/*.grs', '**/*.gr' 
        }
        
        then:
        expect sourceSet.gosu.filter.includes, equalTo(['**/*.gs', '**/*.gsx', '**/*.gst', '**/*.gsp', '**/*.grs', '**/*.gr'] as Set)
        expect sourceSet.gosu.filter.excludes, empty()
    }

}
