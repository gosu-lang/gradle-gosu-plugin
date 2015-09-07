# Gradle support for Gosu

A Gradle plugin, similar to the groovy and scala plugins for Gradle.

Specifically, this adds compileGosu and compileTestGosu tasks. These tasks are dependent on the compileJava and compileTestJava tasks, respectively.

Java 8 is required.

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
```

## How do I use this?

The latest release version, and instructions to apply it, is available here: https://plugins.gradle.org/plugin/org.gosu-lang.gosu

It is not necessary to create any other dependencies on Gosu JARs; the plugin automates this process.

Additionally, snapshots are available from http://gosu-lang.org/nexus/content/repositories/snapshots/org/gosu-lang/gosu/gradle-gosu-plugin/

### Build status
[![Circle CI](https://circleci.com/gh/gosu-lang/gradle-gosu-plugin/tree/master.svg?style=svg)](https://circleci.com/gh/gosu-lang/gradle-gosu-plugin/tree/master)

### Changelog

#### 0.1-alpha
Initial release, based on Gosu 1.7. Future revisions will be aligned with the version of Gosu they import.