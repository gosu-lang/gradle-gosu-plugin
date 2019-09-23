package org.gosulang.gradle.unit

import org.gosulang.gradle.tasks.DefaultGosuSourceSet
import org.gosulang.gradle.tasks.GosuSourceSet
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
//import org.gradle.api.internal.DefaultInstantiatorFactory
import org.gradle.api.internal.file.DefaultFileLookup
import org.gradle.api.internal.file.DefaultFilePropertyFactory
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.file.DefaultSourceDirectorySetFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.SourceDirectorySetFactory
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory
import org.gradle.api.internal.model.DefaultObjectFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.reflect.Instantiator
//import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.api.tasks.util.internal.PatternSets
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.nativeintegration.services.NativeServices
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.IgnoreIf
import spock.lang.Specification

import static org.hamcrest.Matchers.*
import static spock.util.matcher.HamcrestSupport.expect

class DefaultGosuSourceSetTest extends Specification {

    private GosuSourceSet sourceSet
    
    @Rule
    public TemporaryFolder _testProjectDir = new TemporaryFolder()

    private Project createRootProject(File rootDir) {
        return ProjectBuilder
                .builder()
                .withProjectDir(rootDir)
                .build()
    }

    def setup() {
        Project project = createRootProject()
        NativeServices.initialize(_testProjectDir.root)
//        FileResolver fileResolver = new DefaultFileLookup(NativeServices.instance.get(FileSystem.class), PatternSets.getNonCachingPatternSetFactory()).getFileResolver(_testProjectDir.root)
//        SourceDirectorySetFactory sourceDirectorySetFactory = new DefaultSourceDirectorySetFactory(fileResolver, new DefaultDirectoryFileTreeFactory())
        SourceDirectorySetFactory sourceDirectorySetFactory = new DefaultSourceDirectorySetFactory((ObjectFactory) ((ProjectInternal)project).services.get(ObjectFactory))
        sourceSet = new DefaultGosuSourceSet("<set-display-name>", sourceDirectorySetFactory)
    }

    def 'verify_the_default_values'() {
        expect:
        sourceSet.gosu instanceof DefaultSourceDirectorySet
        expect sourceSet.gosu, emptyIterable()
        sourceSet.gosu.displayName == 'set display name Gosu source'
        expect sourceSet.gosu.filter.includes, equalTo(['**/*.java', '**/*.gs', '**/*.gsx', '**/*.gst', '**/*.gsp'] as Set)
        expect sourceSet.gosu.filter.excludes, empty()

        sourceSet.allGosu instanceof DefaultSourceDirectorySet
        expect sourceSet.allGosu, emptyIterable()
        sourceSet.allGosu.displayName == 'set display name Gosu source'
        sourceSet.allGosu.source.contains(sourceSet.gosu)
        expect sourceSet.allGosu.filter.includes, equalTo(['**/*.gs', '**/*.gsx', '**/*.gst', '**/*.gsp'] as Set)
        expect sourceSet.allGosu.filter.excludes, empty()
    }

    @IgnoreIf({System.getProperty("os.name").toLowerCase().contains("mac")})
    def 'can configure Gosu source'() {
        when:
        sourceSet.gosu {
            srcDir 'src/somepathtogosu'
        }

        then:
        expect sourceSet.gosu.srcDirs, equalTo([new File(_testProjectDir.root, 'src/somepathtogosu').absoluteFile] as Set)
    }

    @IgnoreIf({System.getProperty("os.name").toLowerCase().contains("mac")})
    def 'can configure Gosu source using an action'() {
        when:
        sourceSet.gosu({ set -> set.srcDir 'src/somepathtogosu' } as Action<SourceDirectorySet>)

        then:
        expect sourceSet.gosu.srcDirs, equalTo([new File(_testProjectDir.root, 'src/somepathtogosu').absoluteFile] as Set)
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
        when:
        sourceSet.gosu {
            filter.include '**/*.grs', '**/*.gr'
        }

        then:
        expect sourceSet.gosu.filter.includes, equalTo(['**/*.java','**/*.gs', '**/*.gsx', '**/*.gst', '**/*.gsp', '**/*.grs', '**/*.gr'] as Set)
        expect sourceSet.gosu.filter.excludes, empty()
        then: // allGosu is unmodified
        expect sourceSet.allGosu.filter.includes, equalTo(['**/*.gs', '**/*.gsx', '**/*.gst', '**/*.gsp'] as Set)
        expect sourceSet.allGosu.filter.excludes, empty()
    }

}
