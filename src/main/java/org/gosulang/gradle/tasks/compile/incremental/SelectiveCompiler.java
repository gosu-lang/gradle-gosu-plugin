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
import org.gosulang.gradle.tasks.compile.incremental.classpath.ClasspathSnapshotProvider;
import org.gosulang.gradle.tasks.compile.incremental.recomp.CurrentCompilation;
import org.gosulang.gradle.tasks.compile.incremental.recomp.PreviousCompilation;
import org.gosulang.gradle.tasks.compile.incremental.recomp.RecompilationSpecProvider;
import org.gradle.api.internal.tasks.compile.incremental.RecompilationNotNecessary;
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpec;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.language.base.internal.compile.Compiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

class SelectiveCompiler<T extends GosuCompileSpec> implements org.gradle.language.base.internal.compile.Compiler<T> {
    private static final Logger LOG = LoggerFactory.getLogger(SelectiveCompiler.class);
    private final PreviousCompilation previousCompilation;
    private final CleaningGosuCompilerSupport<T> cleaningCompiler;
    private final Compiler<T> rebuildAllCompiler;
    private final RecompilationSpecProvider recompilationSpecProvider;
    private final ClasspathSnapshotProvider classpathSnapshotProvider;

    public SelectiveCompiler(PreviousCompilation previousCompilation,
                             CleaningGosuCompilerSupport<T> cleaningCompiler,
                             Compiler<T> rebuildAllCompiler,
                             RecompilationSpecProvider recompilationSpecProvider,
                             ClasspathSnapshotProvider classpathSnapshotProvider) {
        this.previousCompilation = previousCompilation;
        this.cleaningCompiler = cleaningCompiler;
        this.rebuildAllCompiler = rebuildAllCompiler;
        this.recompilationSpecProvider = recompilationSpecProvider;
        this.classpathSnapshotProvider = classpathSnapshotProvider;
    }

    @Override
    public WorkResult execute(T spec) {
        if (spec.getSourceRoots().isEmpty()) {
            LOG.info("Full recompilation is required because the source roots could not be inferred.");
            return rebuildAllCompiler.execute(spec);
        }

        Timer clock = Time.startTimer();
        CurrentCompilation currentCompilation = new CurrentCompilation(spec, classpathSnapshotProvider);

        RecompilationSpec recompilationSpec = recompilationSpecProvider.provideRecompilationSpec(currentCompilation, previousCompilation);

        if (recompilationSpec.isFullRebuildNeeded()) {
            LOG.info("Full recompilation is required because {}. Analysis took {}.", recompilationSpec.getFullRebuildCause(), clock.getElapsed());
            return rebuildAllCompiler.execute(spec);
        }

        recompilationSpecProvider.initializeCompilation(spec, recompilationSpec);

        if (spec.getSource().isEmpty() && spec.getClasses().isEmpty()) {
            LOG.info("None of the classes needs to be compiled! Analysis took {}. ", clock.getElapsed());
            return new RecompilationNotNecessary();
        }

        try {
            return recompilationSpecProvider.decorateResult(recompilationSpec, cleaningCompiler.getCompiler().execute(spec));
        } finally {
            Collection<String> classesToCompile = recompilationSpec.getClassesToCompile();
            LOG.info("Incremental compilation of {} classes completed in {}.", classesToCompile.size(), clock.getElapsed());
            LOG.debug("Recompiled classes {}", classesToCompile);
        }
    }

}
