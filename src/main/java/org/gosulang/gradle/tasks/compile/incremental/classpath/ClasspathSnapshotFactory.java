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

package org.gosulang.gradle.tasks.compile.incremental.classpath;


import org.gradle.api.Action;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

public class ClasspathSnapshotFactory {

  private final ClasspathEntrySnapshotter classpathEntrySnapshotter;
  private final BuildOperationExecutor buildOperationExecutor;

  public ClasspathSnapshotFactory(ClasspathEntrySnapshotter classpathEntrySnapshotter, BuildOperationExecutor buildOperationExecutor) {
    this.classpathEntrySnapshotter = classpathEntrySnapshotter;
    this.buildOperationExecutor = buildOperationExecutor;
  }

  ClasspathSnapshot createSnapshot(final Iterable<File> entries) {
    final Set<ClasspathSnapshotFactory.CreateSnapshot> snapshotOperations = snapshotAll(entries);

    final LinkedHashMap<File, ClasspathEntrySnapshot> snapshots = new LinkedHashMap<>();
    final LinkedHashMap<File, HashCode> hashes = new LinkedHashMap<>();
    final Set<String> allClasses = new HashSet<>();
    final Set<String> duplicateClasses = new HashSet<>();

    for (ClasspathSnapshotFactory.CreateSnapshot operation : snapshotOperations) {
      File entry = operation.entry;
      ClasspathEntrySnapshot snapshot = operation.snapshot;
      if (snapshot != null) {
        snapshots.put(entry, snapshot);
        hashes.put(entry, snapshot.getHash());
        for (String c : snapshot.getClasses()) {
          if (!allClasses.add(c)) {
            duplicateClasses.add(c);
          }
        }
      }
    }

    ClasspathSnapshotData classpathSnapshotData = new ClasspathSnapshotData(hashes, duplicateClasses);
    return new ClasspathSnapshot(snapshots, classpathSnapshotData);
  }

  private Set<ClasspathSnapshotFactory.CreateSnapshot> snapshotAll(final Iterable<File> entries) {
    final Set<ClasspathSnapshotFactory.CreateSnapshot> snapshotOperations = new LinkedHashSet<>();

    buildOperationExecutor.runAll(new Action<BuildOperationQueue<CreateSnapshot>>() {
      @Override
      public void execute(BuildOperationQueue<ClasspathSnapshotFactory.CreateSnapshot> buildOperationQueue) {
        for (File entry : entries) {
          ClasspathSnapshotFactory.CreateSnapshot operation = new ClasspathSnapshotFactory.CreateSnapshot(entry);
          snapshotOperations.add(operation);
          buildOperationQueue.add(operation);
        }
      }
    });
    return snapshotOperations;
  }

  private class CreateSnapshot implements RunnableBuildOperation {
    private final File entry;
    private ClasspathEntrySnapshot snapshot;

    private CreateSnapshot(File entry) {
      this.entry = entry;
    }

    @Override
    public void run(BuildOperationContext context) {
      if (entry.exists()) {
        snapshot = classpathEntrySnapshotter.createSnapshot(entry);
      }
    }

    @Override
    public BuildOperationDescriptor.Builder description() {
      return BuildOperationDescriptor.displayName("Create incremental compile snapshot for " + entry);
    }
  }
}
