# Manifold Integration - Phase 2: Incremental Compilation

**Goal:** Add incremental compilation support to the Manifold-based single-pass compilation, achieving fine-grained recompilation based on actual dependencies.

**Status:** Planning
**Date:** January 2026
**Prerequisites:** Phase 1 complete and validated

---

## Overview

Phase 2 adds incremental compilation by implementing a Gradle adapter for Manifold's `getChangedResourceFiles()` mechanism. This enables Manifold to know which .gs files changed and selectively recompile only what's necessary.

**Success Criteria:**
- ✅ Gosu files only recompile when they actually change
- ✅ Java ABI changes only trigger recompilation of dependent Gosu files
- ✅ Build times improve significantly on incremental builds
- ✅ Correctness maintained (no missed recompilations)
- ✅ Integration with Gradle's incremental task infrastructure

---

## Problem Analysis

### Current Behavior (After Phase 1)

**Without incremental support:**
- Every build recompiles ALL Gosu files
- Manifold's `getChangedResourceFiles()` returns empty list (line 530 in ManifoldJavaFileManager.java)
- Manifold's `isFilteredFromIncrementalCompilation()` always returns false
- Result: No filtering, everything recompiles

**Root cause:**
```java
public static List<File> getChangedResourceFiles()
{
    List<File> changedFiles = Collections.emptyList();
    Class<?> type = ReflectUtil.type( "manifold.ij.jps.IjChangedResourceFiles" );
    if( type != null )
    {
        changedFiles = (List<File>)ReflectUtil.method( type, "getChangedFiles" ).invokeStatic();
    }
    return changedFiles;  // Empty for Gradle!
}
```

### Manifold's Incremental Compilation Flow

**How it works in IntelliJ (from ManifoldJavaFileManager.java:480-524):**

1. **javac requests a type** → calls `findGeneratedFile(fqn)`
2. **Check if filtered** → `isFilteredFromIncrementalCompilation(fqn)`
3. **Get changed files** → `getChangedResourceFiles()`
4. **Check if type's file changed:**
   - If changed → return stub (will be recompiled)
   - If not changed → return null (javac uses existing .class file)
5. **javac continues** → either compiles or skips based on response

**Key insight:** No dependency file needed! The existing .class files in the build directory ARE the state.

---

## Implementation Plan

### Step 1: Create GradleChangedResourceFiles Adapter

**New file:** `src/main/java/org/gosulang/gradle/manifold/GradleChangedResourceFiles.java`

```java
package org.gosulang.gradle.manifold;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adapter to provide changed resource files from Gradle's InputChanges API to Manifold.
 *
 * This replaces IntelliJ's IjChangedResourceFiles for Gradle builds.
 */
public class GradleChangedResourceFiles
{
    private static final ThreadLocal<List<File>> CHANGED_FILES = new ThreadLocal<>();

    /**
     * Set the list of changed files for the current compilation.
     * Called by JavaCompile task before compilation starts.
     */
    public static void setChangedFiles(List<File> changedFiles)
    {
        CHANGED_FILES.set(changedFiles != null ? changedFiles : Collections.emptyList());
    }

    /**
     * Get the list of changed files for the current compilation.
     * Called by Manifold's ManifoldJavaFileManager during compilation.
     */
    public static List<File> getChangedFiles()
    {
        List<File> files = CHANGED_FILES.get();
        return files != null ? files : Collections.emptyList();
    }

    /**
     * Clear the changed files after compilation completes.
     */
    public static void clear()
    {
        CHANGED_FILES.remove();
    }

    /**
     * Check if incremental compilation is active.
     */
    public static boolean isIncremental()
    {
        List<File> files = CHANGED_FILES.get();
        return files != null && !files.isEmpty();
    }
}
```

**Why ThreadLocal:**
- Gradle may run multiple compilations in parallel
- Each compilation needs its own changed files list
- ThreadLocal provides isolation

### Step 2: Patch Manifold to Use Gradle Adapter

**Challenge:** Manifold's `getChangedResourceFiles()` uses reflection to find IntelliJ's class. We need it to find our Gradle adapter instead.

