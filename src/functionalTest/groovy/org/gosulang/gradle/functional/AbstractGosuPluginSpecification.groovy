package org.gosulang.gradle.functional

import org.gradle.util.VersionNumber
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractGosuPluginSpecification extends Specification implements MultiversionTestable {

    // These are the versions of gradle to iteratively test against
    // Locally, only test the latest.
    @Shared
    String[] gradleVersionsToTest = System.getenv().get('CI') != null ? getTestedVersions().plus(getGradleVersion()).sort() : [getGradleVersion()]

    protected static final String LF = System.lineSeparator
    protected static final String FS = File.separator

    @Rule
    TemporaryFolder testProjectDir = new TemporaryFolder()

    protected  URL _gosuVersionResource = this.class.classLoader.getResource("gosuVersion.txt")

    File buildScript
    Closure<List<String>> expectedOutputDir = { String gradleVersion ->
        List<String> retval = ['build', 'classes']
        if(VersionNumber.parse(gradleVersion) >= VersionNumber.parse('4.0')) {
            retval += 'gosu'
        }
        return retval
    }
    
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
                implementation group: 'org.gosu-lang.gosu', name: 'gosu-core-api', version: '$gosuVersion'
                testImplementation group: 'junit', name: 'junit', version: '4.12'
            }
            //compileGosu.gosuOptions.forkOptions.jvmArgs += ['-Xdebug', '-Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=y'] //debug on linux/OS X
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
     * @param An array of files and directories
     * @return Delimited String of the values, joined as suitable for use in a classpath statement
     */
    protected String asPath(String... values) {
        return String.join(FS, values)
    }

    /**
     * @param An iterable of files and directories
     * @return Delimited String of the values, joined as suitable for use in a classpath statement
     */
    protected String asPath(List<String> values) {
        return asPath(values.toArray(new String[0]))
    }
    
    /**
     * 
     * @param An iterable of directories
     * @return Delimited String of the values, joined as suitable for use in a package statement
     */
    protected String asPackage(String... values) {
        return String.join(".", values)
    }
}
