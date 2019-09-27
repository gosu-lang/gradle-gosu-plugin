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


import org.gosulang.gradle.tasks.compile.GosuCompileSpec;
import org.gosulang.gradle.tasks.compile.incremental.recomp.PreviousCompilationData;
import org.gradle.api.internal.tasks.compile.incremental.RecompilationNotNecessary;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshotData;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshotProvider;
import org.gradle.api.tasks.WorkResult;
import org.gradle.cache.internal.Stash;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Stores the incremental class dependency analysis after compilation has finished.
 */
class IncrementalResultStoringCompiler<T extends GosuCompileSpec> implements Compiler<T> {

    private final Compiler<T> delegate;
    private final ClasspathSnapshotProvider classpathSnapshotProvider;
    private final Stash<PreviousCompilationData> stash;

    IncrementalResultStoringCompiler(Compiler<T> delegate, ClasspathSnapshotProvider classpathSnapshotProvider, Stash<PreviousCompilationData> stash) {
        this.delegate = delegate;
        this.classpathSnapshotProvider = classpathSnapshotProvider;
        this.stash = stash;
    }

    @Override
    public WorkResult execute(T spec) {
        WorkResult result = delegate.execute(spec);
        if (result instanceof RecompilationNotNecessary) {
            return result;
        }
        storeResult(spec, result);
        return result;
    }

    // TODO: should result be stored?
    private void storeResult(GosuCompileSpec spec, WorkResult result) {
        ClasspathSnapshotData classpathSnapshot = classpathSnapshotProvider.getClasspathSnapshot(concat(spec.getClasspath(), spec.getModulePath())).getData();
        PreviousCompilationData data = new PreviousCompilationData(spec.getDestinationDir(), classpathSnapshot);
        stash.put(data);
    }

    private List<File> concat(Iterable<File> a, Iterable<File> b) {
        return Stream.concat(StreamSupport.stream(a.spliterator(), false),
                             StreamSupport.stream(b.spliterator(), false))
            .collect(Collectors.toList());
    }
}
