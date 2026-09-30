package org.gosulang.gradle.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject

/**
 * Reads the property named by {@code propertyToRead} from {@code propsFile} and writes its value to
 * {@code <outputDir>/<propertyToRead>.txt}.
 *
 * All inputs are resolved at configuration time via {@link ProjectLayout}; the task action never touches
 * {@code Task.project}, which keeps it compatible with the configuration cache.
 */
abstract class VersionWriterTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    abstract RegularFileProperty getPropsFile()

    @Input
    abstract Property<String> getPropertyToRead()

    @Input
    abstract Property<String> getFallbackValue()

    @OutputDirectory
    abstract DirectoryProperty getOutputDir()

    @Inject
    abstract ProjectLayout getLayout()

    VersionWriterTask() {
        description = 'Takes an input String \'propertyToRead\', reads it from gradle.properties, and writes it to a text file in the outputDir'
        propsFile.convention(layout.projectDirectory.file('gradle.properties'))
        fallbackValue.convention('unused')
        outputDir.convention(layout.buildDirectory.dir(name))
    }

    @TaskAction
    void start() {
        File dir = outputDir.get().asFile
        dir.mkdirs()
        Properties props = new Properties()
        propsFile.get().asFile.withReader { props.load(it) }
        String key = propertyToRead.get()
        new File(dir, "${key}.txt").text = props.getProperty(key) ?: fallbackValue.get()
    }

}
