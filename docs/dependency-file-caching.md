# Make Dependency File a Gradle Build Cache Artifact

## Executive Summary

**Goal:** Declare the gosuc dependency file as a Gradle task output so it gets cached alongside compiled class files, enabling incremental compilation immediately after a cache restore.

**Current State:**
- Dependency file is marked as `@Input` in GosuCompileOptions
- Gradle treats it as configuration, not a task output
- Not included in build cache artifacts

**Target State:**
- Dependency file declared as `@OutputFile` on GosuCompile task
- Gradle caches it with class files
- On cache hit, both classes AND dependency file are restored
- Next incremental build can immediately use cached dependency file

**Prerequisites (Already Complete):**
✅ UTF-8 encoding with explicit `StandardCharsets.UTF_8`
✅ Deterministic ordering (sorted keys and values)
✅ Cross-platform compatible (only FQCNs, no file paths)
✅ Readable angle brackets: Gson configured with `.disableHtmlEscaping()` for better readability

**Complexity:** Medium - requires careful path resolution and Gradle conventions

---

## JSON Character Set & Escaping Clarification

**Q: Does JSON imply a specific character set?**
- Yes, RFC 8259 requires UTF-8, UTF-16, or UTF-32
- UTF-8 is the default and most common
- ✅ Already fixed with `StandardCharsets.UTF_8` in commit 8f9e977a9

**Q: Do we need to escape angle brackets `<` and `>` in JSON?**
- **JSON spec does NOT require it** - angle brackets are valid in strings
- **However, Gson escapes them by default** for XSS security (when JSON embedded in HTML)
  - `<` → `\u003c`, `>` → `\u003e`
  - Example: `"ArrayList<String>"` → `"ArrayList\u003cString\u003e"`
- **For our use case (on-disk build metadata):**
  - No web context, no XSS risk
  - Disabled HTML escaping with `.disableHtmlEscaping()` for readability
  - ✅ Now writes: `"ArrayList<String>"` (readable, smaller file)
  - Safe because: never sent over network, never embedded in HTML, internal tooling only

**Note:** This change invalidates existing cached dependency files (different byte content). This is a one-time cache miss for improved readability going forward.

---

## Problem Analysis

### Current Architecture

**GosuCompileOptions** (`GosuCompileOptions.java`):
```java
@Input
@Optional
public String getDependencyFile() {
  return dependencyFile;
}
```

- **Problem:** `@Input` means Gradle treats this as configuration input, not task output
- Gradle won't cache the file
- On cache restore, only class files are restored, not the dependency file

**Path Resolution** (`CommandLineGosuCompiler.java`, lines 165-180):
```java
String dependencyFile = spec.getGosuCompileOptions().getDependencyFile();
if (dependencyFile == null || dependencyFile.isEmpty()) {
  // Default to build/tmp/gosuc-deps-{taskName}.json
  String taskName = _projectName.replaceAll(":", "");
  if (taskName.isEmpty()) {
    taskName = "default";
  }
  File defaultDepFile = new File(_project.getBuildDir(),
                                  "tmp/gosuc-deps-" + taskName + ".json");
  dependencyFile = defaultDepFile.getAbsolutePath();
} else if (!new File(dependencyFile).isAbsolute()) {
  // Make relative paths relative to project directory
  dependencyFile = new File(_project.getProjectDir(), dependencyFile).getAbsolutePath();
}
```

- Path resolution happens at **execution time** in the compiler
- GosuCompile task doesn't know the resolved path at **configuration time**
- Gradle needs to know outputs at configuration time for cache key calculation

### Why This Matters for Build Cache

**Ideal Scenario:**
1. Developer clones repo (clean workspace)
2. Runs build → cache hit restores:
   - ✅ Class files to `build/classes/java/main`
   - ✅ Dependency file to `build/tmp/gosuc-deps-compileGosu.json`
