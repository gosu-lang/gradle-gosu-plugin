package org.gosulang.gradle.tasks

import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.nativeintegration.services.NativeServices
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat

class DefaultGosuSourceSetTest {

    @Rule
    public final TemporaryFolder _testProjectDir = new TemporaryFolder()

    @Before
    void before() {
        NativeServices.initialize(_testProjectDir.root)
    }

    private final DefaultGosuSourceSet sourceSet = new DefaultGosuSourceSet("<set-display-name>", [resolve: { it as File }] as FileResolver)

    @Test
    public void defaultValues() {
        assertThat(sourceSet.gosu, instanceOf(DefaultSourceDirectorySet))
        assertThat(sourceSet.gosu, emptyIterable())
        assertThat(sourceSet.gosu.displayName, equalTo('<set-display-name> Gosu source'))
        assertThat(sourceSet.gosu.filter.includes, equalTo(['**/*.gs', '**/*.gsx', '**/*.gst'] as Set))
        assertThat(sourceSet.gosu.filter.excludes, empty())

//        assertThat(sourceSet.allGosu, instanceOf(DefaultSourceDirectorySet))
//        assertThat(sourceSet.allGosu, emptyIterable())
//        assertThat(sourceSet.allGosu.displayName, equalTo('<set-display-name> Gosu source'))
//        assertThat(sourceSet.allGosu.source, hasItem(sourceSet.gosu))
//        assertThat(sourceSet.allGosu.filter.includes, equalTo(['**/*.gs', '**/*.gsx', '**/*.gst'] as Set))
//        assertThat(sourceSet.allGosu.filter.excludes, empty())
    }

    @Test
    public void canConfigureGosuSource() {
        sourceSet.gosu { srcDir 'src/somepathtogosu' }
        assertThat(sourceSet.gosu.srcDirs, equalTo([new File('src/somepathtogosu').canonicalFile] as Set))
    }

}
