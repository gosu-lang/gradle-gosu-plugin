package org.gosulang.gradle.build

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

class VersionWriterTask extends DefaultTask {

    @InputFiles
    File propsFile = new File(project.rootDir, 'gradle.properties')

    @Input
    String propertyToRead

    @Input
    String fallbackValue = 'unused'

    @OutputDirectory
    File outputDir = project.file("$project.buildDir/$name")

    VersionWriterTask() {
        description = 'Takes an input String \'propertyToRead\', reads it from gradle.properties, and writes it to a text file in the outputDir'
    }

    @TaskAction
    void start() {
        outputDir.mkdir()
        def props = new Properties()
        props.load(new BufferedReader(new FileReader(propsFile)))
        project.file("$outputDir/${propertyToRead}.txt").text = props.getProperty(propertyToRead) ?: fallbackValue
    }

}