3. Changes one file → only that file + consumers recompile (thanks to cached dependency file)

**Current Scenario:**
1. Developer clones repo (clean workspace)
2. Runs build → cache hit restores:
   - ✅ Class files to `build/classes/java/main`
   - ❌ No dependency file (not a declared output)
3. Changes one file → **full recompilation** (no dependency baseline)

---

## Solution Design

### Approach: Add @OutputFile to GosuCompile Task

**Why on GosuCompile, not GosuCompileOptions?**
- GosuCompile is the `@CacheableTask` (line 34)
- It has access to project context (`getProject()`)
- Gradle conventions: outputs are declared on tasks, not options
- Follows pattern of `getDestinationDirectory()` (inherited from AbstractCompile)

**Path Resolution Strategy:**
- Move path resolution logic to GosuCompile (from CommandLineGosuCompiler)
- Resolve path at **configuration time**, not execution time
- Make resolved path available to both Gradle (for caching) and compiler (for execution)

### Implementation Steps

#### 1. Add Output File Property to GosuCompile

**File:** `src/main/java/org/gosulang/gradle/tasks/compile/GosuCompile.java`

Add a property that:
- Resolves the dependency file path (handling defaults and relative paths)
- Decorates it with `@OutputFile`
- Returns it as a `RegularFileProperty` (modern Gradle API)

**Location:** After existing properties, before `compile()` method (around line 55)

```java
private final RegularFileProperty dependencyFile = getProject().getObjects().fileProperty();

/**
 * The dependency tracking file for incremental compilation.
 * This file is generated by gosuc and tracks type-level dependencies.
 * It is cached alongside class files for cross-build incremental compilation.
 *
 * @return The dependency file output location
 */
@OutputFile
public RegularFileProperty getDependencyFileOutput() {
  return dependencyFile;
}
```

#### 2. Initialize Dependency File Path in Task Configuration

**File:** `src/main/java/org/gosulang/gradle/tasks/compile/GosuCompile.java`

Add logic to resolve and set the dependency file path, similar to existing path resolution:

**Option A: In constructor or init block**
```java
public GosuCompile() {
  // Initialize dependency file with default value
  dependencyFile.convention(
    getProject().getLayout().getBuildDirectory()
      .file("tmp/gosuc-deps-" + getName() + ".json")
  );
}
```

**Option B: In compile() method before execution**
```java
@TaskAction
protected void compile(InputChanges inputs) {
  // Resolve dependency file path from options or use default
  String configuredPath = getOptions().getDependencyFile();
  if (configuredPath != null && !configuredPath.isEmpty()) {
    File resolved = new File(configuredPath).isAbsolute()
      ? new File(configuredPath)
      : getProject().file(configuredPath);
    dependencyFile.set(resolved);
  }
  // else: use convention value set in constructor

  // ... rest of compile logic
}
```

**Recommendation:** Use Option A with convention + Option B to allow overrides

#### 3. Use Resolved Path in Compiler

**File:** `src/main/java/org/gosulang/gradle/tasks/compile/CommandLineGosuCompiler.java`

**Current (lines 165-180):** Path resolution happens here

**Change:** Accept resolved path from GosuCompile instead of resolving it here

**Update DefaultGosuCompileSpec** to include resolved dependency file path:
```java
// In DefaultGosuCompileSpec.java
private File _dependencyFile;

public void setDependencyFile(File dependencyFile) {
  _dependencyFile = dependencyFile;
}

public File getDependencyFile() {
  return _dependencyFile;
}
```

**Update GosuCompile.createSpec()** (around line 411):
```java
spec.setDependencyFile(getDependencyFileOutput().get().getAsFile());
```

**Update CommandLineGosuCompiler.createArgFile()** (lines 165-180):
```java
// Simplify - path is already resolved
File dependencyFile = spec.getDependencyFile();
if (dependencyFile != null) {
  fileOutput.add("-dependency-file");
  fileOutput.add(dependencyFile.getAbsolutePath());
}
```

