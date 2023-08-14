package org.gosulang.gradle.unit

import org.gosulang.gradle.GosuBasePlugin
import org.gosulang.gradle.tasks.compile.GosuCompile
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.gradle.util.internal.WrapUtil.toLinkedSet
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

class GosuBasePluginTest {

    @Rule
    public final TemporaryFolder _testProjectDir = new TemporaryFolder()

    private Project createRootProject() {
        return createRootProject(_testProjectDir.root)
    }

    private Project createRootProject(File rootDir) {
        return ProjectBuilder
                .builder()
                .withProjectDir(rootDir)
                .build()
    }

    private Project project

    @Before
    public void applyPlugin() throws IOException {
        project = createRootProject()
        project.pluginManager.apply(GosuBasePlugin)
    }

    @Test
    public void appliesTheJavaBasePluginToTheProject() {
        assertTrue(project.plugins.hasPlugin(JavaBasePlugin))
    }

    @Test
    void appliesMappingsToNewSourceSet() {
        def sourceSet = project.sourceSets.create('custom')
        assertEquals(sourceSet.gosu.displayName, "custom Gosu source")
        assertEquals(sourceSet.gosu.srcDirs, toLinkedSet(project.file("src/custom/gosu")))
        assertEquals(sourceSet.gosu.getDestinationDirectory().get().asFile, project.file("build/classes/gosu/custom"))

    }

    @Test
    void addsGosuSourceSetOutputsToNewSourceSet() {
        def sourceSet = project.sourceSets.create("custom")
        assertTrue(sourceSet.output.classesDirs.contains(project.file("build/classes/java/custom")))
        assertTrue(sourceSet.output.classesDirs.contains(project.file("build/classes/gosu/custom")))
    }

    @Test
    void addsCompileTaskToNewSourceSet() {
        def sourceSet = project.sourceSets.create('custom')

        def task = project.tasks['compileCustomGosu']
        assertTrue(task instanceof GosuCompile)
        assertEquals(task.description, 'Compiles the custom Gosu source.')
        assertTrue(task.dependsOn.contains('compileCustomJava'))
    }

}