**Option A: Fork Manifold (Not Recommended)**
- Modify ManifoldJavaFileManager.java directly
- Maintenance burden
- Hard to upgrade Manifold versions

**Option B: Runtime Bytecode Patching (Recommended)**
- Use ASM to patch ManifoldJavaFileManager at runtime
- Intercept getChangedResourceFiles() method
- Redirect to GradleChangedResourceFiles

**Option C: Gradle Build Listener (Simplest)**
- Don't patch Manifold
- Set system property that Manifold checks
- Add Gradle adapter to annotation processor classpath

**Recommended: Option C with system property**

**Modify Manifold's logic to check for Gradle:**

Actually, simpler approach - Manifold's reflection already looks for a class. We just need to:
1. Put `GradleChangedResourceFiles` in a package Manifold expects OR
2. Configure our class to be discoverable

**Even simpler: Use reflection like IntelliJ does**

Manifold already uses reflection (line 530):
```java
Class<?> type = ReflectUtil.type( "manifold.ij.jps.IjChangedResourceFiles" );
```

We can register our adapter similarly!

**New implementation:**
```java
public static List<File> getChangedResourceFiles()
{
    List<File> changedFiles = Collections.emptyList();

    // Try IntelliJ (for IDE builds)
    Class<?> ijType = ReflectUtil.type( "manifold.ij.jps.IjChangedResourceFiles" );
    if( ijType != null )
    {
        changedFiles = (List<File>)ReflectUtil.method( ijType, "getChangedFiles" ).invokeStatic();
        return changedFiles;
    }

    // Try Gradle (for Gradle builds)
    Class<?> gradleType = ReflectUtil.type( "org.gosulang.gradle.manifold.GradleChangedResourceFiles" );
    if( gradleType != null )
    {
        changedFiles = (List<File>)ReflectUtil.method( gradleType, "getChangedFiles" ).invokeStatic();
    }

    return changedFiles;
}
```

**We need to patch this method at runtime OR contribute this change upstream to Manifold.**

### Step 3: Create JavaCompile Subclass with Incremental Support

**New file:** `src/main/java/org/gosulang/gradle/tasks/compile/ManifoldAwareJavaCompile.java`

```java
package org.gosulang.gradle.tasks.compile;

import org.gosulang.gradle.manifold.GradleChangedResourceFiles;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.work.ChangeType;
import org.gradle.work.FileChange;
import org.gradle.work.InputChanges;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Extension of JavaCompile that provides changed file information to Manifold
 * for incremental Gosu compilation.
 */
public class ManifoldAwareJavaCompile extends JavaCompile
{
    @Override
    protected void compile(InputChanges inputChanges)
    {
        try {
            if (inputChanges.isIncremental()) {
                // Collect changed Gosu source files
                List<File> changedGosuFiles = new ArrayList<>();

                // Check all source files for changes
                for (FileChange change : inputChanges.getFileChanges(getSource())) {
                    File file = change.getFile();

                    // Only track .gs files (Gosu sources)
                    if (file.getName().endsWith(".gs") || file.getName().endsWith(".gsx")) {
                        if (change.getChangeType() != ChangeType.REMOVED) {
                            changedGosuFiles.add(file);
                            getLogger().info("Gosu file changed: {}", file);
                        }
                    }
                }

                // Provide changed files to Manifold
                GradleChangedResourceFiles.setChangedFiles(changedGosuFiles);
                getLogger().info("Incremental Gosu compilation: {} changed files", changedGosuFiles.size());
            } else {
                // Full rebuild
                GradleChangedResourceFiles.clear();
                getLogger().info("Full Gosu compilation (not incremental)");
            }

            // Perform compilation
            super.compile(inputChanges);

        } finally {
            // Clean up thread-local state
            GradleChangedResourceFiles.clear();
        }
    }
}
```

**Key points:**
- Extends Gradle's JavaCompile
- Intercepts compile() method
- Extracts changed .gs files from InputChanges
- Sets them in GradleChangedResourceFiles before compilation
- Cleans up after compilation

### Step 4: Update GosuBasePlugin to Use ManifoldAwareJavaCompile

