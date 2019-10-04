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

package org.gosulang.gradle.tasks.compile.incremental.deps;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ClassDependentsAccumulator {

  private final Set<String> dependenciesToAll = new HashSet<>();
  private final Map<String, Set<String>> dependents = new HashMap<String, Set<String>>();
  private final Map<String, Set<Integer>> classesToConstants = new HashMap<>();
  private final Set<String> seenClasses = new HashSet<>();
  private String fullRebuildCause;

  public void addClass(ClassAnalysis classAnalysis) {
    addClass(classAnalysis.getClassName(), classAnalysis.isDependencyToAll(), classAnalysis.getClassDependencies(), classAnalysis.getConstants());
  }

  public void addClass(String className, boolean dependencyToAll, Iterable<String> classDependencies, Set<Integer> constants) {
    if (seenClasses.contains(className)) {
      // same classes may be found in different classpath trees/jars
      // and we keep only the first one
      return;
    }
    seenClasses.add(className);
    if (!constants.isEmpty()) {
      classesToConstants.put(className, constants);
    }
    if (dependencyToAll) {
      dependenciesToAll.add(className);
      dependents.remove(className);
    }
    for (String dependency : classDependencies) {
      if (!dependency.equals(className) && !dependenciesToAll.contains(dependency)) {
        addDependency(dependency, className);
      }
    }
  }

  private Set<String> rememberClass(String className) {
    Set<String> d = dependents.get(className);
    if (d == null) {
      d = new HashSet<>();
      dependents.put(className, d);
    }
    return d;
  }

  //@VisibleForTesting
  Map<String, DependentsSet> getDependentsMap() {
    if (dependenciesToAll.isEmpty() && dependents.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, DependentsSet> builder = new HashMap<>();
    for (String s : dependenciesToAll) {
      builder.put(s, DependentsSet.dependencyToAll());
    }
    for (Map.Entry<String, Set<String>> entry : dependents.entrySet()) {
      builder.put(entry.getKey(), DependentsSet.dependentClasses(entry.getValue()));
    }
    return builder;
  }

  //@VisibleForTesting
  Map<String, Set<Integer>> getClassesToConstants() {
    return Collections.unmodifiableMap(classesToConstants);
  }

  private void addDependency(String dependency, String dependent) {
    Set<String> dependents = rememberClass(dependency);
    dependents.add(dependent);
  }

  public void fullRebuildNeeded(String fullRebuildCause) {
    this.fullRebuildCause = fullRebuildCause;
  }

  public ClassSetAnalysisData getAnalysis() {
    return new ClassSetAnalysisData(Collections.unmodifiableSet(new HashSet<>(seenClasses)), getDependentsMap(), getClassesToConstants(), fullRebuildCause);
  }

  private static <K, V> Map<K, Set<V>> asMap(Map<K, Set<V>> multimap) {
    Map<K, Set<V>> builder = new HashMap<>();
    for (K key : multimap.keySet()) {
      builder.put(key, Collections.unmodifiableSet(new HashSet<>(multimap.get(key))));
    }
    return builder;
  }
}
