# Gradle support for Gosu

A Gradle plugin, similar to the groovy and scala plugins for Gradle.

Specifically, this adds compileGosu and compileTestGosu tasks. These tasks are dependent on the compileJava and compileTestJava tasks, respectively.

Java 8 is required.

Build status: [![Circle CI](https://circleci.com/gh/gosu-lang/gradle-gosu-plugin/tree/master.svg?style=svg)](https://circleci.com/gh/gosu-lang/gradle-gosu-plugin/tree/master)

## Why would I use this?

1. As with Maven's surefire plugin, Gradle's test runner looks for compiled .class files in the build directory.  Using this plugin allows test execution outside of the IDE, such as with a build/CI server.
2. Standardizes source roots. 
  * When applying the plugin to a project in IntelliJ, the default source/test roots are automatically created and marked as source root/test root appropriately.
  * Of course, these defaults are configurable using the sourceSets DSL:
  
```  
sourceSets {
    main {
        gosu {
            exclude 'example/**'
        }
    }
}

compileGosu {
    gosuOptions.checkedArithmetic = true
    gosuOptions.failOnError = false
}
```

## How do I use this?

The latest release version, and instructions to apply it, is available here: https://plugins.gradle.org/plugin/org.gosu-lang.gosu

It is now necessary to create a single compile-time dependency on the gosu-core-api JAR.  Runtime and other dependencies are automatically inferred and applied by the plugin.

Additionally, snapshots are available from https://oss.sonatype.org/content/repositories/snapshots/org/gosu-lang/gosu/gradle-gosu-plugin/

### Changelog

See **[CHANGELOG.md](https://github.com/gosu-lang/gradle-gosu-plugin/blob/master/CHANGELOG.md)**