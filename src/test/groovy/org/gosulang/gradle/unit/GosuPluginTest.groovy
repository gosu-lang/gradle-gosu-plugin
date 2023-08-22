package org.gosulang.gradle.unit

import org.gosulang.gradle.GosuPlugin
import org.gosulang.gradle.tasks.compile.GosuCompile
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.configurations.Configurations
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.testfixtures.ProjectBuilder
import org.hamcrest.Matchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import java.util.concurrent.Callable

import static org.gradle.util.internal.WrapUtil.toLinkedSet
import static org.hamcrest.MatcherAssert.assertThat
import static org.junit.Assert.*

class GosuPluginTest {

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
    private ObjectFactory objectFactory
    private JavaPluginExtension extension

    @Before
    public void applyPlugin() throws IOException {
        project = createRootProject()
        objectFactory = ((ProjectInternal) project).services.get(ObjectFactory)
        project.pluginManager.apply(JavaPlugin)
        extension = javaPluginExtension(project)
        project.pluginManager.apply(GosuPlugin)
    }
    private static JavaPluginExtension javaPluginExtension(Project project) {
        return extensionOf(project, JavaPluginExtension.class);
    }
    private static <T> T extensionOf(ExtensionAware extensionAware, Class<T> type) {
        return extensionAware.getExtensions().getByType(type);
    }

    @Test
    void appliesTheJavaPluginToTheProject() {
        assertTrue(project.plugins.hasPlugin(JavaPlugin))
    }

    @Test
    public void addsGosuConfigurationToTheProject() {
        def configuration = project.configurations.getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom), Matchers.emptyIterable())
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)
    }

    @Test
    void addsGosuConventionToEachSourceSet() {
        def sourceSet = project.sourceSets.main
        assertEquals(sourceSet.gosu.displayName, 'main Gosu source')
        assertEquals(sourceSet.gosu.srcDirs, toLinkedSet(project.file('src/main/gosu')))

        sourceSet = project.sourceSets.test
        assertEquals(sourceSet.gosu.displayName, 'test Gosu source')
        assertEquals(sourceSet.gosu.srcDirs, toLinkedSet(project.file('src/test/gosu')))
    }

    @Test
    void addsCompileTaskForEachSourceSet() {
        def task = project.tasks['compileGosu']
        assertTrue(task instanceof GosuCompile)
        assertEquals(task.description, 'Compiles the main Gosu source.')
        assertTrue(task.dependsOn.contains(JavaPlugin.COMPILE_JAVA_TASK_NAME))

        task = project.tasks['compileTestGosu']
        assertTrue(task instanceof GosuCompile)
        assertEquals(task.description, 'Compiles the test Gosu source.')
        assertTrue(task.dependsOn.contains(JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME))
        //assertTrue(task.dependsOn.contains(JavaPlugin.CLASSES_TASK_NAME)) //TODO failing; do we care?
    }

    @Test
    void canConfigureSourceSets() {
        File dir = new File('classes-dir')
        extension.sourceSets {
            main {
                System.out.println(output.classesDirs.asPath)
                output.dir ( new Callable() {
                    public Object call() {
                        return dir;
                    } })
            }
        }
        assert(extension.sourceSets.main.output.dirs.containsAll(project.file(dir)))
   //     assertThat(convention.sourceSets.main.output.classesDirs.singleFile, equalTo(project.file(dir)))

//        main {
//                output.resourcesDir = dir
//            }
//        }
//        assertEquals(extension.sourceSets.main.output.getResourcesDir(), project.file(dir))
    }

    @Test
    void canConfigureMainGosuClosure() {
        File dir = new File('path/to/POGOs')
        project.sourceSets {
            main {
                gosu.srcDirs = [ dir ] //gosu typeis org.gradle.api.internal.file.DefaultSourceDirectorySet
            }
        }
        assertEquals(project.sourceSets.main.gosu.srcDirs, toLinkedSet(project.file(dir)))
    }

    @Test
    void canConfigureMainGosuClosureSrcDirsSingleArg() {
        String dirAsString = 'path/to/POGOs'
        project.sourceSets {
            main {
                gosu {
                    srcDirs dirAsString //gosu typeis org.gradle.api.internal.file.DefaultSourceDirectorySet
                }
            }
        }
        assertEquals(project.sourceSets.main.gosu.srcDirs, toLinkedSet(project.file('src/main/gosu'), project.file(dirAsString)))
    }

    @Test
    void canConfigureMainGosuClosureSrcDirsMultipleArg() {
        String dirAsString = 'path/to/POGOs'
        String anotherSource = 'some/more/gosu/files'
        project.sourceSets {
            main {
                gosu {
                    srcDirs dirAsString, anotherSource
                }
            }
        }
        assertEquals(project.sourceSets.main.gosu.srcDirs, toLinkedSet(project.file('src/main/gosu'), project.file(dirAsString), project.file(anotherSource)))
    }

    @Test
    void canConfigureMainGosuClosureSrcDirSingular() {
        String dirAsString = 'path/to/POGOs'
        project.sourceSets {
            main {
                gosu {
                    srcDir dirAsString //gosu typeis org.gradle.api.internal.file.DefaultSourceDirectorySet
                }
            }
        }
        assertEquals(project.sourceSets.main.gosu.srcDirs, toLinkedSet(project.file('src/main/gosu'), project.file(dirAsString)))
    }

    @Test
    public void canConfigureMainGosuClosureSrcDirMultiple() {
        String dirAsString = 'path/to/POGOs'
        String anotherSource = 'some/more/gosu/filezz'
        project.sourceSets {
            main {
                gosu {
                    srcDir dirAsString
                    srcDir anotherSource
                }
            }
        }
        assertEquals(project.sourceSets.main.gosu.srcDirs, toLinkedSet(project.file('src/main/gosu'), project.file(dirAsString), project.file(anotherSource)))
    }

    @Test
    public void canConfigureMainGosuClosureSrcDirAndSrcDirs() {
        String dirAsString = 'path/to/POGOs'
        String anotherSource = 'some/more/gosu/filezz'
        String aThirdSource = 'formerly/gscript'
        project.sourceSets {
            main {
                gosu {
                    srcDir dirAsString
                    srcDirs anotherSource, aThirdSource
                }
            }
        }
        assertEquals(project.sourceSets.main.gosu.srcDirs, toLinkedSet(project.file('src/main/gosu'), project.file(dirAsString), project.file(anotherSource), project.file(aThirdSource)))
    }

    /** 
     * Get the default fork setting, then reverse it
     * Verify failOnError defaults to true, then reverse it
     */
    @Test
    public void canConfigureCompileOptionsForJava() {
        def isFork = project.tasks.compileJava.options.fork
        assertTrue(project.tasks.compileJava.options.failOnError)
        project.tasks.withType(JavaCompile.class).configureEach {
            options.fork = !isFork
            options.failOnError = false
        }
        assertEquals(project.tasks.compileJava.options.fork, !isFork)
        assertFalse(project.tasks.compileJava.options.failOnError)
    }

    /**
     * Get the default fork setting, then reverse it
     * Verify failOnError defaults to true, then reverse it
     */
    @Test
    public void canConfigureCompileOptionsForGosu() {
        def isFork = project.tasks.compileGosu.options.fork
        assertTrue(project.tasks.compileGosu.options.failOnError)
        project.tasks.withType(GosuCompile.class).configureEach {
            options.fork = !isFork
            options.failOnError = false
        }
        assertEquals(project.tasks.compileGosu.options.fork, !isFork)
        assertFalse(project.tasks.compileGosu.options.failOnError)
    }

}
