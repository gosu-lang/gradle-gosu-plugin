# Gradle support for Gosu

A Gradle plugin, similar to the groovy and scala plugins for Gradle.

Specifically, this adds compileGosu and compileTestGosu tasks. These tasks are dependent on the compileJava and compileTestJava tasks, respectively.

Java 8 is required, as is Gosu version 1.13.9+ or 1.14.2+. Gosu 1.14 and 1.14.1 are not supported.

Build status: [![Circle CI](https://circleci.com/gh/gosu-lang/gradle-gosu-plugin/tree/master.svg?style=svg)](https://circleci.com/gh/gosu-lang/gradle-gosu-plugin/tree/master)

## Why would I use this?

1. As with Maven's surefire plugin, Gradle's test runner looks for compiled .class files in the build directory. Using this plugin allows test execution outside of the IDE, such as with a build/CI server.
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

It is now necessary to create a single compile-time dependency on the gosu-core-api JAR. Runtime and other dependencies are automatically inferred and applied by the plugin.

Additionally, snapshots are available from https://oss.sonatype.org/content/repositories/snapshots/org/gosu-lang/gosu/gradle-gosu-plugin/

## Configurable Options

Here are the most relevant set of configurations, with default values listed where appropriate.

The list is not exhaustive. 
Compilation tasks extend `org.gradle.api.tasks.compile.AbstractCompile`, 
while `gosudoc` extends `org.gradle.api.tasks.SourceTask`. 
Additional configuration from these parent Task types may be available.


```

sourceSets {
  main {
    gosu {
      srcDirs = ['src/main/gosu']
      filter.include '**/*.gs', '**/*.gsx', '**/*.gst', '**/*.gsp'
      filter.exclude []
    }  
  }
  test {
    gosu {
      srcDirs = ['src/test/gosu']
      filter.include '**/*.gs', '**/*.gsx', '**/*.gst', '**/*.gsp'
      filter.exclude []
    }  
  }
}

compileGosu { //Task type: org.gosulang.gradle.tasks.compile.GosuCompile
  options.warning = true
  gosuOptions.checkedArithmetic = false
  gosuOptions.failOnError = true
  gosuOptions.verbose = false
  gosuOptions.fork = true
  gosuOptions.forkOptions.with {
    memoryInitialSize = '' //'128m', '1g', etc.
    memoryMaximumSize = ''
    jvmArgs = [] //empty by default, but JAVA_OPTS environment var will be honored
    //debugging examples:
    //jvmArgs += ['-Xdebug', '-Xrunjdwp:transport=dt_shmem,address=gosuc,server=y,suspend=y'] //debug on windows
    //jvmArgs += ['-Xdebug', '-Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=y'] //debug on linux/OS X
  }
}

compileTestGosu {
  //same options as compileGosu task
}

gosudoc { // Task type: org.gosulang.gradle.tasks.gosudoc.GosuDoc
  include [sourceSet.main.output]
  exclude []
  gosuDocOptions.forkOptions.with {
    memoryInitialSize = '' //'128m', '1g', etc.
    memoryMaximumSize = ''
    jvmArgs = [] //empty by default, but JAVA_OPTS environment var will be honored
    //debugging examples:
    //jvmArgs += ['-Xdebug', '-Xrunjdwp:transport=dt_shmem,address=gosudoc,server=y,suspend=y'] //debug on windows
    //jvmArgs += ['-Xdebug', '-Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=y'] //debug on linux/OS X
  }
}
```

### Changelog

See **[CHANGELOG.md](https://github.com/gosu-lang/gradle-gosu-plugin/blob/master/CHANGELOG.md)**