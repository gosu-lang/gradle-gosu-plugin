package org.gosulang.gradle.unit

import org.gosulang.gradle.functional.MultiversionTestable
import org.gosulang.gradle.tasks.DefaultGosuSourceSet
import org.gradle.api.internal.file.DefaultFileLookup
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.file.DefaultSourceDirectorySetFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.SourceDirectorySetFactory
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory
import org.gradle.api.tasks.util.internal.PatternSets
import org.gradle.internal.nativeintegration.services.NativeServices
import org.junit.Ignore
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.hamcrest.Matchers.*
import static spock.util.matcher.HamcrestSupport.expect

@Ignore("API changes prevent this test from accessing the SourceDirectorySetFactory; see o.g.api.internal.file.TestFiles#sourceDirectorySetFactory")
class DefaultGosuSourceSetTest extends Specification implements MultiversionTestable {

    private DefaultGosuSourceSet sourceSet
    private FileResolver projectFiles
    private SourceDirectorySetFactory factory
    
    @Rule
    public final TemporaryFolder _testProjectDir = new TemporaryFolder()

    def setup() {
        NativeServices.initialize(_testProjectDir.root)
//        FileSystem fileSystem = NativeServices.instance.get(FileSystem.class)
//        DefaultFileLookup defaultFileLookup = new DefaultFileLookup(fileSystem, PatternSets.getNonCachingPatternSetFactory())
//        FileResolver fileResolver = defaultFileLookup.getFileResolver(_testProjectDir.root)
        projectFiles = new DefaultFileLookup(NativeServices.instance.get(FileSystem.class), PatternSets.getNonCachingPatternSetFactory()).getFileResolver(_testProjectDir.root)
        factory = new DefaultSourceDirectorySetFactory(fileResolver, new DefaultDirectoryFileTreeFactory());
        sourceSet = new DefaultGosuSourceSet("<set-display-name>", projectFiles)
    }

    def 'verify the default values'() {
        expect:
        sourceSet.gosu instanceof DefaultSourceDirectorySet
        expect sourceSet.gosu, emptyIterable()
        sourceSet.gosu.displayName == '<set-display-name> Gosu source'
        expect sourceSet.gosu.filter.includes, equalTo(['**/*.gs', '**/*.gsx', '**/*.gst', '**/*.gsp'] as Set)
        expect sourceSet.gosu.filter.excludes, empty()
    }
    
    def 'can configure Gosu source'() {
        when:
        sourceSet.gosu {
            srcDir 'src/somepathtogosu'
        }
        
        then:
        expect sourceSet.gosu.srcDirs, equalTo([new File(_testProjectDir.root, 'src/somepathtogosu').canonicalFile] as Set)
    }

    def 'can exclude a file pattern'() {
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
