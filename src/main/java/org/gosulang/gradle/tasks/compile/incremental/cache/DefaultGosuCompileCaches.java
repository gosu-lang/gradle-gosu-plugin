package org.gosulang.gradle.tasks.compile.incremental.cache;

import org.gosulang.gradle.tasks.compile.incremental.recomp.PreviousCompilationData;
import org.gosulang.gradle.tasks.compile.incremental.recomp.PreviousCompilationStore;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassAnalysisCache;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassAnalysisSerializer;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.DefaultClassAnalysisCache;
import org.gradle.api.internal.tasks.compile.incremental.cache.UserHomeScopedCompileCaches;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntrySnapshotCache;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntrySnapshotData;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntrySnapshotDataSerializer;
import org.gradle.api.internal.tasks.compile.incremental.classpath.DefaultClasspathEntrySnapshotCache;
import org.gradle.api.internal.tasks.compile.incremental.classpath.SplitClasspathEntrySnapshotCache;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.snapshot.FileSystemSnapshotter;
import org.gradle.internal.snapshot.WellKnownFileLocations;

import java.io.Closeable;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultGosuCompileCaches implements GosuCompileCaches, Closeable {
  private final ClassAnalysisCache classAnalysisCache;
  private final ClasspathEntrySnapshotCache classpathEntrySnapshotCache;
  private final PersistentCache cache;
  private final PersistentIndexedCache<String, PreviousCompilationData> previousCompilationCache;

  public DefaultGosuCompileCaches(FileSystemSnapshotter fileSystemSnapshotter,
                                  UserHomeScopedCompileCaches userHomeScopedCompileCaches,
                                  CacheRepository cacheRepository,
                                  Gradle gradle,
                                  InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory,
                                  WellKnownFileLocations fileLocations,
                                  StringInterner interner) {
    cache = cacheRepository
        .cache(gradle, "javaCompile")
        .withDisplayName("Java compile cache")
        .withLockOptions(mode(FileLockManager.LockMode.None)) // Lock on demand
        .open();
    PersistentIndexedCacheParameters<HashCode, ClassAnalysis> classCacheParameters = PersistentIndexedCacheParameters.of("classAnalysis", new HashCodeSerializer(), new ClassAnalysisSerializer(interner))
        .withCacheDecorator(inMemoryCacheDecoratorFactory.decorator(400000, true));
    this.classAnalysisCache = new DefaultClassAnalysisCache(cache.createCache(classCacheParameters));

    PersistentIndexedCacheParameters<HashCode, ClasspathEntrySnapshotData> jarCacheParameters = PersistentIndexedCacheParameters.of("jarAnalysis", new HashCodeSerializer(), new ClasspathEntrySnapshotDataSerializer(interner))
        .withCacheDecorator(inMemoryCacheDecoratorFactory.decorator(20000, true));
    this.classpathEntrySnapshotCache = new SplitClasspathEntrySnapshotCache(fileLocations, userHomeScopedCompileCaches.getClasspathEntrySnapshotCache(), new DefaultClasspathEntrySnapshotCache(fileSystemSnapshotter, cache.createCache(jarCacheParameters)));

    PersistentIndexedCacheParameters<String, PreviousCompilationData> previousCompilationCacheParameters = PersistentIndexedCacheParameters.of("taskHistory", String.class, new PreviousCompilationData.Serializer())
        .withCacheDecorator(inMemoryCacheDecoratorFactory.decorator(2000, false));
    previousCompilationCache = cache.createCache(previousCompilationCacheParameters);
  }

  @Override
  public void close() {
    cache.close();
  }

  @Override
  public ClassAnalysisCache getClassAnalysisCache() {
    return classAnalysisCache;
  }

  @Override
  public ClasspathEntrySnapshotCache getClasspathEntrySnapshotCache() {
    return classpathEntrySnapshotCache;
  }

  @Override
  public PreviousCompilationStore createPreviousCompilationStore(String taskPath) {
    return new PreviousCompilationStore(taskPath, previousCompilationCache);
  }
}
