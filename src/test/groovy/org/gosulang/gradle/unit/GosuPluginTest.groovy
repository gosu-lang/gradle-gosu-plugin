package org.gosulang.gradle.unit

import org.gosulang.gradle.GosuPlugin
import org.gosulang.gradle.tasks.compile.GosuCompile
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.configurations.Configurations
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.internal.DefaultJavaPluginConvention
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.reflect.Instantiator
import org.gradle.api.model.ObjectFactory
import org.gradle.testfixtures.ProjectBuilder
import org.hamcrest.Matchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import java.util.concurrent.Callable

import static org.gradle.util.WrapUtil.toLinkedSet
import static org.hamcrest.Matchers.empty
import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.instanceOf
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
    private ObjectFactory instantiator
    private JavaPluginConvention convention

    @Before
    public void applyPlugin() throws IOException {
        project = createRootProject()
        instantiator = ((ProjectInternal) project).services.get(ObjectFactory)
        project.pluginManager.apply(JavaPlugin)
        convention = new DefaultJavaPluginConvention(
                ((ProjectInternal) project), instantiator)
 //       convention = new JavaPluginConvention(((ProjectInternal) project), instantiator)
        project.pluginManager.apply(GosuPlugin)
    }

    @Test
    public void appliesTheJavaPluginToTheProject() {
        assertTrue(project.plugins.hasPlugin(JavaPlugin))
    }

    @Test
    public void addsGosuConfigurationToTheProject() {
        def configuration = project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom), Matchers.emptyIterable())
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)
    }

    @Test
    public void addsGosuConventionToEachSourceSet() {
        def sourceSet = project.sourceSets.main
        assertThat(sourceSet.gosu.displayName, equalTo('main Gosu source'))
        assertThat(sourceSet.gosu.srcDirs, equalTo(toLinkedSet(project.file('src/main/gosu'))))

        sourceSet = project.sourceSets.test
        assertThat(sourceSet.gosu.displayName, equalTo('test Gosu source'))
        assertThat(sourceSet.gosu.srcDirs, equalTo(toLinkedSet(project.file('src/test/gosu'))))
    }

    @Test
    public void addsCompileTaskForEachSourceSet() {
        def task = project.tasks['compileGosu']
        assertThat(task, instanceOf(GosuCompile))
        assertThat(task.description, equalTo('Compiles the main Gosu source.'))
        assertTrue(task.dependsOn.contains(JavaPlugin.COMPILE_JAVA_TASK_NAME))

        task = project.tasks['compileTestGosu']
        assertThat(task, instanceOf(GosuCompile))
        assertThat(task.description, equalTo('Compiles the test Gosu source.'))
        assertTrue(task.dependsOn.contains(JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME))
        //assertTrue(task.dependsOn.contains(JavaPlugin.CLASSES_TASK_NAME)) //TODO failing; do we care?
    }

    @Test
    public void canConfigureSourceSets() {
        File dir = new File('classes-dir')
        convention.sourceSets {
            main {
                System.out.println(output.classesDirs.asPath)
                output.addClassesDir ( new Callable() {
                    public Object call() {
                        return dir;
                    } })
            }
        }
        assert(convention.sourceSets.main.output.classesDirs.containsAll(project.file(dir)))
   //     assertThat(convention.sourceSets.main.output.classesDirs.singleFile, equalTo(project.file(dir)))

//        main {
//                output.classesDir = dir
//            }
//        }
//        assertThat(convention.sourceSets.main.output.classesDir, equalTo(project.file(dir)))
    }

    @Test
    public void canConfigureMainGosuClosure() {
        File dir = new File('path/to/POGOs')
        project.sourceSets {
            main {
                gosu.srcDirs = [ dir ] //gosu typeis org.gradle.api.internal.file.DefaultSourceDirectorySet
            }
        }
        assertThat(project.sourceSets.main.gosu.srcDirs, equalTo(toLinkedSet(project.file(dir))))
    }

    @Test
    public void canConfigureMainGosuClosureSrcDirsSingleArg() {
        String dirAsString = 'path/to/POGOs'
        project.sourceSets {
            main {
                gosu {
                    srcDirs dirAsString //gosu typeis org.gradle.api.internal.file.DefaultSourceDirectorySet
                }
            }
        }
        assertThat(project.sourceSets.main.gosu.srcDirs, equalTo(toLinkedSet(project.file('src/main/gosu'), project.file(dirAsString))))
    }

    @Test
    public void canConfigureMainGosuClosureSrcDirsMultipleArg() {
        String dirAsString = 'path/to/POGOs'
        String anotherSource = 'some/more/gosu/files'
        project.sourceSets {
            main {
                gosu {
                    srcDirs dirAsString, anotherSource
                }
            }
        }
        assertThat(project.sourceSets.main.gosu.srcDirs, equalTo(toLinkedSet(project.file('src/main/gosu'), project.file(dirAsString), project.file(anotherSource))))
    }

    @Test
    public void canConfigureMainGosuClosureSrcDirSingular() {
        String dirAsString = 'path/to/POGOs'
        project.sourceSets {
            main {
                gosu {
                    srcDir dirAsString //gosu typeis org.gradle.api.internal.file.DefaultSourceDirectorySet
                }
            }
        }
        assertThat(project.sourceSets.main.gosu.srcDirs, equalTo(toLinkedSet(project.file('src/main/gosu'), project.file(dirAsString))))
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
        assertThat(project.sourceSets.main.gosu.srcDirs, equalTo(toLinkedSet(project.file('src/main/gosu'), project.file(dirAsString), project.file(anotherSource))))
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
        assertThat(project.sourceSets.main.gosu.srcDirs, equalTo(toLinkedSet(project.file('src/main/gosu'), project.file(dirAsString), project.file(anotherSource), project.file(aThirdSource))))
    }

    /** 
     * Get the default fork setting, then reverse it
     * Verify failOnError defaults to true, then reverse it
     */
    @Test
    public void canConfigureCompileOptionsForJava() {
        def isFork = project.tasks.compileJava.options.fork
        assertThat(project.tasks.compileJava.options.failOnError, equalTo(true))
        project.tasks.withType(JavaCompile.class) {
            options.fork = !isFork
            options.failOnError = false
        }
        assertThat(project.tasks.compileJava.options.fork, equalTo(!isFork))
        assertThat(project.tasks.compileJava.options.failOnError, equalTo(false)) 
    }

    /**
     * Get the default fork setting, then reverse it
     * Verify failOnError defaults to true, then reverse it
     */
    @Test
    public void canConfigureCompileOptionsForGosu() {
        def isFork = project.tasks.compileGosu.options.fork
        assertThat(project.tasks.compileGosu.options.failOnError, equalTo(true))
        project.tasks.withType(GosuCompile.class) {
            options.fork = !isFork
            options.failOnError = false
        }
        assertThat(project.tasks.compileGosu.options.fork, equalTo(!isFork))
        assertThat(project.tasks.compileGosu.options.failOnError, equalTo(false))
    }

}
