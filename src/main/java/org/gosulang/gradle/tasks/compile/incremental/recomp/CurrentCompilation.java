/*
 * Copyright 2018 the original author or authors.
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
import org.gosulang.gradle.tasks.compile.incremental.classpath.ClasspathSnapshot;
import org.gosulang.gradle.tasks.compile.incremental.classpath.ClasspathSnapshotProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class CurrentCompilation {
    private final GosuCompileSpec spec;
    private final ClasspathSnapshotProvider classpathSnapshotProvider;

    public CurrentCompilation(GosuCompileSpec spec, ClasspathSnapshotProvider classpathSnapshotProvider) {
        this.spec = spec;
        this.classpathSnapshotProvider = classpathSnapshotProvider;
    }

    public ClasspathSnapshot getClasspathSnapshot() {
        return classpathSnapshotProvider.getClasspathSnapshot(concat(spec.getClasspath(), spec.getModulePath()));
    }

    private Iterable<File> concat(Iterable<File> classpath, List<File> modulePath) {
        ArrayList<File> result = new ArrayList<>();
        classpath.forEach(result::add);
        result.addAll(modulePath);
        return result;
    }
}
