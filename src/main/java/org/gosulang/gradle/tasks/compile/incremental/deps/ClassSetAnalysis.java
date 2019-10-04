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

import org.gradle.api.internal.tasks.compile.incremental.processing.GeneratedResource;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class ClassSetAnalysis {

  private final ClassSetAnalysisData classAnalysis;

  public ClassSetAnalysis(ClassSetAnalysisData classAnalysis) {
    this.classAnalysis = classAnalysis;
  }

  public DependentsSet getRelevantDependents(Iterable<String> classes, Set<Integer> constants) {
    Set<String> resultClasses = null;
    Set<GeneratedResource> resultResources = null;
    for (String cls : classes) {
      DependentsSet d = getRelevantDependents(cls, constants);
      if (d.isDependencyToAll()) {
        return d;
      }
      Set<String> dependentClasses = d.getDependentClasses();
      Set<GeneratedResource> dependentResources = d.getDependentResources();
      if (dependentClasses.isEmpty() && dependentResources.isEmpty()) {
        continue;
      }
      if (resultClasses == null) {
        resultClasses = new LinkedHashSet<>();
      }
      resultClasses.addAll(dependentClasses);
      if (resultResources == null) {
        resultResources = new LinkedHashSet<>();
      }
      resultResources.addAll(dependentResources);
    }
    return resultClasses == null ? DependentsSet.empty() : DependentsSet.dependents(resultClasses, resultResources);
  }

  public DependentsSet getRelevantDependents(String className, Set<Integer> constants) {
    DependentsSet deps = getDependents(className);
    if (deps.isDependencyToAll()) {
      return deps;
    }
    if (!constants.isEmpty()) {
      return DependentsSet.dependencyToAll();
    }

    Set<String> resultClasses = new HashSet<String>();
    Set<GeneratedResource> resultResources = new HashSet<>();
    recurseDependentClasses(new HashSet<String>(), resultClasses, resultResources, deps.getDependentClasses());
    resultClasses.remove(className);

    return DependentsSet.dependents(resultClasses, resultResources);
  }

  /**
   * Recursively accumulate dependent classes and resources.  Dependent classes discovered can themselves be used to query
   * further dependents, while resources are just data accumulated along the way.
   */
  private void recurseDependentClasses(Set<String> visitedClasses, Set<String> resultClasses, Set<GeneratedResource> resultResources, Iterable<String> dependentClasses) {
    for (String d : dependentClasses) {
      if (!visitedClasses.add(d)) {
        continue;
      }
      if (!isNestedClass(d)) {
        resultClasses.add(d);
      }
      DependentsSet currentDependents = getDependents(d);
      if (!currentDependents.isDependencyToAll()) {
        resultResources.addAll(currentDependents.getDependentResources());
        recurseDependentClasses(visitedClasses, resultClasses, resultResources, currentDependents.getDependentClasses());
      }
    }
  }

  private DependentsSet getDependents(String className) {
    DependentsSet dependents = classAnalysis.getDependents(className);
    if (dependents.isDependencyToAll()) {
      return dependents;
    }
    return DependentsSet.dependents(dependents.getDependentClasses(), dependents.getDependentResources());
  }

  private boolean isNestedClass(String d) {
    return d.contains("$");
  }

  public Set<Integer> getConstants(String className) {
    return classAnalysis.getConstants(className);
  }

  public Set<String> getTypesToReprocess() {
    return Collections.emptySet(); // with java this would come from annotation processing, but Gosu doesn't do that
  }
}