**File:** [GosuBasePlugin.java](src/main/java/org/gosulang/gradle/GosuBasePlugin.java)

**Change the task type:**

```java
private void configureSourceSets() {
    javaPluginExtension(_project).getSourceSets().all(sourceSet -> {
        // ... existing source set configuration ...

        // Replace the standard JavaCompile task with our Manifold-aware version
        String compileJavaTaskName = sourceSet.getCompileJavaTaskName();

        _project.getTasks().named(compileJavaTaskName, JavaCompile.class, task -> {
            // This won't work - can't change task type after creation
        });

        // Instead, need to replace the task
        _project.getTasks().replace(compileJavaTaskName, ManifoldAwareJavaCompile.class);

        // Then configure it
        TaskProvider<ManifoldAwareJavaCompile> compileJava = _project.getTasks().named(
            compileJavaTaskName,
            ManifoldAwareJavaCompile.class
        );

        compileJava.configure(task -> {
            // Add Gosu sources
            task.source(gosuSourceSet.getGosu());

            // ... rest of configuration from Phase 1 ...
        });
    });
}
```

**Actually, better approach - use decoration:**

```java
private void makeJavaCompileIncrementalForGosu(TaskProvider<JavaCompile> compileJava, GosuSourceSet gosuSourceSet) {
    compileJava.configure(task -> {
        // Wrap the compile action with incremental support
        task.doFirst(t -> {
            // This runs before compilation
            // But we can't access InputChanges here...
        });
    });
}
```

**Problem:** Can't easily intercept InputChanges from doFirst/doLast

**Better solution:** Create a custom task action that wraps JavaCompile's action

Actually, Gradle's JavaCompile doesn't expose easy hooks. Let's use a different approach:

**Use Gradle's WorkResult and compile listener:**

```java
private void configureIncrementalGosuCompilation(TaskProvider<JavaCompile> compileJava, GosuSourceSet gosuSourceSet) {
    compileJava.configure(task -> {
        // Source already configured in Phase 1

        // Make source inputs incremental-aware
        FileCollection gosuSources = gosuSourceSet.getGosu();

        // Unfortunately, we need a custom task type OR use Gradle's internal APIs
        // Recommendation: Use custom task type (ManifoldAwareJavaCompile)
    });
}
```

**Final decision: Custom task type is necessary**

The plugin should:
1. **Register custom task type** in plugin initialization
2. **Replace compileJava** with ManifoldAwareJavaCompile
3. **Configure normally**

### Step 5: Patch Manifold's ManifoldJavaFileManager

**Challenge:** Manifold's code needs to call our GradleChangedResourceFiles class.

**Option A: Submit PR to Manifold (Recommended Long-term)**
- Add Gradle support to Manifold upstream
- Cleanest solution
- Takes time to get merged

**Option B: Runtime ASM patching (Immediate solution)**
- Intercept ManifoldJavaFileManager.getChangedResourceFiles()
- Redirect to check for Gradle adapter

**Option C: Forked Manifold (Not recommended)**
- Fork and maintain our own version
- High maintenance burden

**Immediate implementation: Option B**

**New file:** `src/main/java/org/gosulang/gradle/manifold/ManifoldPatcher.java`

```java
package org.gosulang.gradle.manifold;

import org.objectweb.asm.*;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * Patches Manifold's ManifoldJavaFileManager to check for Gradle's changed files.
 *
 * This is a temporary solution until Manifold officially supports Gradle.
 */
public class ManifoldPatcher implements ClassFileTransformer
{
    @Override
    public byte[] transform(
        ClassLoader loader,
        String className,
        Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain,
        byte[] classfileBuffer)
    {
        if ("manifold/internal/javac/ManifoldJavaFileManager".equals(className)) {
            return patchManifoldJavaFileManager(classfileBuffer);
        }
        return classfileBuffer;
    }

    private byte[] patchManifoldJavaFileManager(byte[] originalBytes)
    {
        // Use ASM to patch getChangedResourceFiles() method
        ClassReader reader = new ClassReader(originalBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);

        ClassVisitor patcher = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                            String signature, String[] exceptions)
            {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                if ("getChangedResourceFiles".equals(name)) {
                    return new GetChangedFilesPatcher(mv);
                }

                return mv;
            }
        };

        reader.accept(patcher, 0);
        return writer.toByteArray();
    }

    private static class GetChangedFilesPatcher extends MethodVisitor
    {
        public GetChangedFilesPatcher(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        // Inject call to GradleChangedResourceFiles before returning
        // This is complex - might be easier to just submit a PR to Manifold
    }
}
```

