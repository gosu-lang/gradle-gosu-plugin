/*
 * Copyright 2017 the original author or authors.
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

package org.gosulang.gradle.tasks.compile.incremental.deps;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * An immutable set of details extracted from a class file.
 */
public class ClassAnalysis {
  private final String className;
  private final Set<String> classDependencies;
  private final boolean dependencyToAll;
  private final Set<Integer> constants;

  public ClassAnalysis(String className, Set<String> classDependencies, boolean dependencyToAll, Set<Integer> constants) {
    this.className = className;
    this.classDependencies = Collections.unmodifiableSet(new HashSet<>(classDependencies));
    this.dependencyToAll = dependencyToAll;
    this.constants = constants.isEmpty() ? Collections.emptySet() : constants;
  }

  public String getClassName() {
    return className;
  }

  public Set<String> getClassDependencies() {
    return classDependencies;
  }

  public Set<Integer> getConstants() {
    return constants;
  }

  public boolean isDependencyToAll() {
    return dependencyToAll;
  }
}
