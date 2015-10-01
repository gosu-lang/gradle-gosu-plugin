package org.gosulang.gradle.tasks

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.util.stream.Collectors

class GosuRuntimeInferenceTest extends Specification {

    private final URL _pluginClasspathResource = getClass().getClassLoader().getResource("plugin-classpath.txt")
    private final URL _gosuVersionResource = getClass().getClassLoader().getResource("gosuVersion.txt")

    public final TemporaryFolder _testProjectDir = new TemporaryFolder()

    def setup() {
        _testProjectDir.create()
        
        def _buildFile = _testProjectDir.newFile("build.gradle");
        
        def gosuVersion = getGosuVersion(_gosuVersionResource)
        def buildFileContent = 
"""
buildscript {
    repositories {
//        maven { url 'https://oss.sonatype.org/content/repositories/snapshots/' }
        jcenter()
    }
    
    dependencies {
        classpath files(${getClasspathWithoutGosuJars()})
    }
}
apply plugin: 'org.gosu-lang.gosu'
repositories {
    mavenCentral()
}

dependencies {
    compile group: 'org.gosu-lang.gosu', name: 'gosu-core-api', version: '$gosuVersion'
    testCompile group: 'junit', name: 'junit', version: '4.11'
//    runtime group: 'org.gosu-lang.gosu', name: 'gosu-core', version: '$gosuVersion' //intentionally commenting-out to cause test failure
}
"""
        writeFile(_buildFile, buildFileContent)
        
        System.out.println("--- Dumping build.gradle ---")
        System.out.println(buildFileContent);
        System.out.println("--- Done dumping build.gradle ---")
        
        //make a POGO to compile
        String simplePogoContent = 'public class SimplePogo {}'
        File pogo = new File(_testProjectDir.newFolder('src', 'main', 'gosu'), 'SimplePogo.gs')
        pogo.getParentFile().mkdirs();
        writeFile(pogo, simplePogoContent);
    }
    
    def "Build throws if only one gosu jar is explicitly declared as a dependency"() {

        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(_testProjectDir.getRoot())
                .withArguments('clean', 'compileGosu', '-is')
                .build()
        
        then:
        UnexpectedBuildFailure ex = thrown()
        ex.message.contains('Cannot infer Gosu class path because both the Gosu Core API and Gosu Core Jars were not found.')

    }

    protected String getClasspathWithoutGosuJars() {
        URL url = _pluginClasspathResource
        List<String> pluginClasspathRaw = new BufferedReader(new FileReader(url.getFile())).lines().collect(Collectors.toList())
        List<String> elementsToRemove = pluginClasspathRaw.findAll { it.contains('/org.gosu-lang.gosu/gosu-core-api/') || 
                                                                     it.contains('/org.gosu-lang.gosu/gosu-core/') ||
                                                                     it.contains('/org.gosu-lang.gosu.managed/')}
        return "'" + String.join("', '", pluginClasspathRaw.minus(elementsToRemove)) + "'"; //wrap each entry in single quotes
    }

    protected static String getGosuVersion(URL url) throws IOException {
        return new BufferedReader(new FileReader(url.getFile())).lines().findFirst().get();
    }

    protected static void writeFile(File destination, String content) throws IOException {
        BufferedWriter output = null;
        try {
            output = new BufferedWriter(new FileWriter(destination));
            output.write(content);
        } finally {
            if (output != null) {
                output.close();
            }
        }
    }

}
