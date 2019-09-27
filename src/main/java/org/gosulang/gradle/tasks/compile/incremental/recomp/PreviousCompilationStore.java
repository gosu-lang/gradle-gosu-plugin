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

import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.Loader;
import org.gradle.cache.internal.Stash;

public class PreviousCompilationStore implements Loader<PreviousCompilationData>, Stash<PreviousCompilationData> {
  private final String taskPath;
  private final PersistentIndexedCache<String, PreviousCompilationData> cache;

  public PreviousCompilationStore(String taskPath, PersistentIndexedCache<String, PreviousCompilationData> cache) {
    this.taskPath = taskPath;
    this.cache = cache;
  }

  @Override
  public void put(PreviousCompilationData analysis) {
    this.cache.put(this.taskPath, analysis);
  }

  @Override
  public PreviousCompilationData get() {
    return (PreviousCompilationData)this.cache.get(this.taskPath);
  }
}
