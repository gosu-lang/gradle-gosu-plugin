/*
 * Copyright 2013 the original author or authors.
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
import org.gosulang.gradle.tasks.compile.incremental.cache.TaskScopedCompileCaches;
import org.gosulang.gradle.tasks.compile.incremental.recomp.PreviousCompilation;
import org.gosulang.gradle.tasks.compile.incremental.recomp.PreviousCompilationData;
import org.gosulang.gradle.tasks.compile.incremental.recomp.RecompilationSpecProvider;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshotMaker;
import org.gradle.api.internal.tasks.compile.incremental.recomp.PreviousCompilationOutputAnalyzer;
import org.gradle.language.base.internal.compile.Compiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorates a non-incremental Gosu compiler (like gosuc) so that it can be invoked incrementally.
 */
public class IncrementalCompilerDecorator<T extends GosuCompileSpec> {

    private static final Logger LOG = LoggerFactory.getLogger(IncrementalCompilerDecorator.class);
    private final ClasspathSnapshotMaker classpathSnapshotMaker;
    private final TaskScopedCompileCaches compileCaches;
    private final CleaningGosuCompilerSupport<T> cleaningCompiler;
    private final Compiler<T> rebuildAllCompiler;
    private final PreviousCompilationOutputAnalyzer previousCompilationOutputAnalyzer;

    public IncrementalCompilerDecorator(ClasspathSnapshotMaker classpathSnapshotMaker,
                                        TaskScopedCompileCaches compileCaches,
                                        CleaningGosuCompilerSupport<T> cleaningCompiler,
                                        Compiler<T> rebuildAllCompiler,
                                        PreviousCompilationOutputAnalyzer previousCompilationOutputAnalyzer) {
        this.classpathSnapshotMaker = classpathSnapshotMaker;
        this.compileCaches = compileCaches;
        this.cleaningCompiler = cleaningCompiler;
        this.rebuildAllCompiler = rebuildAllCompiler;
        this.previousCompilationOutputAnalyzer = previousCompilationOutputAnalyzer;
    }

    public Compiler<T> prepareCompiler(RecompilationSpecProvider recompilationSpecProvider) {
        Compiler<T> compiler = getCompiler(recompilationSpecProvider);
        return new IncrementalResultStoringCompiler<T>(compiler, classpathSnapshotMaker, compileCaches.getPreviousCompilationStore());
    }

    private Compiler<T> getCompiler(RecompilationSpecProvider recompilationSpecProvider) {
        if (!recompilationSpecProvider.isIncremental()) {
            LOG.info("Full recompilation is required because no incremental change information is available. This is usually caused by clean builds or changing compiler arguments.");
            return rebuildAllCompiler;
        }

        PreviousCompilationData data = compileCaches.getPreviousCompilationStore().get();
        if (data == null) {
            LOG.info("Full recompilation is required because no previous compilation result is available.");
            return rebuildAllCompiler;
        }

        PreviousCompilation previousCompilation = new PreviousCompilation(data, compileCaches.getClasspathEntrySnapshotCache(), previousCompilationOutputAnalyzer);
        return new SelectiveCompiler<T>(previousCompilation, cleaningCompiler, rebuildAllCompiler, recompilationSpecProvider, classpathSnapshotMaker);
    }
}
