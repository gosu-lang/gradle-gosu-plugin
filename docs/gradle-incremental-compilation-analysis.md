# Gradle's Incremental Compilation with Classpath Changes

**Date:** 2026-01-13
**Gradle Version Analyzed:** 8.6.0
**Status:** Reference documentation for future implementation

## Executive Summary

Gradle's JavaCompile task performs **selective recompilation** when JAR dependencies change their ABI (Application Binary Interface), not full recompilation. This analysis documents how Gradle achieves this using ASM bytecode analysis, reverse dependency graphs, and breadth-first search algorithms.

**Key Finding:** Gradle tracks dependencies at the **class level within JARs**, not just JAR-level dependencies. When a JAR changes, Gradle:
1. Extracts ABI information for each class in the JAR using ASM
2. Compares hash codes to identify which specific classes changed
3. Uses a reverse dependency graph to find affected source files
4. Recompiles only those files (plus transitive dependencies)

## Why We Can't Use Gradle's Implementation

All the relevant code is under `org.gradle.api.internal.*` packages, which means:
- **Not public API** - Subject to change without notice
- **No stability guarantees** - May break between Gradle versions
- **Not accessible** - Internal classes are not exposed to plugins

To implement this for GosuCompile, we would need to **reimplement** the core algorithms ourselves.

## Current State in gradle-gosu-plugin

Before diving into how Gradle achieves selective recompilation on JAR changes, let's understand what gradle-gosu-plugin currently does.

### Current Behavior

When a producer class in a JAR mutates its ABI, Gradle's `@CompileClasspath` detection will force the `compileGosu` task to re-run, which currently results in a **full recompilation** of all Gosu sources.

#### 1. What Gradle Detects