#### 4. Remove @Input Annotation from GosuCompileOptions

**File:** `src/main/java/org/gosulang/gradle/tasks/compile/GosuCompileOptions.java`

**Current (line 152):**
```java
@Input
@Optional
public String getDependencyFile() {
```

**Change to:**
```java
@Optional
public String getDependencyFile() {
```

**Rationale:**
- No longer an input (it's an output on GosuCompile)
- Still useful for user configuration (they can set custom path)
- But doesn't affect Gradle task inputs/outputs

**Keep the setter** - users can still configure custom paths:
```groovy
compileGosu {
  gosuOptions.dependencyFile = 'custom/path/deps.json'
}
```

---

## Alternative Approach: RegularFileProperty Convention

**Modern Gradle approach** uses `Property` API with conventions:

```java
// In GosuCompile
private final RegularFileProperty dependencyFile;

@Inject
public GosuCompile(ObjectFactory objects) {
  this.dependencyFile = objects.fileProperty();

  // Set convention (default value)
  this.dependencyFile.convention(
    getProject().getLayout().getBuildDirectory()
      .file(getProject().provider(() ->
        "tmp/gosuc-deps-" + getName() + ".json"
      ))
  );
}

@OutputFile
public RegularFileProperty getDependencyFile() {
  return dependencyFile;
}

// Users can override:
// compileGosu.dependencyFile.set(file('custom/path/deps.json'))
```

**Benefits:**
- Lazy evaluation (path resolved when needed)
- Type-safe API
- Better integration with Gradle configuration cache
- Follows modern Gradle conventions

**Trade-off:**
- More complex API change
- Requires updating GosuCompileOptions to use `Property` types

**Recommendation:** Start with simpler approach (Option A/B), migrate to `Property` API later if needed.

---

## Critical Files to Modify

### Primary Changes

1. **`src/main/java/org/gosulang/gradle/tasks/compile/GosuCompile.java`**
   - Add `@OutputFile` property for dependency file
   - Initialize with default path convention
   - Pass resolved path to compiler via spec

2. **`src/main/java/org/gosulang/gradle/tasks/compile/DefaultGosuCompileSpec.java`**
   - Add `File _dependencyFile` field
   - Add getter/setter methods

3. **`src/main/java/org/gosulang/gradle/tasks/compile/CommandLineGosuCompiler.java`**
   - Simplify path resolution (accept from spec instead of resolving)
   - Remove default path logic (lines 165-180)

4. **`src/main/java/org/gosulang/gradle/tasks/compile/GosuCompileOptions.java`**
   - Remove `@Input` annotation from `getDependencyFile()`
   - Keep getter/setter for user configuration

### Testing Changes

5. **Functional tests** - verify cache behavior:
   - Test 1: Build, clean, build again → dependency file restored from cache
   - Test 2: Cache hit + one file change → only changed file + consumers recompile
   - Test 3: Custom dependency file path works correctly

---

## Verification Plan

### Unit Tests

**Test 1: Default Path Resolution**
```groovy
def project = ProjectBuilder.builder().build()
def task = project.tasks.create('compileGosu', GosuCompile)

// Should default to build/tmp/gosuc-deps-compileGosu.json
assert task.dependencyFileOutput.get().asFile.path.endsWith('tmp/gosuc-deps-compileGosu.json')
```

**Test 2: Custom Path Configuration**
```groovy
task.gosuOptions.dependencyFile = 'custom/deps.json'
// Should resolve relative to project dir
assert task.dependencyFileOutput.get().asFile.path.contains('custom/deps.json')
```

**Test 3: Absolute Path Configuration**
```groovy
task.gosuOptions.dependencyFile = '/tmp/abs-path/deps.json'
assert task.dependencyFileOutput.get().asFile.path == '/tmp/abs-path/deps.json'
```

### Functional/Integration Tests

**Test 4: Build Cache Integration**
```groovy
// Build 1: Fresh build
def result1 = GradleRunner.create()
  .withProjectDir(testProjectDir)
  .withArguments('compileGosu', '--build-cache')
  .build()

def depFile = new File(testProjectDir, 'build/tmp/gosuc-deps-compileGosu.json')
assert depFile.exists()
def depFileContent1 = depFile.text

// Clean
testProjectDir.deleteDir()
testProjectDir.mkdirs()

// Build 2: Cache restore
def result2 = GradleRunner.create()
  .withProjectDir(testProjectDir)
  .withArguments('compileGosu', '--build-cache')
  .build()

assert result2.output.contains('FROM-CACHE')
assert depFile.exists()
def depFileContent2 = depFile.text

// Verify restored dependency file is identical
assert depFileContent1 == depFileContent2
```

**Test 5: Incremental Compilation After Cache Restore**
```groovy
// Build 1: Fresh build with cache
GradleRunner.create()
  .withProjectDir(testProjectDir)
  .withArguments('compileGosu', '--build-cache')
  .build()

// Clean (simulating fresh checkout)
def classesDir = new File(testProjectDir, 'build/classes')
def tmpDir = new File(testProjectDir, 'build/tmp')
classesDir.deleteDir()
tmpDir.deleteDir()

// Build 2: Cache restore
GradleRunner.create()
  .withProjectDir(testProjectDir)
  .withArguments('compileGosu', '--build-cache')
  .build()

// Verify both class files and dependency file restored
assert new File(testProjectDir, 'build/classes/gosu/main/com/example/MyClass.class').exists()
assert new File(testProjectDir, 'build/tmp/gosuc-deps-compileGosu.json').exists()

// Build 3: Change one file
def sourceFile = new File(testProjectDir, 'src/main/gosu/com/example/MyClass.gs')
sourceFile.text = sourceFile.text.replace('// marker', '// changed')

def result = GradleRunner.create()
  .withProjectDir(testProjectDir)
  .withArguments('compileGosu', '--info')
  .build()

// Should only recompile MyClass + consumers (not a full rebuild)
assert result.output.contains('incremental')
assert !result.output.contains('Full recompilation')
```

### Manual Verification

1. **Build with cache:**
   ```bash
   cd /home/node/dev/gradle-gosu-plugin
   ./gradlew compileGosu --build-cache
   ```

2. **Verify dependency file exists:**
   ```bash
   ls -lh build/tmp/gosuc-deps-compileGosu.json
   cat build/tmp/gosuc-deps-compileGosu.json
   ```

3. **Clean and restore from cache:**
   ```bash
   rm -rf build/classes build/tmp
   ./gradlew compileGosu --build-cache
   ```

4. **Verify both restored:**
   ```bash
   ls -lh build/classes/gosu/main/
   ls -lh build/tmp/gosuc-deps-compileGosu.json
   ```

5. **Test incremental compilation:**
   ```bash
   # Change one source file
   echo "// marker" >> src/main/gosu/SomeFile.gs
   ./gradlew compileGosu --info | grep -i "incremental\|recompil"
   ```

---

## Implementation Sequence

### Phase 1: Core Changes
1. Add `@OutputFile` property to GosuCompile
2. Add dependency file field to DefaultGosuCompileSpec
3. Update GosuCompile.createSpec() to set dependency file
4. Simplify CommandLineGosuCompiler path resolution

### Phase 2: Clean Up
5. Remove `@Input` from GosuCompileOptions.getDependencyFile()
6. Update documentation/comments

### Phase 3: Testing
7. Add unit tests for path resolution
8. Add functional tests for cache behavior
9. Manual verification with real projects

### Phase 4: Documentation
10. Update README with cache behavior explanation
11. Add example configurations

---

## Edge Cases & Considerations

### 1. Multi-Module Projects

**Issue:** Multiple GosuCompile tasks in different modules
- Each needs its own dependency file
- Default naming: `gosuc-deps-{taskName}.json`
- Task name includes module: `:app:compileGosu` → `gosuc-deps-app-compileGosu.json`

**Solution:** Use `getName()` in default path (already planned)

### 2. Custom Task Names

**Issue:** User creates custom GosuCompile task
```groovy
task compileIntegrationGosu(type: GosuCompile) {
  // ...
}
```

**Solution:** `getName()` returns `"compileIntegrationGosu"` → unique file name

### 3. Dependency File Deletion

**Issue:** What if user deletes dependency file manually?
- Gradle will detect missing output
- Task will re-execute (not FROM-CACHE)
- gosuc will create new dependency file (empty baseline)
- Full recompilation will occur (expected behavior)

**Solution:** No special handling needed - Gradle's behavior is correct

### 4. Parallel Task Execution

**Issue:** Multiple GosuCompile tasks running in parallel
- Each has unique dependency file path (by task name)
- No conflicts

**Solution:** Already handled by naming convention

### 5. Configuration Cache Compatibility

**Issue:** Gradle's configuration cache serializes task configuration
- `RegularFileProperty` is serializable ✓
- Path resolution must happen at execution time, not configuration time

**Solution:** Use `convention()` with `Provider` for lazy evaluation:
```java
dependencyFile.convention(
  getProject().getLayout().getBuildDirectory()
    .file(getProject().provider(() -> "tmp/gosuc-deps-" + getName() + ".json"))
);
```

### 6. Incremental Task Inputs

**Issue:** GosuCompile uses `InputChanges` for incremental compilation
- Currently detects source file changes
- Dependency file is output, not input
- No circular dependency

**Solution:** No issues - dependency file flows in one direction (compilation → dependency file)

---

## Gradle Build Cache Behavior

### Cache Key Calculation

Gradle calculates cache key from:
- **Task inputs:** Source files, classpath, compiler options
- **Task type:** GosuCompile class
- **Task configuration:** Options, flags, etc.

**Cache key does NOT include:**
- Task outputs (they're what gets cached)
- Dependency file content (it's an output)

### Cache Storage

When task executes:
1. gosuc compiles sources → generates class files + dependency file
2. Gradle packages outputs into cache entry:
   - All files in `getDestinationDirectory()` (class files)
   - File at `getDependencyFileOutput()` (dependency file)
3. Stores in local build cache (default: `~/.gradle/caches/build-cache-1/`)

### Cache Restore

On subsequent build with matching inputs:
1. Gradle finds cache entry (matching cache key)
2. Restores ALL outputs:
   - Unpacks class files to `build/classes/gosu/main/`
   - Restores dependency file to `build/tmp/gosuc-deps-compileGosu.json`
3. Marks task as `FROM-CACHE`
4. Skips task execution

### Next Incremental Build

After cache restore:
1. Developer changes one source file
2. GosuCompile detects change via `InputChanges`
3. gosuc reads cached dependency file
4. gosuc finds consumers of changed type
5. gosuc recompiles only changed file + consumers
6. gosuc updates dependency file
7. New outputs cached for next build

**Result:** Incremental compilation works immediately after cache restore! ✓

---

## Summary

**Changes Required:**
1. Add `@OutputFile` to GosuCompile task
2. Move path resolution from compiler to task
3. Pass resolved path to compiler via spec
4. Remove `@Input` from GosuCompileOptions

**Benefits:**
- Dependency file cached with class files
- Incremental compilation works after cache restore
- Cross-platform compatible (UTF-8, deterministic, FQCN-only)
- Follows Gradle conventions

**Testing:**
- Unit tests for path resolution
- Functional tests for cache behavior
- Manual verification with real projects

**Complexity:** Medium (path resolution refactoring)

**Impact:** High (enables incremental compilation in CI/cached builds)
