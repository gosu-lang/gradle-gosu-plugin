package org.gosulang.gradle.unit

import org.gosulang.gradle.GosuBasePlugin
import org.gosulang.gradle.tasks.compile.GosuCompile
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.lang.reflect.Method
import java.nio.file.Path

/**
 * Direct coverage of {@code GosuCompile#pruneEmptyDirectories}, the sweeper that removes package
 * directories left empty by the stale-output cleanup for
 * <a href="https://github.com/gosu-lang/gradle-gosu-plugin/issues/105">#105</a>.
 *
 * <p>The functional tests reach this code only through a real compile, which makes the edge cases
 * -- a surviving sibling, the destination directory itself, a path outside the root -- expensive to
 * provoke and slow to check.  Driving the method against directory trees built by hand covers each
 * branch in milliseconds.  The sole reason for reflection is that the method is, and should remain,
 * private: it is an implementation detail of {@code cleanStaleOutputs()}, not API.
 */
class PruneEmptyDirectoriesTest extends Specification {

    @Rule
    TemporaryFolder testProjectDir = new TemporaryFolder()

    private GosuCompile compileGosu
    private File destinationDir

    def setup() {
        // Spock does not run the JUnit 4 @Rule for us; same explicit create() the functional
        // specs' AbstractGosuPluginSpecification#setup performs.
        testProjectDir.create()
        Project project = ProjectBuilder.builder().withProjectDir(testProjectDir.root).build()
        project.pluginManager.apply(GosuBasePlugin)
        project.sourceSets.create('main')
        compileGosu = project.tasks['compileGosu'] as GosuCompile
        destinationDir = compileGosu.destinationDirectory.get().asFile
        assert destinationDir.mkdirs()
    }

    /**
     * Mirrors the contract cleanStaleOutputs() calls under: root arrives absolute and normalised.
     */
    private void prune(Collection<File> startingPoints) {
        Method method = GosuCompile.getDeclaredMethod('pruneEmptyDirectories', Set, Path)
        method.setAccessible(true)
        method.invoke(compileGosu,
                new HashSet<File>(startingPoints),
                destinationDir.toPath().toAbsolutePath().normalize())
    }

    /** Creates an empty directory under the destination directory. */
    private File emptyDir(String relativePath) {
        File dir = new File(destinationDir, relativePath)
        assert dir.mkdirs() || dir.isDirectory()
        return dir
    }

    /** Creates a file (and its parents) under the destination directory. */
    private File fileAt(String relativePath) {
        File file = new File(destinationDir, relativePath)
        assert file.parentFile.mkdirs() || file.parentFile.isDirectory()
        file.text = 'stand-in for a .class file'
        return file
    }

    def 'an emptied package directory is removed along with its now-empty parent'() {
        given:
        File pkg = emptyDir('com/example')

        when:
        prune([pkg])

        then:
        !pkg.exists()
        !new File(destinationDir, 'com').exists()
        destinationDir.isDirectory()
    }

    def 'nested empty directories collapse in a single pass'() {
        given: 'only the deepest directory is offered as a starting point'
        File deepest = emptyDir('a/b/c/d')

        when:
        prune([deepest])

        then: 'each parent is re-queued as its child goes, so the whole chain unwinds'
        !new File(destinationDir, 'a').exists()
        destinationDir.isDirectory()
    }

    def 'a directory that still holds a file is left alone'() {
        given:
        File survivor = fileAt('com/example/Keep.class')

        when:
        prune([survivor.parentFile])

        then:
        survivor.exists()
        survivor.parentFile.isDirectory()
    }

    def 'a surviving sibling stops the walk at the shared parent'() {
        given:
        File doomed = emptyDir('com/example')
        File survivor = fileAt('com/other/Keep.class')

        when:
        prune([doomed])

        then: 'the empty package goes'
        !doomed.exists()

        and: 'the shared parent stays, because the sibling is still occupied'
        new File(destinationDir, 'com').isDirectory()
        survivor.exists()
    }

    def 'the destination directory itself is never removed, even when empty'() {
        given: 'the last package empties out, leaving the output root with nothing in it'
        File pkg = emptyDir('com')

        when:
        prune([pkg])

        then:
        !pkg.exists()

        and: 'the root is a declared @OutputDirectory and must survive the sweep'
        destinationDir.isDirectory()
        destinationDir.list().length == 0
    }

    def 'a starting point outside the destination directory is ignored'() {
        given: 'a sibling whose path shares a string prefix with the root -- .../main vs .../main2'
        File sibling = new File(destinationDir.parentFile, destinationDir.name + '2')
        assert sibling.mkdirs()

        and: 'and an unrelated directory elsewhere in the project'
        File unrelated = testProjectDir.newFolder('somewhere-else')

        when:
        prune([sibling, unrelated])

        then: 'Path#startsWith is component-aware, so neither is mistaken for something under root'
        sibling.isDirectory()
        unrelated.isDirectory()
    }

    // --- packageDirectoriesOf: the FQCN -> pruning-candidate mapping used by the incremental path

    private Set<File> packageDirectoriesOf(Collection<String> fqcns) {
        Method method = GosuCompile.getDeclaredMethod('packageDirectoriesOf', Set, File)
        method.setAccessible(true)
        return method.invoke(compileGosu, new HashSet<String>(fqcns), destinationDir) as Set<File>
    }

    def 'a packaged type maps to its package directory'() {
        expect:
        packageDirectoriesOf(['com.example.Doomed']) == [new File(destinationDir, 'com/example')] as Set
    }

    def 'types sharing a package collapse to one candidate'() {
        expect:
        packageDirectoriesOf(['com.example.One', 'com.example.Two']) ==
                [new File(destinationDir, 'com/example')] as Set
    }

    def 'a nested type maps to the enclosing package, not the enclosing type'() {
        expect: 'Outer$Inner is one FQCN segment -- the $ is not a package separator'
        packageDirectoriesOf(['com.example.Outer$Inner']) ==
                [new File(destinationDir, 'com/example')] as Set
    }

    def 'a default-package type yields no candidate'() {
        expect: 'its directory is the output root, which the sweeper would refuse anyway'
        packageDirectoriesOf(['Doomed']).isEmpty()
    }

    def 'candidates are produced whether or not they exist on disk'() {
        given: 'a removed Java type names a package with no Gosu output of its own'
        Set<File> candidates = packageDirectoriesOf(['com.nowhere.JavaOnly'])

        expect: 'the mapping is purely lexical; the sweeper is what skips a directory that is absent'
        candidates == [new File(destinationDir, 'com/nowhere')] as Set
        !candidates.first().exists()

        when: 'the sweeper is handed it anyway'
        prune(candidates)

        then: 'no exception, and nothing else disturbed'
        destinationDir.isDirectory()
    }

    def 'a starting point that no longer exists is skipped'() {
        given:
        File ghost = new File(destinationDir, 'already/gone')

        when:
        prune([ghost])

        then:
        !ghost.exists()
        destinationDir.isDirectory()
    }
}
