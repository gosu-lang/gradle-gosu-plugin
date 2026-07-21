# Manifold Integration - Phase 0 & 1: Validation and Basic Compilation

**Goal:**
- **Phase 0:** Validate Manifold + Gradle incremental compilation compatibility ✅ **COMPLETE**
- **Phase 1:** Replace two-phase compilation with single-pass javac+Manifold compilation

**Status:**
- Phase 0: ✅ COMPLETE (Jan 7, 2026) - GREEN LIGHT for Phase 1
- Phase 1: Ready to begin
**Date:** January 2026

---

## Overview

**Phase 0** validates that Manifold doesn't break Gradle's incremental Java compilation. This is critical because Manifold must be placed on the `annotationProcessor` path, which may cause Gradle to treat it as a non-incremental annotation processor.

**Phase 1** focuses on **correctness only** - ensuring that compiling via javac+Manifold produces identical outputs to the current two-phase gosuc approach. No incremental compilation of Gosu sources yet (that's Phase 2).

**Success Criteria:**

**Phase 0:** ✅ **COMPLETE**
- ✅ Manifold doesn't break Gradle's incremental Java compilation *(validated)*
- ✅ No warnings about non-incremental annotation processors *(validated)*
- ✅ Incremental builds only recompile changed Java files *(validated)*

**Phase 1:**
- ✅ All Java and Gosu sources compile in single pass
- ✅ Generated .class files are functionally equivalent
- ✅ All existing tests pass
- ✅ Runtime classpath is correct (both Java and Gosu classes loadable)
- ✅ No regressions in compilation behavior
- ✅ Gradle incremental Java compilation still works (Phase 0 validation continues to hold)

**Note on Output Structure:**
- Current: `build/classes/java/main` + `build/classes/gosu/main` (two directories)
- Manifold: `build/classes/java/main` only (single directory, standard Gradle convention)
- This is a **simplification**, not a regression

---

## Current State Analysis

### Current Two-Phase Approach

**Task Chain:**
```
compileJava → compileGosu → classes
```

**How it works:**
1. `compileJava` - Gradle's JavaCompile task compiles .java files to `build/classes/java/main`
2. `compileGosu` - Custom GosuCompile task invokes gosuc CLI, writes to `build/classes/gosu/main`
3. Both directories end up on the runtime classpath

**Configuration (GosuBasePlugin.java:86-87):**
```java
configureForSourceSet(sourceSet, gosuSourceSet.getGosu(), gosuCompile, _project);
gosuCompile.configure(t -> t.dependsOn(sourceSet.getCompileJavaTaskName()));
```

### Gosu-Manifold Integration (Already Exists)

**Files:**
- `/tmp/gosu-lang/gosu-core-api/pom.xml` - Manifold dependency (version 2024.1.38)
- `GosuTypeManifold.java` - Implements ITypeManifold for Gosu
- `GosuRuntimeManifoldHost.java` - Runtime host integration
- META-INF service registrations

**What it provides:**
- Stub generation for Gosu types
- Self-compilation (Gosu produces its own bytecode)
- Integration with Manifold's type system

---

## Phase 0: Verify Manifold + Gradle Incremental Compilation Compatibility

**Goal:** Validate that Manifold doesn't break Gradle's incremental Java compilation before attempting full integration.

**Why this is critical:** Manifold must be placed on the `annotationProcessor` path for javac to discover it, but Gradle may treat it as an annotation processor and check if it supports incremental annotation processing. If Manifold is non-incremental, Gradle will disable incremental compilation for the entire module, causing full recompilation on every change.

### Phase 0 Testing Plan

**Step 1: Create a minimal test project**

Create a simple Gradle Java project with:
- A few Java source files
- Manifold dependency on `annotationProcessor` path
- `-Xplugin:Manifold` compiler argument
- NO Gosu sources yet (pure Java + Manifold test)

**build.gradle:**
```gradle
plugins {
    id 'java'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'systems.manifold:manifold-rt:2024.1.38'
    annotationProcessor 'systems.manifold:manifold:2024.1.38'
}

compileJava {
    options.compilerArgs += '-Xplugin:Manifold'
}
```

**Step 2: Test incremental compilation behavior**

1. **Clean build:**
   ```bash
   ./gradlew clean compileJava --info
   ```
   - Look for warnings: "Gradle may disable incremental compilation as the following annotation processors are not incremental"
   - Check if Manifold is listed as non-incremental

2. **Make a trivial change to ONE Java file:**
   ```bash
   # Edit one file (add a comment)
   ./gradlew compileJava --info
   ```
   - **Expected (incremental):** Only the changed file recompiles
   - **Failure (non-incremental):** ALL Java files recompile
   - Search logs for "Full recompilation is required"

3. **Verify via Gradle's incremental compilation logs:**
   - Check for: `Compiler arguments: ...` to see if incremental is disabled
   - Look for file change detection: "Incremental compilation of X classes completed"
   - Confirm only the modified file's .class is regenerated

**Step 3: Document findings**

Create a test report documenting:
- ✅ Does Manifold support Gradle incremental compilation?
- ✅ Are there any warning messages in build logs?
- ✅ Performance: First build vs. incremental build times
- ⚠️ Any workarounds needed?

**Decision Point:**
- **If incremental works:** Proceed to Phase 1 (basic Gosu+Java compilation)
- **If incremental breaks:** Investigate alternatives:
  - Check if newer Manifold versions support incremental annotation processing
  - Explore alternative javac plugin discovery mechanisms
  - Consider filing an issue with Manifold project
  - May need to fall back to Approach A or B from comparison doc

**References:**
- [Making an annotation processor incremental](https://docs.gradle.org/current/userguide/java_plugin.html#making_an_annotation_processor_incremental)
- [Gradle Incremental Annotation Processing](https://docs.gradle.org/current/userguide/java_plugin.html#sec:incremental_annotation_processing)
- Test project location: `TODO: Create gradle-gosu-plugin/test-projects/manifold-incremental-test/`

**✅ COMPLETED: Phase 0 Testing**
- [x] Create test project at `gradle-gosu-plugin/test-projects/manifold-incremental-test/`
- [x] Follow testing procedure above
- [x] Document results in test report
- [x] Make go/no-go decision for Phase 1

**Phase 0 Result: ✅ SUCCESS - GREEN LIGHT FOR PHASE 1**

See [test-projects/manifold-incremental-test/PHASE0_TEST_REPORT.md](test-projects/manifold-incremental-test/PHASE0_TEST_REPORT.md) for detailed results.

**Key Findings:**
- Manifold 2024.1.38 does NOT break Gradle 8.6's incremental Java compilation
- No warnings about non-incremental annotation processors
- Incremental builds correctly recompile only changed files and their dependents
- Test validated with 3 Java classes, showing 66% reduction in recompilation

**Decision: PROCEED TO PHASE 1**

---

## Implementation Plan (Phase 1+)

**⚠️ NOTE:** The following phases assume Phase 0 verified Manifold compatibility with Gradle incremental compilation.

### Step 1: Add Manifold Dependencies to gradle-gosu-plugin

**File:** `build.gradle` (or equivalent)

**Changes needed:**
```gradle
dependencies {
    // Add Manifold runtime
    implementation 'systems.manifold:manifold-rt:2024.1.38'

    // Note: Manifold javac plugin JAR will be added to user projects'
    // annotation processor path (where javac discovers plugins via ServiceLoader)
}
```

**⚠️ WARNING: Potential Gradle Incremental Compilation Issue**

Manifold is a javac compiler plugin (not an annotation processor), but it must be placed on the `annotationProcessor` path for javac to discover it via ServiceLoader. This may cause Gradle to treat it as an annotation processor and check if it supports incremental annotation processing.

If Manifold doesn't declare itself as an [incremental annotation processor](https://docs.gradle.org/current/userguide/java_plugin.html#sec:incremental_annotation_processing), Gradle may disable incremental compilation for the entire module. According to Gradle's documentation, "annotation processing can be incremental only if all annotation processors being used are incremental."

**Impact:**
- If Manifold is non-incremental: Full Java recompilation on any source change
- This would negate the benefits we're trying to achieve

**Testing Priority:**
- Phase 1 must verify that Gradle's incremental compilation still works
- Monitor build logs for: "Gradle may disable incremental compilation as the following annotation processors are not incremental"
- Use `./gradlew compileJava --info` to check for incremental behavior

**References:**
- [Gradle Incremental Annotation Processing](https://docs.gradle.org/current/userguide/java_plugin.html#sec:incremental_annotation_processing)
- [Common non-incremental processors](https://github.com/google/dagger/issues/1514)

### Step 2: Modify GosuBasePlugin to Use JavaCompile for Both

**File:** [GosuBasePlugin.java](src/main/java/org/gosulang/gradle/GosuBasePlugin.java)

**Current behavior (lines 83-87):**
```java
private void configureGosuCompile(SourceSet sourceSet, GosuSourceSet gosuSourceSet) {
    String compileTaskName = sourceSet.getCompileTaskName("gosu");
    TaskProvider<? extends AbstractCompile> gosuCompile = _project.getTasks().register(compileTaskName, GosuCompile.class);
    configureForSourceSet(sourceSet, gosuSourceSet.getGosu(), gosuCompile, _project);
    gosuCompile.configure(t -> t.dependsOn(sourceSet.getCompileJavaTaskName()));
}
```

**New behavior:**
```java
private void configureManifoldJavaCompile(SourceSet sourceSet, GosuSourceSet gosuSourceSet) {
    // Get the existing compileJava task
    TaskProvider<JavaCompile> compileJava = _project.getTasks().named(
        sourceSet.getCompileJavaTaskName(),
        JavaCompile.class
    );

    // Configure it to compile both Java and Gosu sources
    compileJava.configure(task -> {
        // Add Gosu sources to the compilation
        task.source(gosuSourceSet.getGosu());

        // Add Manifold plugin JAR to annotation processor path
        // (javac discovers plugins via ServiceLoader from this path)
        task.getOptions().getAnnotationProcessorPath().from(
            _project.getConfigurations().getByName("annotationProcessor")
        );

        // Activate Manifold compiler plugin
        List<String> compilerArgs = task.getOptions().getCompilerArgs();
        if (!compilerArgs.contains("-Xplugin:Manifold")) {
            compilerArgs.add("-Xplugin:Manifold");
        }

        // Ensure Gosu runtime is on classpath
        task.setClasspath(task.getClasspath().plus(
            _gosuRuntime.inferGosuClasspath(task.getClasspath())
        ));
    });

    // Optional: Create an alias task for backwards compatibility
    _project.getTasks().register(sourceSet.getCompileTaskName("gosu"), task -> {
        task.dependsOn(compileJava);
        task.setGroup("build");
        task.setDescription("Compiles Gosu sources (via Manifold + JavaCompile)");
    });
}
```

**Key changes:**
- No longer creates custom GosuCompile task
- Enhances existing JavaCompile task to handle .gs files
- Adds Manifold compiler plugin
- Maintains backwards compatibility with alias task

### Step 3: Configure User Projects to Use Manifold

**Users need to add to their build.gradle:**

```gradle
plugins {
    id 'org.gosu-lang.gosu' version 'X.Y.Z'
}

dependencies {
    // Gosu runtime (already required)
    implementation 'org.gosu-lang.gosu:gosu-core-api:1.18.5'

    // NEW: Manifold javac plugin JAR
    // Added to annotationProcessor path so javac can discover the plugin
    annotationProcessor 'systems.manifold:manifold:2024.1.38'
}

// Optional: Activate Manifold compiler plugin (may be auto-configured by plugin)
compileJava {
    options.compilerArgs += '-Xplugin:Manifold'
}
```

**Plugin should auto-configure this:**

```java
private void configureManifoldDependencies() {
    _project.afterEvaluate(p -> {
        // Auto-add Manifold plugin JAR to annotation processor path if not present
        // (javac looks for compiler plugins via ServiceLoader on this path)
        Configuration annotationProcessor = p.getConfigurations().getByName("annotationProcessor");

        boolean hasManifold = annotationProcessor.getDependencies().stream()
            .anyMatch(dep -> dep.getGroup().equals("systems.manifold")
                          && dep.getName().equals("manifold"));

        if (!hasManifold) {
            p.getLogger().info("Auto-adding Manifold compiler plugin");
            p.getDependencies().add("annotationProcessor", "systems.manifold:manifold:2024.1.38");
        }
    });
}
```

### Step 4: Handle Source Set Configuration

**File:** [GosuBasePlugin.java](src/main/java/org/gosulang/gradle/GosuBasePlugin.java)

**Current approach (lines 70-74):**
```java
private void configureSourceSets() {
    javaPluginExtension(_project).getSourceSets().all(sourceSet -> {
      GosuSourceSet gosuSourceSet = new DefaultGosuSourceSet(sourceSet.getName(), _project.getObjects());
      ((ExtensionAware) sourceSet).getExtensions().add(GosuSourceSet.NAME, gosuSourceSet);
      gosuSourceSet.getGosu().srcDir("src/" + sourceSet.getName() + "/gosu");
      sourceSet.getResources().getFilter().exclude(element -> gosuSourceSet.getGosu().contains(element.getFile()));
      sourceSet.getAllSource().source(gosuSourceSet.getGosu());
      configureGosuCompile(sourceSet, gosuSourceSet);
    });
}
```

**Updated approach:**
```java
private void configureSourceSets() {
    javaPluginExtension(_project).getSourceSets().all(sourceSet -> {
      GosuSourceSet gosuSourceSet = new DefaultGosuSourceSet(sourceSet.getName(), _project.getObjects());
      ((ExtensionAware) sourceSet).getExtensions().add(GosuSourceSet.NAME, gosuSourceSet);
      gosuSourceSet.getGosu().srcDir("src/" + sourceSet.getName() + "/gosu");

      // Add .gs files to allSource (so they're included in compileJava)
      sourceSet.getAllSource().source(gosuSourceSet.getGosu());

      // Exclude .gs files from resources (they're source, not resources)
      sourceSet.getResources().getFilter().exclude(element ->
          gosuSourceSet.getGosu().contains(element.getFile()));

      // Configure JavaCompile to handle Gosu via Manifold
      configureManifoldJavaCompile(sourceSet, gosuSourceSet);
    });
}
```

### Step 5: Remove or Deprecate GosuCompile Task

**Options:**

**Option A: Keep for backwards compatibility (recommended for Phase 1)**
- Leave GosuCompile task in codebase
- Make it delegate to JavaCompile
- Add deprecation warning
- Remove in future major version

**Option B: Remove entirely**
- Delete GosuCompile.java and related classes
- Update documentation
- Breaking change requiring major version bump

**Recommended: Option A for Phase 1**

```java
@Deprecated
@CacheableTask
public class GosuCompile extends AbstractCompile {
    public GosuCompile() {
        getLogger().warn(
            "GosuCompile task is deprecated. Gosu compilation now happens via JavaCompile with Manifold. " +
            "This task is a no-op and will be removed in a future version."
        );
    }

    @TaskAction
    protected void compile() {
        // No-op: compilation already happened via JavaCompile
        getLogger().info("Gosu compilation completed via JavaCompile + Manifold");
    }
}
```

---

## Testing Strategy

### Phase 1.1: Basic Functionality Tests

**Goal:** Verify that single-pass compilation works at all

**Test 1: Simple Java + Gosu Project**
```
src/main/java/com/example/JavaClass.java
src/main/gosu/com/example/GosuClass.gs (references JavaClass)
```

**Expected:**
- Both files compile in single pass
- .class files generated in correct location
- Gosu can reference Java types
- Java can reference Gosu types (via stub)

**Test 2: Gosu Enhancement of Java Class**
```
src/main/java/com/example/Person.java
src/main/gosu/com/example/PersonEnhancement.gsx
```

**Expected:**
- Enhancement methods visible to Java code
- Proper bytecode generation

### Phase 1.2: Compatibility Tests

**Goal:** Ensure feature parity with existing implementation

**Test Suite:**
1. All existing gradle-gosu-plugin functional tests must pass
2. Compilation output must be byte-identical (or functionally equivalent)
3. IDE integration must work (IntelliJ IDEA)
4. Multi-module projects must compile correctly
5. Test source sets (src/test/gosu) must work

**Comparison Testing:**
```bash
# Build with old approach
git checkout main
./gradlew clean build
cp -r build/classes /tmp/old-classes

# Build with Manifold approach
git checkout manifold-integration
./gradlew clean build

# Compare outputs
diff -r /tmp/old-classes build/classes
```

### Phase 1.3: Edge Case Tests

**Test scenarios:**
1. **Circular references:** Java ↔ Gosu (should fail gracefully)
2. **Inner classes:** Java inner classes referenced by Gosu
3. **Generics:** Complex generic types across Java/Gosu boundary
4. **Annotations:** Java annotations on Gosu types
5. **Package-private access:** Cross-language visibility
6. **Static initialization:** Execution order of static blocks
7. **Annotation processors:** Custom annotation processors with Gosu

---

## Validation Checklist

Before proceeding to Phase 2:

- [ ] **⚠️ CRITICAL: Gradle incremental Java compilation still works**
  - [ ] Run `./gradlew compileJava --info` and verify no warnings about non-incremental annotation processors
  - [ ] Make a trivial Java change, recompile, verify only changed file recompiles (check logs)
  - [ ] Confirm Manifold on annotationProcessor path doesn't break Gradle's incremental compilation
- [ ] All Java sources compile successfully
- [ ] All Gosu sources compile successfully
- [ ] Generated .class files are functionally equivalent to gosuc output
- [ ] All existing tests pass
- [ ] No compilation errors in standard scenarios
- [ ] IDE integration works (if applicable)
- [ ] Multi-module builds work correctly
- [ ] Test compilation works (src/test/gosu)
- [ ] Build performance is acceptable (within 10% of current)
- [ ] Documentation updated

---

## Rollback Plan

**If issues are discovered:**

1. **Keep two-phase approach as default**
2. **Make Manifold approach opt-in via flag:**
   ```gradle
   gosuOptions {
       useManifoldCompilation = true  // experimental
   }
   ```
3. **Gather feedback and iterate**
4. **Fix issues before making default**

---

## Migration Guide for Users

**For users upgrading to Phase 1:**

### Before (Old approach):
```gradle
plugins {
    id 'org.gosu-lang.gosu'
}

dependencies {
    implementation 'org.gosu-lang.gosu:gosu-core-api:1.18.5'
}
```

### After (Manifold approach):
```gradle
plugins {
    id 'org.gosu-lang.gosu'
}

dependencies {
    implementation 'org.gosu-lang.gosu:gosu-core-api:1.18.5'
    annotationProcessor 'systems.manifold:manifold:2024.1.38'  // NEW
}

compileJava {
    options.compilerArgs += '-Xplugin:Manifold'  // NEW (or auto-configured by plugin)
}
```

**Breaking changes:**
- None (backwards compatible)
- Old `compileGosu` task still exists as alias

---

## Success Metrics

**Phase 0 is successful if:**
1. ✅ Manifold doesn't break Gradle's incremental Java compilation
2. ✅ No warnings about non-incremental annotation processors
3. ✅ Test project demonstrates incremental behavior (only changed files recompile)

**Phase 1 is successful if:**
1. ✅ 100% of existing tests pass
2. ✅ Build times within 10% of current approach
3. ✅ Zero compilation errors on reference projects
4. ✅ Identical functional behavior of compiled code
5. ✅ Gradle incremental Java compilation continues to work (Phase 0 validation still holds)
6. ✅ Team confidence to proceed to Phase 2

---

## Implementation Sequence

**Step 1: Complete Phase 0 (REQUIRED FIRST)**
- Create toy project to test Manifold + Gradle incremental compilation
- Document findings
- **Decision point:** Only proceed to Phase 1 if Phase 0 passes

**Step 2: Phase 1 Implementation** (assuming Phase 0 succeeded)
- Follow implementation plan above
- Continuously monitor that incremental compilation still works

**Step 3: After Phase 1 Validation**
1. Proceed to Phase 2: Incremental Compilation (for Gosu sources)
2. Implement GradleChangedResourceFiles adapter
3. Add performance benchmarking
4. Production testing on large codebases

---

## Open Questions

### Phase 0 Questions

1. **⚠️ CRITICAL: Gradle Incremental Compilation Compatibility**
   - Does placing Manifold on `annotationProcessor` path break Gradle's incremental Java compilation?
   - If Manifold is treated as a non-incremental annotation processor, this could cause full recompilation on every change
   - **Phase 0 will answer this question** via toy project testing
   - **Potential workarounds if it fails:**
     - Check if newer Manifold versions support incremental annotation processing
     - Explore alternative javac plugin discovery mechanisms
     - Consider filing issue with Manifold project
     - May need to fall back to Approach A or B from comparison doc
   - **Related:** [Gradle Incremental Annotation Processing docs](https://docs.gradle.org/current/userguide/java_plugin.html#sec:incremental_annotation_processing)

### Phase 1 Questions (assuming Phase 0 passed)

1. **Manifold version pinning:** Should we pin to 2024.1.38 or allow newer versions?

2. **Classloader issues:** Any potential classloader conflicts between Gosu and Manifold?

3. **Source file ordering:** Does compilation order matter for Java-Gosu dependencies?

4. **Error reporting:** Are error messages as clear as gosuc's messages?

5. **Build cache:** How does this interact with Gradle's build cache?

6. **Configuration cache:** Is configuration cache compatible?

---

## References

- [Manifold Setup Documentation](https://github.com/manifold-systems/manifold/blob/master/manifold-core-parent/manifold/README.md)
- [Gradle JavaCompile Task](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.compile.JavaCompile.html)
- [Gradle Annotation Processing](https://docs.gradle.org/current/userguide/java_plugin.html#sec:annotation_processing)
- Gosu's GosuTypeManifold.java implementation
