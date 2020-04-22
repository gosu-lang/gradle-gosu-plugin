package org.gosulang.gradle.unit

import org.gosulang.gradle.GosuBasePlugin
import org.gosulang.gradle.tasks.compile.GosuCompile
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.gradle.util.WrapUtil.toLinkedSet
import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.instanceOf
import static org.junit.Assert.assertThat
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
        project.pluginManager.apply(JavaPlugin)
        project.pluginManager.apply(GosuBasePlugin)
    }

    @Test
    public void appliesTheJavaBasePluginToTheProject() {

        assertTrue(project.plugins.hasPlugin(JavaPlugin))
    }

    @Test
    void appliesMappingsToNewSourceSet() {
        def sourceSet = project.sourceSets.create('custom')
        assertThat(sourceSet.gosu.displayName, equalTo("custom Gosu source"))
        assertThat(sourceSet.gosu.srcDirs, equalTo(toLinkedSet(project.file("src/custom/gosu"))))
    }

    @Test
    void addsCompileTaskToNewSourceSet() {
        project.sourceSets.create('custom')

        def task = project.tasks['compileCustomGosu']
        assertThat(task, instanceOf(GosuCompile.class))
        assertThat(task.description, equalTo('Compiles the custom Gosu source.'))
        assertTrue(task.dependsOn.contains('compileCustomJava'))
    }

}
