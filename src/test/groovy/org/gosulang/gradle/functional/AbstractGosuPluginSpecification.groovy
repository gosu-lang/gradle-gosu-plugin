package org.gosulang.gradle.functional

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractGosuPluginSpecification extends Specification implements MultiversionTestable {

    // These are the versions of gradle to iteratively test against
    // Locally, only test the latest.
    @Shared
    String[] gradleVersionsToTest = System.getenv().get('CI') != null ? getTestedVersions().plus(getGradleVersion()) : [getGradleVersion()]

    protected static final String LF = System.lineSeparator
    protected static final String FS = File.separator

    @Rule
    final TemporaryFolder testProjectDir = new TemporaryFolder()

    protected final URL _gosuVersionResource = this.class.classLoader.getResource("gosuVersion.txt")

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
                testCompile group: 'junit', name: 'junit', version: '4.12'
            }
            gosudoc {
                gosuDocOptions.verbose = true
            }
            """
        return buildFileContent
    }

    def setup() {
        testProjectDir.create()
        buildScript = testProjectDir.newFile('build.gradle')
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
