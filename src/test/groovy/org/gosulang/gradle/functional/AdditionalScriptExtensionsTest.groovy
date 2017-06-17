package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.VersionNumber
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Unroll
class AdditionalScriptExtensionsTest extends AbstractGosuPluginSpecification {

    File simplePogo, dummyRuleSet, dummyRule, orderFile, shouldBeIgnored
    String apiAwareOutputDir

    /**
     * super#setup is invoked automatically
     * @return
     */
    def setup() {
        simplePogo = new File(testProjectDir.newFolder('src', 'main', 'gosu',), 'SimplePogo.gs')
        File ruleSetFolder = testProjectDir.newFolder('src', 'main', 'config', 'rules', 'DummyRuleSet_dir')
        File rulesRootFolder = ruleSetFolder.parentFile
        dummyRuleSet = new File(rulesRootFolder, 'DummyRuleSet.grs')
        dummyRule = new File(ruleSetFolder, 'DummyRule.gr')
        orderFile = new File(ruleSetFolder, 'order.txt')
        shouldBeIgnored = new File(rulesRootFolder, 'ShouldBeIgnored.file')
    }

    def 'Compile a non-standard extension [Gradle #gradleVersion]'() {
        given:
        buildScript << getBasicBuildScriptForTesting() + """
            sourceSets.main {
              gosu {
                srcDir 'src/main/config'
                filter.include 'rules/**/*.grs', 'rules/**/*.gr'
              }
            }
            task copyRuleMetadata(type: Copy) {
              from 'src/main/config'
              into $apiAwareOutputDir
              include 'rules/**/order.txt'
            }
            tasks.processResources.dependsOn(tasks.copyRuleMetadata)
        """

        simplePogo << """
            public class SimplePogo {
              //do nothing
            }
        """
        
        dummyRuleSet << """
            package rules
            
            class DummyRuleSet {
            
              static function invoke(bean : Object) : Object {
                return invoke( new Object(), bean )
              }

              static function invoke(exeSession : Object, bean : Object) : Object {
                return new Object()
              }
            }
        """
        
        dummyRule << """
            package rules.DummyRuleSet_dir
            
            internal class DummyRule {
            
              static function doCondition(something : Object) : boolean { 
                return false 
              }
              static function doAction(something : Object, action : Object) {
                //do nothing
              }
            }
        """
        
        orderFile << 'DummyRule'

        shouldBeIgnored << 'Dummy file contents which will be ignored by explicit ant selector include/exclude patterns'
        
        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('clean', 'classes', '-is')
                .withGradleVersion(gradleVersion)

        BuildResult result = runner.build()

        then:
        result.output.contains('Initializing gosuc compiler')
        result.task(':compileGosu').outcome == SUCCESS
        result.task(':copyRuleMetadata').outcome == SUCCESS
        result.task(':classes').outcome == SUCCESS

        //did we actually compile anything?
        new File(testProjectDir.root, asPath(expectedOutputDir(gradleVersion) + ['main', 'SimplePogo.class'])).exists()
        new File(testProjectDir.root, asPath(expectedOutputDir(gradleVersion) + ['main', 'rules', 'DummyRuleSet.class'])).exists()
        new File(testProjectDir.root, asPath(expectedOutputDir(gradleVersion) + ['main', 'rules', 'DummyRuleSet_dir', 'DummyRule.class'])).exists()
        new File(testProjectDir.root, asPath(expectedOutputDir(gradleVersion) + ['main', 'rules', 'DummyRuleSet_dir', 'order.txt'])).exists()

        where:
        gradleVersion << gradleVersionsToTest
        apiAwareOutputDir = VersionNumber.parse(gradleVersion) >= VersionNumber.parse('4.0') ? 'sourceSets.main.gosu.outputDir' : 'sourceSets.main.output.classesDir'

    }

}
