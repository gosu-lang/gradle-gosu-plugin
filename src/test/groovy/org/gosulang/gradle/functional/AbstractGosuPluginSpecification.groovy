package org.gosulang.gradle.functional

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Shared
import spock.lang.Specification

class AbstractGosuPluginSpecification extends Specification {

    private final static String latestGradleVersion = '2.9'
    
    // These are the versions of gradle to iteratively test against
    @Shared
    String[] gradleVersionsToTest = [latestGradleVersion].plus(System.getenv().get('CIRCLECI') == null ? ['2.8'] : [])
    
    protected static final String LF = System.lineSeparator
    protected static final String FS = File.separator

    @Rule
    final TemporaryFolder testProjectDir = new TemporaryFolder()

    protected final URL _pluginClasspathResource = this.class.classLoader.getResource("plugin-classpath.txt")
    protected final URL _gosuVersionResource = this.class.classLoader.getResource("gosuVersion.txt")

    List<File> pluginClasspath
    File buildScript
    
    protected String getBasicBuildScriptForTesting() {
        String gosuVersion = this.gosuVersion
        String buildFileContent =
            """
            plugins {
                id 'org.gosu-lang.gosu'
            }
            repositories {
                mavenLocal()
                mavenCentral()
                maven {
                    url 'https://oss.sonatype.org/content/repositories/snapshots' //for Gosu snapshot builds
                }
            }
            dependencies {
                compile group: 'org.gosu-lang.gosu', name: 'gosu-core-api', version: '$gosuVersion'
                testCompile group: 'junit', name: 'junit', version: '4.11'
            }
            """
        return buildFileContent
    }

    def setup() {
        testProjectDir.create()
        buildScript = testProjectDir.newFile('build.gradle')
        pluginClasspath = getClasspath()
    }

    protected List<File> getClasspath() throws IOException {
        return getClasspath(_pluginClasspathResource)
    }

    protected List<File> getClasspath(URL url) throws IOException {
        return url.readLines().collect { new File( it ) }
    }

    protected String getGosuVersion() {
        return getGosuVersion(_gosuVersionResource)
    }

    protected String getGosuVersion(URL url) {
        return new BufferedReader(new FileReader(url.file)).lines().findFirst().get()
    }

    /**
     * @param An iterable of files and directories
     * @return Delimited String of the values, joined as suitable for use in a classpath statement
     */
    protected String asPath(String... values) {
        return String.join(FS, values);
    }

    /**
     * 
     * @param An iterable of directories
     * @return Delimited String of the values, joined as suitable for use in a package statement
     */
    protected String asPackage(String... values) {
        return String.join(".", values);
    }
}
