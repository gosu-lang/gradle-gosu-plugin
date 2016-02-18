package org.gosulang.gradle.tasks.compile.daemon;

import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.daemon.CompileResult;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemon;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonFactory;
import org.gradle.api.internal.tasks.compile.daemon.DaemonForkOptions;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.CachingClassLoader;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.internal.classloader.DefaultClassLoaderFactory;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.MultiParentClassLoader;
import org.gradle.internal.classloader.MutableURLClassLoader;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.io.ClassLoaderObjectInputStream;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.util.GUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.Serializable;
import java.util.concurrent.Callable;

public class InProcessGosuCompilerDaemonFactory implements CompilerDaemonFactory, Factory<InProcessGosuCompilerDaemonFactory> {
  private final ClassLoaderFactory _classLoaderFactory;
  private /*final*/ File _gradleUserHomeDir;
  
  public InProcessGosuCompilerDaemonFactory(ClassLoaderFactory classLoaderFactory) {
    _classLoaderFactory = classLoaderFactory;
//    _gradleUserHomeDir = gradleUserHomeDir;
  }

  @Override
  public InProcessGosuCompilerDaemonFactory create() {
    return new InProcessGosuCompilerDaemonFactory(new DefaultClassLoaderFactory());
  }
  
  public InProcessGosuCompilerDaemonFactory addProjectContext(Project project) {
    _gradleUserHomeDir = project.getGradle().getGradleUserHomeDir();
    return this;
  }
  
  @Override
  public CompilerDaemon getDaemon( File workingDir, DaemonForkOptions forkOptions ) {
//    ClassLoaderFactory classLoaderFactory = new DefaultClassLoaderFactory();
    return new CompilerDaemon() {
      public <T extends CompileSpec> CompileResult execute( Compiler<T> compiler, T spec ) {
        ClassLoader gosuClassLoader = _classLoaderFactory.createIsolatedClassLoader(new DefaultClassPath(forkOptions.getClasspath()));
        FilteringClassLoader filteredGosu = _classLoaderFactory.createFilteringClassLoader(gosuClassLoader);
        for (String packageName : forkOptions.getSharedPackages()) {
          filteredGosu.allowPackage(packageName);
        }
        
        FilteringClassLoader loggingClassLoader = _classLoaderFactory.createFilteringClassLoader(compiler.getClass().getClassLoader());
        loggingClassLoader.allowPackage("org.slf4j");
        loggingClassLoader.allowClass(Logger.class);
        loggingClassLoader.allowClass(LogLevel.class);

        ClassLoader gosuAndLoggingClassLoader = new CachingClassLoader(new MultiParentClassLoader(loggingClassLoader, filteredGosu));

        ClassLoader workerClassLoader = new MutableURLClassLoader(gosuAndLoggingClassLoader, ClasspathUtil.getClasspath(compiler.getClass().getClassLoader()));

        try {
          byte[] serializedWorker = GUtil.serialize(new Worker<T>(compiler, spec, _gradleUserHomeDir));
          ClassLoaderObjectInputStream inputStream = new ClassLoaderObjectInputStream(new ByteArrayInputStream(serializedWorker), workerClassLoader);
          Callable<?> worker = (Callable<?>) inputStream.readObject();
          Object result = worker.call();
          byte[] serializedResult = GUtil.serialize(result);
          inputStream = new ClassLoaderObjectInputStream(new ByteArrayInputStream(serializedResult), getClass().getClassLoader());
          return (CompileResult) inputStream.readObject();
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
      }
    };
  }

  private static class Worker<T extends CompileSpec> implements Callable<Object>, Serializable {
    private final Compiler<T> compiler;
    private final T spec;
    private final File gradleUserHome;

    private Worker(Compiler<T> compiler, T spec, File gradleUserHome) {
      this.compiler = compiler;
      this.spec = spec;
      this.gradleUserHome = gradleUserHome;
    }

    public Object call() throws Exception {
      // We have to initialize this here because we're in an isolated classloader
      NativeServices.initialize(gradleUserHome);
      return new CompileResult(compiler.execute(spec).getDidWork(), null);
    }
  }
}