**This is complex. Simpler approach:**

### Alternative: Use Gradle's Annotation Processor Classpath

**Insight:** If we put GradleChangedResourceFiles on the annotation processor classpath, Manifold can find it!

```java
private void configureManifoldAnnotationProcessor(TaskProvider<JavaCompile> compileJava) {
    compileJava.configure(task -> {
        // Add our adapter to the annotation processor classpath
        FileCollection apClasspath = task.getOptions().getAnnotationProcessorPath();
        if (apClasspath == null) {
            apClasspath = _project.getConfigurations().getByName("annotationProcessor");
        }

        // Add the gradle-gosu-plugin JAR (contains GradleChangedResourceFiles)
        // This makes it available to Manifold at compile-time
        task.getOptions().setAnnotationProcessorPath(
            apClasspath.plus(_project.files(/* path to our plugin JAR */))
        );
    });
}
```

**Then submit PR to Manifold:**

```java
// In Manifold's ManifoldJavaFileManager.getChangedResourceFiles():
public static List<File> getChangedResourceFiles()
{
    List<File> changedFiles = Collections.emptyList();

    // Try IntelliJ
    Class<?> ijType = ReflectUtil.type( "manifold.ij.jps.IjChangedResourceFiles" );
    if( ijType != null )
    {
        changedFiles = (List<File>)ReflectUtil.method( ijType, "getChangedFiles" ).invokeStatic();
        if( !changedFiles.isEmpty() ) {
            return changedFiles;
        }
    }

    // Try Gradle
    Class<?> gradleType = ReflectUtil.type( "org.gosulang.gradle.manifold.GradleChangedResourceFiles" );
    if( gradleType != null )
    {
        changedFiles = (List<File>)ReflectUtil.method( gradleType, "getChangedFiles" ).invokeStatic();
    }

    return changedFiles;
}
```

---

## Testing Strategy

### Test 1: Basic Incremental Compilation

**Scenario:**
1. Build project with 10 Gosu files
2. Change 1 Gosu file
3. Rebuild

**Expected:**
- Only 1 Gosu file recompiles
- Other 9 use existing .class files
- Build time significantly faster

**Validation:**
```bash
./gradlew clean build  # Full build
# Record time: T1

./gradlew build --info  # Should be UP-TO-DATE
# Confirm no compilation

# Modify one .gs file
echo "// change" >> src/main/gosu/Example.gs

./gradlew build --info  # Incremental build
# Should show: "Incremental Gosu compilation: 1 changed files"
# Record time: T2
# Assert: T2 << T1
```

### Test 2: Java Change Triggers Gosu Recompilation

