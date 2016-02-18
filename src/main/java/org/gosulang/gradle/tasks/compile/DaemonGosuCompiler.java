package org.gosulang.gradle.tasks.compile;

import org.gradle.api.internal.tasks.compile.daemon.AbstractDaemonCompiler;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonFactory;
import org.gradle.api.internal.tasks.compile.daemon.DaemonForkOptions;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DaemonGosuCompiler<T extends GosuCompileSpec> extends AbstractDaemonCompiler<T> {
  /**
   * SHARED_PACKAGES is a shitty name
   * Rather, this value will expose these packages and subpackages to the worker thread's classloader;
   * maps to FilteredClassLoader filteredApplication in ImplementationClassLoaderWorker
   */
//  private static final Iterable<String> SHARED_PACKAGES = Arrays.asList("jarjar.org.gradle", "org.slf4j", "com.google", "org.gradle", "org.gosulang", "com.sun.tools.javac");
  private static final Iterable<String> SHARED_PACKAGES = Arrays.asList("org.gosulang", "com.sun.tools.javac");
  private final Iterable<File> _gosuClasspath; //FIXME for serialization??
  
  public DaemonGosuCompiler( File daemonWorkingDir, Compiler<T> delegate, CompilerDaemonFactory compilerDaemonFactory, Iterable<File> gosuClasspath ) {
    super(daemonWorkingDir, delegate, compilerDaemonFactory);
    _gosuClasspath = gosuClasspath;
  }

  //TODO implement constructor
  
  @Override
  protected DaemonForkOptions toDaemonOptions( GosuCompileSpec spec ) {
    GosuForkOptions options = spec.getGosuCompileOptions().getForkOptions();
    List<File> classPath = new ArrayList<>();
    for(File file : _gosuClasspath) {
      classPath.add(file);
    }
    //TODO jam in this plugin FTW
    List<String> daemonJvmArgs = new ArrayList<>(options.getJvmArgs());
//    daemonJvmArgs.add("-cp");
//    daemonJvmArgs.add("/home/kmoore/.m2/repository/org/gosu-lang/gosu/gradle-gosu-plugin/0.1.4-SNAPSHOT/gradle-gosu-plugin-0.1.4-SNAPSHOT.jar");

    //classPath.add(new File("/home/kmoore/.m2/repository/org/gosu-lang/gosu/gradle-gosu-plugin/0.1.4-SNAPSHOT/gradle-gosu-plugin-0.1.4-SNAPSHOT.jar"));
//    classPath.add(new File("/home/kmoore/.gradle/wrapper/dists/gradle-2.9-all/1aw2ic01pldw5fkvoq6t1fsz4/gradle-2.9/lib/plugins/gradle-platform-base-2.9.jar"));
//    classPath.add(new File("/home/kmoore/.gradle/wrapper/dists/gradle-2.9-all/1aw2ic01pldw5fkvoq6t1fsz4/gradle-2.9/lib/plugins/gradle-language-jvm-2.9.jar"));
//    classPath.add(new File("/home/kmoore/.gradle/wrapper/dists/gradle-2.9-all/1aw2ic01pldw5fkvoq6t1fsz4/gradle-2.9/lib/gradle-core-2.9.jar"));
//    classPath.add(new File("/home/kmoore/.gradle/wrapper/dists/gradle-2.9-all/1aw2ic01pldw5fkvoq6t1fsz4/gradle-2.9/lib/slf4j-api-1.7.10.jar"));
//    classPath.add(new File("/home/kmoore/.gradle/wrapper/dists/gradle-2.9-all/1aw2ic01pldw5fkvoq6t1fsz4/gradle-2.9/lib/gradle-base-services-2.9.jar"));
//    classPath.add(new File("/home/kmoore/.gradle/wrapper/dists/gradle-2.9-all/1aw2ic01pldw5fkvoq6t1fsz4/gradle-2.9/lib/gradle-native-2.9.jar"));
//    classPath.add(new File("/home/kmoore/.gradle/wrapper/dists/gradle-2.9-all/1aw2ic01pldw5fkvoq6t1fsz4/gradle-2.9/lib/gradle-messaging-2.9.jar"));
//    classPath.add(new File("/home/kmoore/.gradle/wrapper/dists/gradle-2.9-all/1aw2ic01pldw5fkvoq6t1fsz4/gradle-2.9/lib/guava-jdk5-17.0.jar"));
//    classPath.add(new File("/home/kmoore/.gradle/wrapper/dists/gradle-2.9-all/1aw2ic01pldw5fkvoq6t1fsz4/gradle-2.9/lib/native-platform-0.10.jar"));
    return new DaemonForkOptions(options.getMemoryInitialSize(), options.getMemoryMaximumSize(), daemonJvmArgs, classPath, SHARED_PACKAGES);
  }

}
