/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gosulang.gradle.tasks.compile.incremental;

import org.gosulang.gradle.tasks.compile.CleaningGosuCompilerSupport;
import org.gosulang.gradle.tasks.compile.GosuCompileSpec;
import org.gosulang.gradle.tasks.compile.incremental.analyzer.CachingClassDependenciesAnalyzer;
import org.gosulang.gradle.tasks.compile.incremental.analyzer.ClassAnalysisCache;
import org.gosulang.gradle.tasks.compile.incremental.analyzer.DefaultClassDependenciesAnalyzer;
import org.gosulang.gradle.tasks.compile.incremental.cache.GosuCompileCaches;
import org.gosulang.gradle.tasks.compile.incremental.cache.TaskScopedCompileCaches;
import org.gosulang.gradle.tasks.compile.incremental.classpath.CachingClasspathEntrySnapshotter;
import org.gosulang.gradle.tasks.compile.incremental.classpath.ClasspathEntrySnapshotCache;
import org.gosulang.gradle.tasks.compile.incremental.classpath.ClasspathEntrySnapshotter;
import org.gosulang.gradle.tasks.compile.incremental.classpath.ClasspathSnapshotFactory;
import org.gosulang.gradle.tasks.compile.incremental.classpath.ClasspathSnapshotMaker;
import org.gosulang.gradle.tasks.compile.incremental.recomp.PreviousCompilationOutputAnalyzer;
import org.gosulang.gradle.tasks.compile.incremental.recomp.PreviousCompilationStore;
import org.gosulang.gradle.tasks.compile.incremental.recomp.RecompilationSpecProvider;
import org.gosulang.gradle.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.snapshot.FileSystemSnapshotter;
import org.gradle.language.base.internal.compile.Compiler;

public class IncrementalCompilerFactory {
    private final FileOperations fileOperations;
    private final StreamHasher streamHasher;
    private final GosuCompileCaches gosuCompileCaches;
    private final BuildOperationExecutor buildOperationExecutor;
    private final StringInterner interner;
    private final FileSystemSnapshotter fileSystemSnapshotter;
    private final FileHasher fileHasher;

    public IncrementalCompilerFactory(FileOperations fileOperations, StreamHasher streamHasher, GosuCompileCaches gosuCompileCaches, BuildOperationExecutor buildOperationExecutor, StringInterner interner, FileSystemSnapshotter fileSystemSnapshotter, FileHasher fileHasher) {
        this.fileOperations = fileOperations;
        this.streamHasher = streamHasher;
        this.gosuCompileCaches = gosuCompileCaches;
        this.buildOperationExecutor = buildOperationExecutor;
        this.interner = interner;
        this.fileSystemSnapshotter = fileSystemSnapshotter;
        this.fileHasher = fileHasher;
    }

    public <T extends GosuCompileSpec> Compiler<T> makeIncremental(CleaningGosuCompilerSupport<T> cleaningGosuCompiler, String taskPath, FileTree sources, RecompilationSpecProvider recompilationSpecProvider) {
        TaskScopedCompileCaches compileCaches = createCompileCaches(taskPath);
        Compiler<T> rebuildAllCompiler = createRebuildAllCompiler(cleaningGosuCompiler, sources);
        ClassDependenciesAnalyzer analyzer = new CachingClassDependenciesAnalyzer(new DefaultClassDependenciesAnalyzer(interner), compileCaches.getClassAnalysisCache());
        ClasspathEntrySnapshotter classpathEntrySnapshotter = new CachingClasspathEntrySnapshotter(fileHasher, streamHasher, fileSystemSnapshotter, analyzer, compileCaches.getClasspathEntrySnapshotCache(), fileOperations);
        ClasspathSnapshotMaker classpathSnapshotMaker = new ClasspathSnapshotMaker(new ClasspathSnapshotFactory(classpathEntrySnapshotter, buildOperationExecutor));
        PreviousCompilationOutputAnalyzer previousCompilationOutputAnalyzer = new PreviousCompilationOutputAnalyzer(fileHasher, streamHasher, analyzer, fileOperations);
        IncrementalCompilerDecorator<T> incrementalSupport = new IncrementalCompilerDecorator<T>(classpathSnapshotMaker, compileCaches, cleaningGosuCompiler, rebuildAllCompiler, previousCompilationOutputAnalyzer);
        return incrementalSupport.prepareCompiler(recompilationSpecProvider);
    }

    private TaskScopedCompileCaches createCompileCaches(String path) {
        final PreviousCompilationStore previousCompilationStore = gosuCompileCaches.createPreviousCompilationStore(path);
        return new TaskScopedCompileCaches() {
            @Override
            public ClassAnalysisCache getClassAnalysisCache() {
                return gosuCompileCaches.getClassAnalysisCache();
            }

            @Override
            public ClasspathEntrySnapshotCache getClasspathEntrySnapshotCache() {
                return gosuCompileCaches.getClasspathEntrySnapshotCache();
            }

            @Override
            public PreviousCompilationStore getPreviousCompilationStore() {
                return previousCompilationStore;
            }

        };
    }

    private <T extends GosuCompileSpec> Compiler<T> createRebuildAllCompiler(final CleaningGosuCompilerSupport<T> cleaningGosuCompiler, final FileTree sourceFiles) {
        return spec -> {
            spec.setSource(sourceFiles);
            return cleaningGosuCompiler.execute(spec);
        };
    }
}