In [GosuCompile.java:348-353](../src/main/java/org/gosulang/gradle/tasks/compile/GosuCompile.java#L348-L353), the compile classpath uses ABI normalization:

```java
@CompileClasspath
@InputFiles
public FileCollection getClasspath() {
  return classpath;
}
```

When a JAR on the classpath changes:
- **ABI change** (new method, changed signature) → Gradle detects hash change → task re-runs
- **Implementation-only change** (method body) → ABI hash unchanged → task stays `UP-TO-DATE` ✅

#### 2. What gosuc Receives

When the task re-runs due to JAR change, gosuc receives:
- `-changed-types` and `-removed-types` based on **Gosu source file changes only**
- **No information** about which types in the JAR changed

From [GosuCompile.java:195-229](../src/main/java/org/gosulang/gradle/tasks/compile/GosuCompile.java#L195-L229), the changed/removed type detection is based on comparing source file timestamps and the dependency graph - it doesn't analyze JAR contents.

#### 3. Result: Full Recompilation

Without knowing which specific JAR types changed, gosuc cannot selectively recompile only affected Gosu sources. It recompiles everything.

### What the Dependency Graph Tracks

The interesting part is that **JAR classes ARE tracked** in the dependency graph, as verified by [JarLevelGranularityTest.groovy:99-100](../src/test/groovy/org/gosulang/gradle/functional/JarLevelGranularityTest.groovy#L99-L100):

```groovy
dependencyJson.contains('com.example.LibraryClass')  // JAR class
dependencyJson.contains('com.example.Consumer')       // Gosu class
```

So the dependency file knows: `Consumer.gs` uses `LibraryClass` from the JAR.

### Why Not Selective?

To enable selective recompilation on JAR changes, we'd need to:

1. **Detect which JAR types changed** - Compare old JAR vs new JAR (expensive)
2. **Extract ABI information** - Analyze .class files for signature changes
3. **Feed that to gosuc** - Add changed JAR types to `-changed-types`

This is complex and expensive, so standard Gradle behavior (including Java compilation) is to do full recompilation on classpath changes.

### Summary Table

| Change Type | Detection | Recompilation Strategy |
|------------|-----------|----------------------|
| Gosu source file | `@Incremental` on sources | Selective (using dependency graph) ✅ |
| Java source in same module | `@Incremental` on javaClassesDir | Selective (using dependency graph) ✅ |
| JAR ABI change | `@CompileClasspath` | Full (all Gosu sources) ⚠️ |
| JAR implementation only | `@CompileClasspath` | None (task UP-TO-DATE) ✅ |

**Note on Java source changes:** The "Java source in same module" case is particularly significant for typical Guidewire patterns where Java interfaces are implemented by Gosu classes in the same subproject. The implementation works as follows:

1. `javaClassesDir` is annotated with both `@CompileClasspath` and `@Incremental` ([GosuCompile.java:247-252](../src/main/java/org/gosulang/gradle/tasks/compile/GosuCompile.java#L247-L252))
2. When a Java interface changes:
   - `compileJava` task runs and updates `.class` files in `build/classes/java/main/`
   - `@CompileClasspath` provides ABI-level sensitivity (implementation-only changes don't trigger task re-run)
   - `@Incremental` allows querying which specific `.class` files changed
3. GosuCompile extracts FQCNs from changed `.class` files ([GosuCompile.java:89-107](../src/main/java/org/gosulang/gradle/tasks/compile/GosuCompile.java#L89-L107))
4. These FQCNs are passed to gosuc via `-changed-types`
5. gosuc uses the dependency graph to **selectively recompile** only affected Gosu sources

This is validated by [JavaInterfaceGosuImplementationTest.groovy](../src/test/groovy/org/gosulang/gradle/functional/JavaInterfaceGosuImplementationTest.groovy), which verifies that when a Java interface changes, only the Gosu implementation is recompiled, not unrelated Gosu classes.

The current implementation provides excellent incremental compilation for both Gosu→Gosu and Java→Gosu dependencies within a module. The dependency graph tracking of JAR types is forward-looking infrastructure that could enable selective recompilation for JAR changes in the future if/when we implement JAR diff analysis.

**However**, as this document will show, Gradle's JavaCompile task actually DOES perform selective recompilation when JAR ABIs change. The rest of this document explains how they achieve this.

## Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│ @CompileClasspath (JAR file)                        │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│ DefaultClassSetAnalyzer                             │
│ - Opens JAR, iterates .class files                  │
│ - For each class, calls ClassDependenciesAnalyzer   │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│ ClassDependenciesVisitor (ASM-based)                │
│ - Extends org.objectweb.asm.ClassVisitor            │
│ - Extracts ABI: methods, fields, superclass,        │
│   interfaces, annotations, constant pool refs       │
│ - Distinguishes accessible vs private dependencies  │
│ - Computes hashes for inlineable constants          │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│ ClassSetAnalysisData (per JAR/classpath entry)      │
│ - Map<className, hashCode> - ABI hash per class     │
│ - Map<className, Set<dependents>> - REVERSE graph!  │
│ - Map<className, Set<constantHashes>>               │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│ On Next Build: Compare & Recompile Selectively      │
│ 1. currentClasspath.findChangesSince(previous)      │
│ 2. Compare ABI hash codes                           │
│ 3. BFS through reverse dependency graph             │
│ 4. Find transitively affected source files          │
│ 5. Pass to compiler: only affected files            │
└─────────────────────────────────────────────────────┘
```

## Key Components

### 1. ClassDependenciesVisitor

**Location:** `platforms/jvm/language-java/src/main/java/org/gradle/api/internal/tasks/compile/incremental/asm/ClassDependenciesVisitor.java`

**Purpose:** ASM-based bytecode visitor that extracts ABI information from compiled .class files.

**Key Fields:**
```java
public class ClassDependenciesVisitor extends ClassVisitor {
    private final IntSet constants;             // Hashes of accessible constants
    private final Set<String> privateTypes;     // Types used in implementation
    private final Set<String> accessibleTypes;  // Types exposed in public API
    private final Predicate<String> typeFilter; // Filters irrelevant types
    private String dependencyToAllReason;       // If set, triggers full rebuild
    private String moduleName;                  // For module-info.java
}
```

**What It Tracks:**

1. **Class Header** (`visit()`)
   - Superclass
   - Implemented interfaces
   - Generic signature types
   - Access modifiers (public/private/protected)

2. **Fields** (`visitField()`)
   - Field type dependencies
   - Accessible constants: `(name + '|' + value).hashCode()`
   - Distinguishes public constants vs private fields

3. **Methods** (`visitMethod()`)
   - Return types
   - Parameter types
   - Generic signatures
   - Method-local types (as private dependencies)

4. **Annotations** (`visitAnnotation()`)
   - Annotation types
   - Retention policies (SOURCE retention triggers full rebuild)

5. **Constant Pool** (`collectRemainingClassDependencies()`)
   - Fast scan of all CONSTANT_Class entries in bytecode
   - Captures references even if not explicitly visited
   - Example: class references in instanceof, method bodies, etc.

**Critical Distinction:**
- **Accessible types** = Types exposed in public/protected members (API surface)
- **Private types** = Types used in implementation only

This distinction enables intelligent recompilation: only classes using the **public API** of a changed class need recompilation.

**Example Flow:**
```java
// Analyzing: public class MyClass extends BaseClass implements IFoo
visit(access, name, signature, "BaseClass", ["IFoo"]) {
    // If public class:
    accessibleTypes.add("BaseClass")
    accessibleTypes.add("IFoo")
}

// Analyzing: private void helper(HelperClass h)
visitMethod(PRIVATE, "helper", "(LHelperClass;)V", ...) {
    // Private method:
    privateTypes.add("HelperClass")  // Not exposed in API
}

// Analyzing: public static final int CONSTANT = 42
visitField(PUBLIC|STATIC|FINAL, "CONSTANT", "I", null, 42) {
    constants.add("CONSTANT|42".hashCode())  // Track for inlining
}
```

### 2. ClassSetAnalysisData

**Location:** `platforms/jvm/language-java/src/main/java/org/gradle/api/internal/tasks/compile/incremental/deps/ClassSetAnalysisData.java`

**Purpose:** Represents a snapshot of a JAR or classpath entry with dependency information.

**Data Structure:**
```java
public class ClassSetAnalysisData {
    // For EACH class in the JAR:
    private final Map<String, HashCode> classHashes;
    // className -> ABI hash (NOT raw bytecode hash!)

    private final Map<String, DependentsSet> dependents;
    // REVERSE mapping: className -> Set<classes that depend on it>

    private final Map<String, IntSet> classesToConstants;
    // className -> Set<constant hashes> for inlining detection

    private final String fullRebuildCause;
    // If non-null, forces full rebuild (e.g., module-info changed)
}
```

**Key Methods:**

```java
// Compares two snapshots to find changed classes
public DependentsSet getChangedClassesSince(ClassSetAnalysisData other) {
    // 1. Find added classes (only package-info matters)
    for (String added : Sets.difference(classHashes.keySet(), other.classHashes.keySet())) {
        if (added.endsWith("package-info")) {
            changed.add(added);
        }
    }

    // 2. Find removed or changed classes (hash comparison)
    for (Map.Entry<String, HashCode> entry :
         Sets.difference(other.classHashes.entrySet(), classHashes.entrySet())) {
        changed.add(entry.getKey());  // Hash differs or class removed
    }

    return DependentsSet.dependentClasses(ImmutableSet.of(), changed.build());
}

// Returns who directly depends on this class
public DependentsSet getDependents(String className) {
    if (className.equals("module-info")) {
        return DependentsSet.dependencyToAll("module-info has changed");
    }
    DependentsSet result = dependents.get(className);
    return result == null ? DependentsSet.empty() : result;
}
```

**Important:** The `dependents` map is a **REVERSE** dependency graph. Most dependency graphs track "what does X depend on", but this tracks "who depends on X". This is crucial for incremental compilation - when X changes, we need to know who uses X.

### 3. ClassSetAnalysis

**Location:** `platforms/jvm/language-java/src/main/java/org/gradle/api/internal/tasks/compile/incremental/deps/ClassSetAnalysis.java`

**Purpose:** Implements the transitive dependency calculation algorithm.

**Key Algorithm: findChangesSince()**

```java
public ClassSetDiff findChangesSince(ClassSetAnalysis other) {
    // Step 1: Find directly changed classes (by comparing ABI hashes)
    DependentsSet directChanges = classAnalysis.getChangedClassesSince(other.classAnalysis);
    if (directChanges.isDependencyToAll()) {
        return new ClassSetDiff(directChanges, Collections.emptyMap());
    }

    // Step 2: Find transitively affected classes using BFS on reverse graph
    DependentsSet transitiveChanges = other.findTransitiveDependents(
        directChanges.getAllDependentClasses(),
        Collections.emptyMap()
    );
    if (transitiveChanges.isDependencyToAll()) {
        return new ClassSetDiff(transitiveChanges, Collections.emptyMap());
    }

    // Step 3: Merge direct + transitive changes
    DependentsSet allChanges = DependentsSet.merge(
        Arrays.asList(directChanges, transitiveChanges)
    );

    // Step 4: Track which constants changed (for inline optimization)
    Map<String, IntSet> changedConstants = findChangedConstants(other, allChanges);

    return new ClassSetDiff(allChanges, changedConstants);
}
```

**Transitive Dependency Algorithm (BFS):**

```java
public DependentsSet findTransitiveDependents(
        Collection<String> classes,
        Map<String, IntSet> changedConstantsByClass) {

    Set<String> privateDependents = new HashSet<>();
    Set<String> accessibleDependents = new HashSet<>();
    Set<String> visited = new HashSet<>();
    Deque<String> remaining = new ArrayDeque<>(classes);  // BFS queue

    while (!remaining.isEmpty()) {
        String current = remaining.pop();
        if (!visited.add(current)) {
            continue;  // Already processed
        }

        accessibleDependents.add(current);

        // Find who depends on this class (using reverse graph)
        DependentsSet dependents = findDirectDependents(current);
        if (dependents.isDependencyToAll()) {
            return dependents;  // Trigger full rebuild
        }

        // Private dependents stop propagating here
        privateDependents.addAll(dependents.getPrivateDependentClasses());

        // Accessible dependents continue BFS traversal
        remaining.addAll(dependents.getAccessibleDependentClasses());
    }

    return DependentsSet.dependents(privateDependents, accessibleDependents, resources);
}
```

**Why BFS?** Because we need to follow the chain:
- `ClassA` changed (has new method)
- `ClassB` uses `ClassA` publicly → needs recompilation
- `ClassC` uses `ClassB` publicly → needs recompilation
- `ClassD` uses `ClassB` privately → needs recompilation but stops here
- `ClassE` uses `ClassD` → does NOT need recompilation (private boundary)

### 4. CurrentCompilation & PreviousCompilation

**Location:** `platforms/jvm/language-java/src/main/java/org/gradle/api/internal/tasks/compile/incremental/recomp/`

**Purpose:** Manages comparison between current and previous build states.

**CurrentCompilation.java:**
```java
public class CurrentCompilation {
    private final JavaCompileSpec spec;
    private final CurrentCompilationAccess classpathSnapshotter;

    public DependentsSet findDependentsOfClasspathChanges(PreviousCompilation previous) {
        // Step 1: Snapshot current classpath (analyze all JARs)
        ClassSetAnalysis currentClasspath = getClasspath();

        // Step 2: Load previous classpath snapshot from cache
        ClassSetAnalysis previousClasspath = previous.getClasspath();
        if (previousClasspath == null) {
            return DependentsSet.dependencyToAll("classpath data incomplete");
        }

        // Step 3: Find what changed (hash comparison + BFS)
        ClassSetAnalysis.ClassSetDiff classpathChanges =
            currentClasspath.findChangesSince(previousClasspath);

        // Step 4: Use previous compilation's source→class mapping to find files
        return previous.findDependentsOfClasspathChanges(classpathChanges);
    }

    private ClassSetAnalysis getClasspath() {
        // Analyzes ALL compile classpath JARs
        return new ClassSetAnalysis(
            classpathSnapshotter.getClasspathSnapshot(
                Iterables.concat(spec.getCompileClasspath(), spec.getModulePath())
            )
        );
    }
}
```

**PreviousCompilation.java:**
```java
public class PreviousCompilation {
    private final PreviousCompilationData data;
    private final ClassSetAnalysis classAnalysis;  // Analysis of previous outputs

    public DependentsSet findDependentsOfClasspathChanges(ClassSetAnalysis.ClassSetDiff diff) {
        if (diff.getDependents().isDependencyToAll()) {
            return diff.getDependents();
        }

        // Find transitive dependents in our source code
        return classAnalysis.findTransitiveDependents(
            diff.getDependents().getAllDependentClasses(),
            diff.getConstants()
        );
    }

    public DependentsSet findDependentsOfSourceChanges(Set<String> classNames) {
        return classAnalysis.findTransitiveDependents(
            classNames,
            classNames.stream().collect(Collectors.toMap(
                Function.identity(),
                classAnalysis::getConstants
            ))
        );
    }
}
```

## Complete Flow Example

Let's trace what happens when `commons-lang3.jar` on the classpath adds a new method to `StringUtils`:

### Initial State (Build N)

```
commons-lang3-3.12.jar:
  org.apache.commons.lang3.StringUtils:
    + public static boolean isEmpty(CharSequence cs)
    ABI Hash: abc123

MyProject sources:
  com.example.MyClass.java:
    uses StringUtils.isEmpty()
    depends on: ["org.apache.commons.lang3.StringUtils"]
```

**Gradle stores:**
```json
{
  "classpathSnapshot": {
    "org.apache.commons.lang3.StringUtils": {
      "hash": "abc123",
      "dependents": ["com.example.MyClass"]
    }
  },
  "outputSnapshot": {
    "com.example.MyClass": {
      "dependencies": ["org.apache.commons.lang3.StringUtils"]
    }
  }
}
```

### Updated JAR (Build N+1)

```
commons-lang3-3.13.jar:
  org.apache.commons.lang3.StringUtils:
    + public static boolean isEmpty(CharSequence cs)
    + public static boolean isBlank(CharSequence cs)  // NEW METHOD
    ABI Hash: def456  // Changed!
```

### Gradle's Detection Process

1. **Hash Comparison:**
   ```java
   current.getChangedClassesSince(previous)
   // Finds: StringUtils hash changed (abc123 → def456)
   ```

2. **Lookup Dependents:**
   ```java
   previous.getDependents("org.apache.commons.lang3.StringUtils")
   // Returns: ["com.example.MyClass"]
   ```

3. **Transitive Dependencies (BFS):**
   ```java
   findTransitiveDependents(["com.example.MyClass"], ...)
   // Walks the graph to find if any other classes depend on MyClass
   // Returns complete set of affected source files
   ```

4. **Selective Recompilation:**
   ```java
   // Only recompiles:
   // - com/example/MyClass.java
   // - (any other classes that transitively depend on MyClass)
   //
   // Does NOT recompile:
   // - Unrelated classes in the project
   ```

### Contrast with Implementation-Only Change

If the JAR only changed method bodies (not signatures):

```
commons-lang3-3.12.1.jar:
  org.apache.commons.lang3.StringUtils:
    + public static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;  // Optimized implementation
    }
    ABI Hash: abc123  // UNCHANGED - same signature
```

**Result:** `@CompileClasspath` normalization detects no ABI change → task stays `UP-TO-DATE` → **zero files recompiled**!

## Implementation Considerations for GosuCompile

If we wanted to implement this for gradle-gosu-plugin:

### 1. Required Components

```
┌─────────────────────────────────────────────────────┐
│ JarClasspathAnalyzer (new class)                    │
│ - Iterate .class files in JARs                      │
│ - Extract ABI using ASM ClassReader                 │
│ - Build Map<className, abiHash>                     │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│ ClassAbiExtractor (new class)                       │
│ - Uses ASM to visit class members                   │
│ - Computes ABI hash (not raw bytecode hash)         │
│ - Distinguishes public vs private dependencies      │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│ ClasspathSnapshotCache (new class)                  │
│ - Persists Map<className, abiHash> to disk          │
│ - Stored in build/tmp/gosu-classpath-cache/         │
│ - Reloaded on next build for comparison             │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│ GosuCompile.java (modifications)                    │
│ - On task execution:                                │
│   1. Load previous classpath snapshot               │
│   2. Analyze current classpath                      │
│   3. Compare and find changed classes               │
│   4. Use dependency graph to find affected .gs files│
│   5. Pass only affected files to gosuc              │
└─────────────────────────────────────────────────────┘
```

### 2. Dependencies

Would need to add ASM to gradle-gosu-plugin:

```groovy
dependencies {
    implementation 'org.ow2.asm:asm:9.5'  // Or latest
    // For signature parsing:
    implementation 'org.ow2.asm:asm-util:9.5'
}
```

### 3. ABI Hash Computation

Key challenge: What makes an ABI "changed"? Need to hash:
- Method signatures (name + descriptor)
- Field signatures (name + type)
- Superclass and interfaces
- Access modifiers (public/protected/private)
- Generic signatures
- Annotation retention policies

**NOT** method bodies, local variables, line numbers, etc.

Example ASM visitor skeleton:
```java
public class AbiHashVisitor extends ClassVisitor {
    private final MessageDigest digest = MessageDigest.getInstance("SHA-256");

    @Override
    public void visit(int version, int access, String name,
                      String signature, String superName, String[] interfaces) {
        if ((access & Opcodes.ACC_PUBLIC) != 0) {
            digest.update(name.getBytes());
            digest.update(superName.getBytes());
            for (String iface : interfaces) {
                digest.update(iface.getBytes());
            }
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc,
                                     String signature, String[] exceptions) {
        if ((access & Opcodes.ACC_PUBLIC) != 0 || (access & Opcodes.ACC_PROTECTED) != 0) {
            digest.update(name.getBytes());
            digest.update(desc.getBytes());
        }
        return null;  // Don't visit method bodies
    }

    public HashCode getAbiHash() {
        return HashCode.fromBytes(digest.digest());
    }
}
```

### 4. Performance Considerations

**Concerns:**
- Analyzing large JARs (e.g., spring-framework.jar) could be slow
- Need to analyze on EVERY build (but can cache results by JAR hash)
- Memory overhead for storing class→hash mappings

**Optimizations:**
- Cache analysis results per JAR file hash (e.g., `build/tmp/gosu-abi-cache/`)
- Only re-analyze JARs that changed (by file hash)
- Parallel JAR analysis using worker threads
- Lazy loading of dependency graph (don't load unless needed)

**Gradle's approach:**
- Uses Gradle's build cache for snapshot storage
- Leverages Gradle's file hashing infrastructure
- Parallelizes JAR analysis across worker threads

### 5. Integration with v2 Architecture

Our current v2 FQCN-based dependency tracking already has:
- ✅ Reverse dependency graph: `usedBy` map
- ✅ FQCN-based tracking (not file paths)
- ✅ Dependency file persistence (JSON format)

What we'd need to add:
- ❌ Classpath snapshot tracking (currently only tracks source→source)
- ❌ ABI hash computation for JAR classes
- ❌ Comparison logic to find changed classes
- ❌ Integration point in GosuCompile.java

**Rough implementation plan:**

1. **Phase 1:** Build JAR analysis infrastructure
   - Create `JarClasspathAnalyzer` with ASM
   - Implement ABI hash computation
   - Test with real JARs (commons-lang3, guava, etc.)

2. **Phase 2:** Add snapshot caching
   - Persist classpath snapshots to disk
   - Load previous snapshots on next build
   - Implement hash comparison logic

3. **Phase 3:** Integrate with dependency graph
   - Extend v2 JSON format to include JAR classes
   - Modify gosuc to accept JAR types in `-changed-types`
   - Update `GosuCompile.java` to pass changed JAR classes

4. **Phase 4:** Testing & refinement
   - Measure performance impact
   - Test with real-world projects
   - Handle edge cases (module-info, package-info, constants)

## Limitations & Edge Cases

### 1. Constant Inlining

When a public constant changes:
```java
public class Constants {
    public static final int MAX_SIZE = 100;  // Changed from 50 to 100
}
```

**Problem:** Java compiler may inline this constant into calling code. Changing it requires recompiling all usages, even if only the value changed.

**Gradle's solution:**
- Tracks accessible constant **values** as hashes
- If constant value changes, forces full recompilation (unless using Java 11+ with constant dependency tracking)

### 2. Module-info.java

Changes to `module-info.java` trigger **full recompilation** because:
- Module declarations affect all classes in the module
- Can change visibility and accessibility globally
- Too complex to track precisely

### 3. Source Retention Annotations

Annotations with `@Retention(SOURCE)`:
```java
@Retention(RetentionPolicy.SOURCE)
public @interface MyAnnotation { }
```

**Problem:** Annotation processors may generate code based on these. Changes require full rebuild.

**Gradle's solution:** Detects `RetentionPolicy.SOURCE` and triggers `dependencyToAll`.

### 4. Annotation Processors

If annotation processors are present, incremental compilation is disabled for resource changes:

```java
public boolean isIncrementalOnResourceChanges() {
    return currentCompilation.getAnnotationProcessorPath().isEmpty();
}
```

**Why?** Annotation processors can generate arbitrary code from any input, making incremental compilation unsafe.

## Performance Characteristics

Based on Gradle's blog posts and the code:

**Initial Build (Cold Cache):**
- Analyze all JARs on classpath: ~50-200ms for typical project
- ASM parsing is fast (~1ms per class file)
- Bottleneck: I/O reading JAR files

**Incremental Build (Warm Cache):**
- Load previous snapshot: ~10-50ms
- Compare hashes: ~1-10ms
- BFS through dependency graph: ~1-5ms
- **Total overhead: ~20-100ms** (negligible vs compilation time)

**Space Overhead:**
- ~50-100 bytes per class analyzed
- Typical project (500 classes on classpath): ~50KB
- Large project (5000 classes): ~500KB
- Very manageable

## References

- **Gradle Blog:** [An In-depth Look at Gradle's Approach to Faster Compilation](https://blog.gradle.org/our-approach-to-faster-compilation)
- **Gradle Blog:** [Incremental Compilation, the Java Library Plugin, and other performance features](https://blog.gradle.org/incremental-compiler-avoidance)
- **GitHub Issue:** [Class-level compilation avoidance #26337](https://github.com/gradle/gradle/issues/26337)
- **Source Code:** [Gradle 8.6.0 on GitHub](https://github.com/gradle/gradle/tree/v8.6.0)

## Conclusion

Gradle's selective recompilation on classpath changes is a **sophisticated but achievable** system:

**Pros of implementing for Gosu:**
- Significant build performance improvement for large projects
- Better developer experience (faster feedback loops)
- Matches standard Gradle JavaCompile behavior

**Cons:**
- Substantial implementation effort (~1-2 weeks of work)
- Ongoing maintenance burden
- Can't reuse Gradle's internal infrastructure
- Risk of bugs (e.g., missing a recompilation when needed)

**Recommendation:**
- Document this analysis (done! ✅)
- Revisit if user demand is high
- Consider contributing to Gradle to expose public APIs for this

For now, our v2 FQCN-based approach handles **source→source** incremental compilation well. JAR-level selective recompilation would be a nice enhancement but is not critical for initial v2 release.

---

*This document was created by analyzing Gradle 8.6.0 source code on 2026-01-13. All code under `org.gradle.api.internal.*` is subject to change without notice and should not be directly depended upon by third-party plugins.*
