/*
 * Copyright 2019 the original author or authors.
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

package org.gosulang.gradle.tasks.compile.incremental.recomp;

import org.gosulang.gradle.tasks.compile.GosuCompileSpec;
import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.FileCollectionAdapter;
import org.gradle.api.internal.file.collections.ListBackedFileSet;
import org.gradle.api.internal.tasks.compile.incremental.processing.GeneratedResource;
import org.gradle.api.internal.tasks.compile.incremental.recomp.CompilationSourceDirs;
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpec;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.FileUtils;
import org.gradle.internal.execution.history.changes.DefaultFileChange;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class GosuRecompilationSpecProvider extends AbstractRecompilationSpecProvider {
    private final IncrementalTaskInputs inputs;
    private final FileCollection filesToCompile;
    private final GosuConventionalSourceFileClassNameConverter sourceFileClassNameConverter;

    public GosuRecompilationSpecProvider(FileOperations fileOperations, FileTreeInternal sources, FileCollection filesToCompile, IncrementalTaskInputs inputs, CompilationSourceDirs sourceDirs) {
        super(fileOperations, sources);
        this.filesToCompile = filesToCompile;
        this.sourceFileClassNameConverter = new GosuConventionalSourceFileClassNameConverter(sourceDirs);
        this.inputs = inputs;
    }

    @Override
    public boolean isIncremental() {
        return inputs.isIncremental();
    }

    @Override
    public RecompilationSpec provideRecompilationSpec(CurrentCompilation current, PreviousCompilation previous) {
        RecompilationSpec spec = new RecompilationSpec();
        processClasspathChanges(current, previous, spec);
        processOtherChanges(previous, spec);
        spec.getClassesToProcess().addAll(previous.getTypesToReprocess());
        return spec;
    }

    @Override
    public void initializeCompilation(GosuCompileSpec spec, RecompilationSpec recompilationSpec) {
        if (!recompilationSpec.isBuildNeeded()) {
            spec.setSource(new FileCollectionAdapter(new ListBackedFileSet(Collections.emptyList())));
            spec.setClasses(Collections.emptySet());
            return;
        }
        Factory<PatternSet> patternSetFactory = fileOperations.getFileResolver().getPatternSetFactory();
        PatternSet classesToDelete = patternSetFactory.create();
        PatternSet sourceToCompile = patternSetFactory.create();

        prepareGosuPatterns(recompilationSpec.getClassesToCompile(), classesToDelete, sourceToCompile);
        spec.setSource(narrowDownSourcesToCompile(sourceTree, sourceToCompile));
        includePreviousCompilationOutputOnClasspath(spec);
        addClassesToProcess(spec, recompilationSpec);
        deleteStaleFilesIn(classesToDelete, spec.getDestinationDir());

        Map<GeneratedResource.Location, PatternSet> resourcesToDelete = prepareResourcePatterns(recompilationSpec.getResourcesToGenerate(), patternSetFactory);
        deleteStaleFilesIn(resourcesToDelete.get(GeneratedResource.Location.CLASS_OUTPUT), spec.getDestinationDir());
    }

    @Override
    public WorkResult decorateResult(RecompilationSpec recompilationSpec, WorkResult workResult) {
        return workResult;
    }

    private FileTree narrowDownSourcesToCompile(FileTree sourceTree, PatternSet sourceToCompile) {
        return sourceTree.matching(sourceToCompile);
    }

    private static Map<GeneratedResource.Location, PatternSet> prepareResourcePatterns(Collection<GeneratedResource> staleResources, Factory<PatternSet> patternSetFactory) {
        Map<GeneratedResource.Location, PatternSet> resourcesByLocation = new EnumMap<>(GeneratedResource.Location.class);
        for (GeneratedResource.Location location : GeneratedResource.Location.values()) {
            resourcesByLocation.put(location, patternSetFactory.create());
        }
        for (GeneratedResource resource : staleResources) {
            resourcesByLocation.get(resource.getLocation()).include(resource.getPath());
        }
        return resourcesByLocation;
    }

    private void processOtherChanges(PreviousCompilation previous, final RecompilationSpec spec) {
        final SourceFileChangeProcessor sourceChangeProcessor = new SourceFileChangeProcessor(previous);
        final Action<InputFileDetails> action = input -> {
            if (spec.getFullRebuildCause() == null) {
                File file = input.getFile();
                if (FileUtils.hasExtension(file, ".gs") || FileUtils.hasExtension(file, ".gsx")) {
                    Collection<String> classNames = sourceFileClassNameConverter.getClassNames(file);
                    if (classNames.isEmpty()) {
                        spec.setFullRebuildCause("source dirs are changed", file);
                    } else {
                        sourceChangeProcessor.processChange(file, classNames, spec);
                    }
                }
            }
        };

        if (inputs.isIncremental()) {
            // can't do inputs.outOfDate because it's already been called once
            filesToCompile.getFiles().forEach(f -> action.execute(new ModifiedAdapter(f)));
        }
        inputs.removed(action);
    }

    static class ModifiedAdapter implements InputFileDetails {
        private File file;

        ModifiedAdapter(File file) {
            this.file = file;
        }

        @Override
        public boolean isAdded() {
            return false;
        }

        @Override
        public boolean isModified() {
            return true;
        }

        @Override
        public boolean isRemoved() {
            return false;
        }

        @Override
        public File getFile() {
            return file;
        }
    }


    private void prepareGosuPatterns(Collection<String> staleClasses, PatternSet filesToDelete, PatternSet sourceToCompile) {
        for (String staleClass : staleClasses) {
            String path = staleClass.replaceAll("\\.", "/");
            filesToDelete.include(path.concat(".class"));
            filesToDelete.include(path.concat(".gs"));
            filesToDelete.include(path.concat(".gsx"));
            filesToDelete.include(path.concat("$*.class"));
            // not sure these two can exist?
//            filesToDelete.include(path.concat("$*.gs"));
//            filesToDelete.include(path.concat("$*.gsx"));

            sourceToCompile.include(path.concat(".gs"));
            sourceToCompile.include(path.concat(".gsx"));
            // not sure these two can exist?
//            sourceToCompile.include(path.concat("$*.gs"));
//            sourceToCompile.include(path.concat("$*.gsx"));
        }
    }
}