**Scenario:**
1. Java class Person.java
2. Gosu class PersonUtil.gs (uses Person)
3. Gosu class Unrelated.gs (doesn't use Person)
4. Change Person.java

**Expected:**
- PersonUtil.gs recompiles (depends on Person)
- Unrelated.gs does NOT recompile (no dependency)

**Current limitation:** This requires dependency tracking, which we're NOT implementing yet!

**Actually:** With Manifold, this should work automatically:
- Java's Person.class changes
- Gradle's JavaCompile detects it
- javac recompiles Person.java
- PersonUtil.gs has no source changes → uses existing .class
- But wait - if Person's ABI changed, javac will detect that PersonUtil needs recompilation!

**Insight:** javac's dependency tracking handles this! We only need to track .gs file changes, not Java dependencies.

### Test 3: No Changes = No Compilation

**Scenario:**
1. Build project
2. Run build again with no changes

**Expected:**
- All tasks UP-TO-DATE
- No compilation occurs
- Build time is minimal

### Test 4: Deleted File

**Scenario:**
1. Build with file A.gs
2. Delete A.gs
3. Rebuild

**Expected:**
- A.class is removed from output
- No errors
- Other files don't recompile

---

## Performance Benchmarks

**Baseline metrics to capture:**

1. **Full build time:** Clean build with all sources
2. **Incremental build (1 file changed):** Time to rebuild after changing 1 Gosu file
3. **Incremental build (10 files changed):** Time with 10% of files changed
4. **No-op build:** Time when nothing changed

**Target improvements:**
- Incremental (1 file): 10-20x faster than full build
- Incremental (10 files): 2-5x faster than full build
- No-op: <1 second

**Example project:**
- 100 Java classes
- 100 Gosu classes
- Typical Guidewire-style entity model

---

## Migration Guide

**For users upgrading from Phase 1 to Phase 2:**

No code changes required! Incremental compilation is automatic.

**To verify it's working:**
```bash
# Full build
./gradlew clean build

# Change one file
echo "// test" >> src/main/gosu/Example.gs

# Incremental build
./gradlew build --info | grep "Incremental Gosu compilation"
# Should show: "Incremental Gosu compilation: 1 changed files"
```

---

## Rollback Plan

**If incremental compilation has issues:**

Add flag to disable:
```gradle
compileJava {
    // Disable Gosu incremental compilation
    options.compilerArgs += '-Dmanifold.incremental=false'
}
```

Or:
```java
// In ManifoldAwareJavaCompile
if (project.hasProperty("gosu.incremental") &&
    project.property("gosu.incremental") == "false") {
    // Always clear changed files (force full rebuild)
    GradleChangedResourceFiles.clear();
}
```

---

## Known Limitations

1. **Java-Gosu dependency tracking:**
   - Currently relies on javac's built-in tracking
   - Fine-grained Java→Gosu tracking may need additional work

2. **Cross-module dependencies:**
   - Multi-module incremental builds need testing
   - May need per-module changed file tracking

3. **Build cache:**
   - Need to validate interaction with Gradle's build cache
   - Changed file tracking must be cache-aware

4. **Configuration cache:**
   - Thread-local state must be compatible

---

## Success Metrics

**Phase 2 is successful if:**
1. ✅ Incremental builds 10x+ faster than full builds (1 file changed)
2. ✅ Correctness: Zero false negatives (missed recompilations)
3. ✅ Low false positives: <5% unnecessary recompilations
4. ✅ All Phase 1 tests still pass
5. ✅ Build cache works correctly
6. ✅ Production-ready on reference projects

---

## Next Steps After Phase 2

1. Performance tuning and optimization
2. Multi-module incremental builds
3. Integration with Gradle Enterprise (build scans)
4. Documentation and user guide
5. Production rollout to Guidewire projects

---

## Upstream Contribution

**PR to Manifold project:**
- Title: "Add Gradle support for incremental compilation"
- Changes: Update ManifoldJavaFileManager.getChangedResourceFiles()
- Benefits: All Gradle users of Manifold benefit
- Reduces maintenance burden for gradle-gosu-plugin

**Draft PR description:**
```markdown
## Add Gradle Support for Incremental Compilation

This PR extends Manifold's incremental compilation support to work with Gradle builds,
similar to the existing IntelliJ integration.

### Changes:
- Modified `ManifoldJavaFileManager.getChangedResourceFiles()` to check for Gradle adapter
- No breaking changes - IntelliJ integration continues to work

### Testing:
- Verified with gradle-gosu-plugin
- Incremental compilation working as expected
- Performance improvements: 10-20x faster incremental builds

### Benefits:
- Enables incremental compilation for all Gradle users of Manifold
- Consistent behavior across IntelliJ and Gradle
- Reduced build times
```

---

## References

- [Gradle Incremental Tasks](https://docs.gradle.org/current/userguide/custom_tasks.html#incremental_tasks)
- [Gradle InputChanges API](https://docs.gradle.org/current/javadoc/org/gradle/work/InputChanges.html)
- Manifold's ManifoldJavaFileManager.java
- Phase 1 Implementation Plan
