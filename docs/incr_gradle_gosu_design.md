# Incremental Gosu Compilation — Gradle Plugin Design

## 1. Two-layer architecture

Incremental compilation is split across two repositories that meet at a
command-line contract:

| Layer | Repo | Role |
|---|---|---|
| **Gradle plugin** (driver) | `gradle-gosu-plugin` | Declares the task's incremental inputs, uses Gradle's `InputChanges` to compute *which types changed / were removed*, enumerates the same-module Java types, and **forks `gosuc` once** with those sets. Runs no loop of its own. **This is the repo documented here.** |
| **gosuc** (executor) | `gosu-lang` | Given those sets, computes the transitive recompile set from a persisted dependency graph, deletes stale outputs, compiles, records new edges from the produced bytecode, and rewrites the graph. |

The division of labour is deliberate and asymmetric:

- The plugin knows **what the build system observed changing**. It never reads the
  dependency graph and never decides what to recompile.
- gosuc knows **what depends on what**. It never inspects file timestamps or Gradle
  state.

Neither side can do the other's job: Gradle cannot see Gosu type dependencies, and
gosuc, invoked once per build, cannot see what changed since the previous build.

---

## 2. Turning it on

```groovy
compileGosu {
    gosuOptions.incrementalCompilation = true
    gosuOptions.verbose = true            // optional; drives gosuc's -verbose
}
```

`incrementalCompilation` is a new `@Input` property on `GosuCompileOptions`,
**defaulting to `false`**. When it is off, this branch's code paths are inert and the
task behaves exactly as it did before: no dep file, no extra inputs consulted, no new
CLI flags. That "off by default, zero-cost" property is pinned by
`IncrementalCompilationWithDependencyTrackingTest`:

- *Incremental compilation disabled by default* — build succeeds, no dep file.

### Preconditions

| Requirement | Enforcement |
|---|---|
| `gosuOptions.fork = true` | Hard check at task-action time; a `GradleException` names the task and explains that the in-process compiler has no incremental support. `fork` already defaults to `true`, so the guard only fires for builds that explicitly opted out. |
| gosu-lang ≥ 1.18.7 | Not enforced in code — an older `gosuc` would reject the new flags. `gradle.properties` moves the version used by functional tests to `1.18.8`. |

Only `CommandLineGosuCompiler` (the forked path, selected by
`GosuCompilerFactory` when `fork` is true) emits the incremental flags;
`InProcessGosuCompiler` is untouched by this branch.

---

## 3. The task model

The heart of the implementation is **how `GosuCompile`
declares its inputs to Gradle**, because that is what determines the quality of the
change information the plugin gets back.

`GosuCompile` is already `@CacheableTask`. This changes adds one output and one
input, and modify the character of two existing properties.

| Property | Annotations | Role |
|---|---|---|
| `getStableSources()` | `@InputFiles` **`@PathSensitive(RELATIVE)`** **`@SkipWhenEmpty`** **`@IgnoreEmptyDirectories`** | The Gosu source set, now queryable for per-file change events, and the task's only tracked view of it. |
| `getSource()` | **`@Internal("tracked via stableSources")`** | No longer an input in its own right (§3.1). |
| `getJavaClassesDir()` | **`@CompileClasspath` `@Incremental` `@Optional`** | *(new)* This source set's `compileJava` output directory, tracked ABI-sensitively and per-file. |
| `getClasspath()` | `@CompileClasspath` | Unchanged annotation; the **value** now excludes `javaClassesDir` (§6). |
| `getDependencyFile()` | **`@OutputFile` `@Optional`** | *(new)* `build/tmp/gosuc-deps-{taskName}.json`. |

### 3.1 `getStableSources()` — a dedicated `FileCollection` instance


```java
@Override
@Internal("tracked via stableSources")
public FileTree getSource() {
  return super.getSource();
}

@SkipWhenEmpty
@IgnoreEmptyDirectories
@PathSensitive(RELATIVE)
@InputFiles
public FileCollection getStableSources() {
  if (_stableSources == null) {
    _stableSources = getObjectFactory().fileCollection().from((Callable<FileTree>) this::getSource);
  }
  return _stableSources;
}
```

Note

1. **`@SkipWhenEmpty`, not `@Incremental`.** The two are interchangeable for this
   task's purpose but cannot be combined: Gradle files both under the single
   annotation category `ModifierAnnotationCategory.INCREMENTAL` and raises a
   *"conflicting incremental annotations"* validation problem for a property carrying
   both. `@SkipWhenEmpty` is the stronger of the pair — it resolves to
   `InputBehavior.PRIMARY`, which tracks per-file changes for `InputChanges` exactly
   as `@Incremental` does **and** skips the task with its previous outputs removed
   when no Gosu source remains. `@IgnoreEmptyDirectories` keeps a tree of empty
   package directories counting as "no source" for that check.
2. **`getSource()` is `@Internal`.** It covers the same files, so tracking both would
   fingerprint the source set twice and put the empty-skip decision on a different
   property than the one the task action queries. Note that `@Internal` *discards
   every annotation category inherited from `SourceTask.getSource()`* — which is
   where `@SkipWhenEmpty` and `@IgnoreEmptyDirectories` used to reach this task from,
   hence their restatement in (1). For the same reason `@PathSensitive` must be
   absent here: Gradle rejects `@Internal` declared alongside an input modifier.
3. **A distinct, cached `FileCollection` instance is returned.** Gradle keys its
   incremental-input registry by the *value object* the getter returns, and
   `InputChanges.getFileChanges(getStableSources())` looks the property up by that
   value, so an instance shared with another tracked property corrupts that mapping
   and makes `getFileChanges()` fail to resolve siblings — concretely,
   `javaClassesDir`. The collection is built from the injected `ObjectFactory` rather
   than `getProject()` so the task stays configuration-cache safe, and it is memoised
   so the identity is stable across calls within a task instance.

4. **`@PathSensitive(RELATIVE)`, not `NAME_ONLY`.** A Gosu type's FQCN *is* its path
   relative to the source root — `extractFQCNFromSourceFile` derives it that way
   (§5.2) and gosuc keys its dependency graph on the same mapping — so a source
   file's directory is semantically significant input. `NAME_ONLY` normalises every
   source to its bare filename and discards exactly that.

   The reachable failure is narrower than it first looks. A `.gs` class (or a
   `.gr`/`.grs` rule) carries a `package` declaration that must agree with its
   directory, so relocating one necessarily edits its content and `NAME_ONLY` notices
   via the content hash. A **`.gst` template carries no package declaration** — its
   type name comes from the path alone — so it can be moved byte-for-byte. Under
   `NAME_ONLY` that move produces an identical fingerprint and `compileGosu` stays
   `UP-TO-DATE`, never compiling the template under its new name. Pinned by
   *moving a template between packages is not UP-TO-DATE* in
   `CompileInputChangeDetectionTest`, which fails on exactly that assertion when the
   annotation is reverted.

   `RELATIVE` is still relocatable, so `@CacheableTask` and remote-cache portability
   are unaffected; the one-time cost is a changed cache key. It also aligns
   `GosuCompile` with `GosuDoc.getSource()`, which has been `RELATIVE` since the two
   were made cacheable in the same 2017 commit.

All four points match what Gradle's own `JavaCompile` does, down to the
`objectFactory.fileCollection().from((Callable<FileTree>) this::getSource)`
construction.

### 3.2 `getJavaClassesDir()` — the Java→Gosu channel

`GosuBasePlugin.configureGosuCompile` wires it from the source set's own Java output
directory, using public API rather than a task lookup:

```java
gosuCompileTask.setJavaClassesDir(_project.files(sourceSet.getJava().getDestinationDirectory()));
```

The annotation triple is doing three separate jobs:

- **`@CompileClasspath`** — ABI-level normalization. Gradle hashes each `.class`
  file's API surface, not its bytes, so a method-body-only edit in a same-module Java
  class produces **no change event at all** and does not re-run `compileGosu`.
- **`@Incremental`** — makes `inputChanges.getFileChanges(getJavaClassesDir())`
  legal, so the plugin can ask *which* `.class` files changed rather than only
  *whether* the directory changed.
- **`@Optional`** — Gosu-only source sets have no Java output.

`compileGosu` already `dependsOn` the source set's `compileJava` task, so the
directory is populated before this task reads it.

### 3.3 `getDependencyFile()` — a declared, cacheable output

```java
@OutputFile
@Optional
public File getDependencyFile() {
  return getLayout().getBuildDirectory()
          .file("tmp/gosuc-deps-" + getName() + ".json")
          .get().getAsFile();
}
```

- **Path is derived from the task name and is not user-configurable**, so
  `compileGosu` and `compileTestGosu` get separate graphs by construction.
- **`@OutputFile` on a `@CacheableTask`** means the dep file rides the Gradle build
  cache alongside the `.class` files. On a `FROM_CACHE` restore the graph returns to
  disk, so the *next* build can compile incrementally instead of falling back to a
  full rebuild.
- **`@Optional`** states the intent that the file is only conditionally produced:
  `gosuc` is passed `-dependency-file` solely when `incrementalCompilation` is on, so
  on a regular build the declared output never materialises. Without `@Optional`, a
  permanently absent declared output is a problem for up-to-date checking and for the
  cache round trip.
- The build directory is reached through the **injected `ProjectLayout` service**, not
  `getProject()`.

---

## 4. Execution flow


Three distinct states reach `gosuc`:

| State | Trigger | Flags emitted (§7) |
|---|---|---|
| **Non-incremental** | `incrementalCompilation = false` | none of the new flags |
| **Full rebuild** | `incrementalCompilation = true`, `inputChanges.isIncremental() == false` | `-incremental`, `-dependency-file`, `-local-java-types` — but **no** `-changed-types` / `-removed-types` |
| **Incremental** | `incrementalCompilation = true`, `inputChanges.isIncremental() == true` | all of the above **plus** `-changed-types` / `-removed-types` when non-empty |

The full-rebuild state is not a bypass: `gosuc` still runs its incremental path, but
with an empty seed set it computes an empty recompile set, treats that as an initial
build, compiles every source, and repopulates the graph from scratch. Gradle drives
this state whenever it cannot supply reliable `InputChanges` — first run, output
directory wiped, task properties changed, `.gradle/` fingerprint cache invalidated,
or a declared output (including the dep file itself) gone missing.

---

## 5. Deriving FQCNs

Gradle reports **files**. `gosuc` speaks **fully-qualified class names**. Everything
in this section is that translation.

### 5.1 `collectFQCNs` — the change sets

One pass over each incremental input:

```java
for (FileChange change : inputChanges.getFileChanges(getStableSources())) { … }   // .gs* sources
for (FileChange change : inputChanges.getFileChanges(getJavaClassesDir())) { … }  // .class files
```

`ChangeType.REMOVED` lands in `removedTypes`; `ADDED` and `MODIFIED` both land in
`changedTypes`. Each addition is logged at `info` (`Gosu type changed: …`,
`Java type removed: …`, …), which is what the functional tests assert against.

A note captured in the code: editing one Java source can produce **several** change
events. Touching a nested class inside `Outer.java` rewrites both `Outer.class` and
`Outer$Inner.class`, each surfaced separately, each mapping to a distinct FQCN
(`com.example.Outer`, `com.example.Outer$Inner`). Both enter `changedTypes`, which is
what lets a nested-Java-type change cascade correctly — pinned by *Nested Java class
change is detected by javaClassesDir tracking* and *Nested Java annotation type change
is detected by javaClassesDir tracking*.

### 5.2 The two extractors

**`extractFQCNFromSourceFile(File)`** — walks `getSourceRoots()`, and for the root
that contains the file, relativises, replaces separators with dots, and strips the
Gosu extension:

```
<root>/com/example/MyClass.gs        → com.example.MyClass
<root>/rules/EventMessage/MyRule.gr  → rules.EventMessage.MyRule
```

The containment test is `Path.startsWith(Path)` on normalised absolute paths, i.e.
path-component-aware. A plain string prefix test would misidentify a sibling
directory such as `.../main2/` as living under `.../main/`.

Extension handling lives in the new **`GosuSourceExtensions`** utility
(`org.gosulang.gradle.tasks`), holding `.gs .gsx .gst .gsp .gr .grs` with
`isGosuSourceFile(String)` and `stripExtension(String)`. It is an intentional mirror
of `gw.lang.reflect.gs.GosuClassTypeLoader.ALL_EXTS` — the authoritative list — because
`gosu-core-api` is not on the Gradle daemon classpath at task-execution time. The
class javadoc records the obligation to keep the two in sync.

**`extractFQCNFromClassFile(File classFile, File javaClassesRoot)`** — relativises
against the Java output root, strips the `.class` suffix **before** replacing
separators, and dots the rest:

```
build/classes/java/main/com/example/Outer$Inner.class → com.example.Outer$Inner
```

Stripping the suffix first means a legal-but-unusual path component named `foo.class`
cannot be mistaken for the file suffix. Because `$` is left alone, the nested-class
FQCN comes out in bytecode shape — exactly the key shape the dep file uses, pinned by
*Nested Java class producer is recorded in dep file using `$` separator*.

### 5.3 `extractLocalJavaTypeFQCNs` — the whole-directory scan

Separately from the change set, every incremental run enumerates **all** `.class`
files under `javaClassesDir` and passes their FQCNs as `-local-java-types`.

This is what lets `gosuc` classify a Java type it meets while extracting dependencies:
a type in this set is a **same-module** Java type, worth an edge in the graph because
a future build can report it as changed; anything else (JRE, external JAR,
cross-subproject) is dropped, because gosuc could never act on such an edge. gosuc
also walks *through* these types in its BFS without adding them to the compile set —
it cannot recompile Java, `compileJava` already did.

An absent directory yields an empty set (a Gosu-only source set). A directory that
exists but is not a directory, or cannot be listed, is an error.

### 5.4 Failure policy: fail loud, never drop

Every mapping failure throws a `GradleException` naming the file and the reason.
There is no "skip what we can't name" path, in either extractor or in the local-types
scan. The rationale is stated in the code and is worth repeating, because "just skip
it" is the tempting wrong answer:

> A change that cannot be mapped to an FQCN cannot be silently dropped. Any *other*
> change in the same build would still drive a narrow incremental compile, and the
> unnamed type's stale output would survive into the next build undetected.

Silent under-reporting is the one failure mode this design cannot tolerate: it turns
a sound over-approximation into an unsound under-approximation. A loud failure costs
a build; a dropped type costs correctness.

---

## 6. Classpath handling

The Java output directory must be **on the classpath handed to `gosuc`** (Gosu code
compiles against the module's Java classes) but **off the classpath Gradle
fingerprints** (it is tracked separately, per-file, as `javaClassesDir`). Getting
that split wrong either loses fine-grained tracking or double-counts the input.

This branch resolves it in one place: **`getClasspath()` subtracts on read.**

```java
@CompileClasspath
public FileCollection getClasspath() {
  FileCollection classpath = super.getClasspath();
  if (classpath != null && getJavaClassesDir() != null && !getJavaClassesDir().isEmpty()) {
    classpath = classpath.minus(getJavaClassesDir());
  }
  return classpath;
}
```

Filtering on *read* covers every route a value can take — the convention mapping, an
explicit `setClasspath` from another plugin or build script, anything else — so no
filtering is needed at any of those sites. 

When `javaClassesDir` is unset the classpath is returned unmodified, preserving
behaviour for consumers that never enable incremental compilation.

`createSpec()` then rebuilds the *execution* classpath:

```java
effectiveClasspath = (orderClasspath == null) ? getClasspath()
                                              : orderClasspath.call(project, compileClasspathConfiguration);
effectiveClasspath = effectiveClasspath.minus(getJavaClassesDir());   // load-bearing only on the orderClasspath path
effectiveClasspath = getJavaClassesDir().plus(effectiveClasspath);    // prepend
```

Two details:

- The **second subtraction is not redundant**. The `orderClasspath` closure resolves
  the `compileClasspath` configuration directly and therefore never passes through
  `getClasspath()`'s filtering. On the normal path it is a no-op.
- **Prepending, not appending.** The project's own Java classes must shadow any
  same-named class arriving from a JAR.

---

## 7. The gosuc contract

`CommandLineGosuCompiler.createArgFile` writes a temporary `@argfile` (one token per
line, UTF-8) in `spec.getTempDir()` and passes `@<path>` to
`gw.lang.gosuc.cli.CommandLineCompiler`. This branch appends the incremental block:

| Flag | Emitted when | Value |
|---|---|---|
| `-incremental` | `gosuOptions.incrementalCompilation` | — |
| `-dependency-file <path>` | `gosuOptions.incrementalCompilation` | `spec.getDependencyFile()`, absolute |
| `-changed-types <fqcns>` | incremental **and not** full-rebuild **and** set non-empty | `File.pathSeparator`-joined FQCNs |
| `-removed-types <fqcns>` | incremental **and not** full-rebuild **and** set non-empty | `File.pathSeparator`-joined FQCNs |
| `-local-java-types <fqcns>` | `gosuOptions.incrementalCompilation` **and** set non-empty | `File.pathSeparator`-joined FQCNs |

Pre-existing flags (`-classpath`, `-d`, `-sourcepath`, `-nowarn`, `-verbose`,
`-maxwarns`, `-maxerrs`, `-checkedArithmetic`) are unchanged.

**The full source list is always written, incremental or not.** The plugin never
narrows the file set handed to the compiler; it only annotates it with change
information. Narrowing is gosuc's job — it maps the recompile set back to source
paths itself. This is why the source-emission loop is identical on both branches of
the incremental check.

The dep-file path is resolved on the **task** (`getDependencyFile()`) and carried
through on the spec, rather than being computed here, precisely so that Gradle's
snapshotter sees it as a declared `@OutputFile`.

---

## 8. Spec plumbing

`GosuCompileSpec` / `DefaultGosuCompileSpec` gain a small state block carrying the
task's findings to the compiler:

| Member | Type | Default | Meaning |
|---|---|---|---|
| `dependencyFile` | `File` | `null` | Resolved dep-file path, set from `GosuCompile.getDependencyFile()` |
| `incremental` | `boolean` | `false` | Gradle supplied usable per-file changes |
| `fullRebuildRequired` | `boolean` | `false` | Incremental requested but `InputChanges` was not incremental |
| `changedTypes` | `Set<String>` | empty | FQCNs added/modified (Gosu **and** Java) |
| `removedTypes` | `Set<String>` | empty | FQCNs deleted (Gosu **and** Java) |
| `localJavaTypes` | `Set<String>` | empty | All FQCNs under `javaClassesDir` |

All collections default to empty sets rather than `null`, so the emitter can test
`isEmpty()` without null guards.

---

## 9. The two-layer change model

A change reaches Gosu sources through exactly one of two channels, and the channel
determines the granularity of the response.

| Origin of the change | Tracked as | Response |
|---|---|---|
| **Same-module Java type** (`build/classes/java/main`) | `@Incremental @CompileClasspath javaClassesDir` → `-changed-types` / `-removed-types` | Selective. Only Gosu types that (transitively) consume it are recompiled. |
| **External JAR / cross-subproject class** | `@CompileClasspath classpath` (not `@Incremental`) | Coarse. The classpath's ABI fingerprint changes, Gradle marks the task non-incremental, the plugin sets `fullRebuildRequired`, and **every** Gosu source is recompiled. |
| **Same-module Java method body only** | ABI hash unchanged under `@CompileClasspath` | Nothing. The task does not even re-run. |

This split is deliberate, not an omission. Tracking external JAR types in the dep
graph would be dead weight: their FQCNs can never appear in `changedTypes`, because
`changedTypes` is sourced from `javaClassesDir` alone, so the BFS could never query
those edges. gosuc's own filter drops them symmetrically. The shape also matches what
Gradle's Java incremental compiler does — per-class dependency tracking within a
subproject, ABI fingerprinting across subproject boundaries.

All three rows are pinned by tests, including the negative one:

- `JarLevelGranularityTest` — a JAR ABI change recompiles *both* the consumer and an
  unrelated Gosu class, and the dep file contains **no** entry for the JAR type. The
  test asserts the over-recompilation on purpose: if someone later makes this
  selective, the assertion fails and forces the change to be a deliberate
  architectural decision rather than an accident.
- `JavaInterfaceGosuImplementationTest` — an interface method addition recompiles only
  the implementing Gosu class, leaving an unrelated one untouched; an
  implementation-only edit to a Java class leaves `compileGosu` `UP_TO_DATE`.

### Comparison with the Gradle Java plugin's driver

| Concern | Gradle `JavaCompile` | This plugin |
|---|---|---|
| Change source | `InputChanges` over `@Incremental` stable sources | Same, plus `@Incremental` over the Java output dir |
| File → class-name mapping | `SourceFileClassNameConverter` (filename heuristic, or compiler-reported mapping on JDK 9+) | Path-relative-to-source-root derivation (§5.2) |
| Who computes the recompile set | In-process (`SelectiveCompiler` + `ClassSetAnalysis`) | The forked `gosuc` process |
| Who narrows the compiler's file list | The plugin | `gosuc` — the plugin always passes all sources |
| Cross-boundary ABI avoidance | `@CompileClasspath` fingerprinting | Same, and additionally applied to the same-module Java output |
| Failure safety | `CompileTransaction` stash/restore | None on either side (§11) |
| Persisted state | `previous-compilation-data.bin`, `@OutputFile`, cacheable | `gosuc-deps-{task}.json`, `@OutputFile @Optional`, cacheable |

---

## 10. Build cache and up-to-date behaviour

Declaring the dep file as an `@Optional @OutputFile` on a `@CacheableTask` produces
four behaviours, each pinned by a test in
`IncrementalCompilationWithDependencyTrackingTest`:

1. **Cache round trip carries the graph.** After `clean`, a `FROM_CACHE` restore
   brings back the dep file byte-identical, and the *next* edit compiles incrementally
   off it — the test's load-bearing signal is a third, unrelated class whose `.class`
   timestamp must stay untouched. Without the restored graph, gosuc would fall back to
   compiling everything and that assertion would fail.
   *(Dependency file is restored from the Gradle build cache for incremental compilation)*
2. **Its absence on a non-incremental build is harmless.** The task still goes
   `UP_TO_DATE` on a no-op re-run and still round-trips through the cache; the restore
   does not resurrect a file that was never stored.
   *(Build cache round trip is unaffected by the absent dep file when incremental is off)*
3. **Deleting it self-heals.** Gradle's fingerprinter notices the missing declared
   output and re-runs the task; `inputChanges.isIncremental()` is then false,
   `fullRebuildRequired` is set, every source goes through gosuc, and the graph is
   regenerated. Net cost: one full recompile.
   *(Deleting the dep file forces gosuc to re-run and regenerate it)*
4. **Per-task isolation.** The name-derived path keeps `compileGosu` and
   `compileTestGosu` graphs apart without configuration.

As with Gradle's Java plugin, caching the graph matters because otherwise every cache
hit would poison the following incremental build.

---

## 11. Known limitations and open items

1. **No transactional safety.** gosuc deletes stale outputs *before* compiling with no
   stash/restore, so a failed compile leaves the output directory missing the deleted
   classes. The plugin does nothing to compensate — there is no equivalent of Gradle's
   `CompileTransaction` on either side of the contract. Recovery is `clean`.
2. **A Java type with no Gosu consumers costs a full recompile.** Correct but badly
   non-minimal, and the cause is not on this side of the contract. The plugin does the
   right thing: an ABI change to a same-module Java type drives the incremental path and
   emits a complete, non-empty `-changed-types`. gosuc seeds its BFS with that FQCN,
   skips it (a `-local-java-types` entry is walked through but never compiled, since
   `compileJava` already built it), finds no consumers, and ends with an **empty
   recompile set** — which it cannot tell apart from "no baseline dependency graph", so
   its empty-set-means-initial-build rule compiles every Gosu source in the module.

   The incentives are backwards: change a Java type that Gosu code uses and the cascade
   is precise (§9); change one nothing uses — the common case in a mixed module — and
   the whole module rebuilds. Pinned by *Java type with no Gosu consumers recompiles
   every Gosu source*, which also asserts that the plugin neither requested a full
   rebuild nor produced an empty change set, so the failure stays attributed to gosuc.

   Fixing it needs a gosuc change: discriminate on whether `-changed-types` was supplied
   at all — present with an empty cascade means *nothing to do*, absent means *initial
   build* — rather than on whether the recompile set came out empty. The plugin's half
   would be to always emit `-changed-types`/`-removed-types` in incremental mode, even
   when a set is empty, instead of suppressing them, so the signal is unambiguous.
3. **`getJavaClassesDir().getSingleFile()`** assumes the collection holds exactly one
   directory. That holds for the `SourceSet`-derived value wired by `GosuBasePlugin`,
   but the setter is public and a caller passing a multi-directory collection would get
   an exception from `getSingleFile()`.
4. **The local-Java-type scan is a full directory walk on every incremental run**,
   independent of how little changed. It is I/O-bound and proportional to the module's
   Java class count, not to the change size.
5. **`orderClasspath` remains a configuration-cache hazard.** The closure is invoked at
   execution time with a live `Project` reference (`getProject()`), flagged by an
   explicit `TODO` in `createSpec()`. It is only reached by builds that set the closure.
6. **`getDependencyFile()` returns a plain `File`**, not a `RegularFileProperty`. It
   uses the injected `ProjectLayout` rather than `getProject()`, but the eager
   `.get().getAsFile()` resolution at fingerprint time is not the modern lazy shape;
   migrating to `objects.fileProperty()` with a `Provider`-based `convention(...)` is
   the natural follow-up.
7. **`GosuSourceExtensions` duplicates `GosuClassTypeLoader.ALL_EXTS`** and is kept in
   sync by hand. A new Gosu extension added upstream and not mirrored here would make
   changed sources with that extension invisible to `collectFQCNs` — they simply would
   not match `isGosuSourceFile`, and no exception would fire.
8. **Over-recompilation is inherited from gosuc**, which keeps a single consumer bucket
   with no accessible/private split and no inlineable-constant ABI tracking, so every
   cascade is the full transitive closure. Correctness-neutral, wasteful at scale.
9. **Functional tests rely on `Thread.sleep(200)`** between builds to get observable
   `lastModified()` differences, and assert on `-i` log lines. Both are timing- and
   format-sensitive.
10. **Minor:** the `instanceof GosuCompile` check in `GosuBasePlugin.configureGosuCompile`
    is redundant on a `TaskProvider<GosuCompile>`, and `GosuCompileOptions` carries an
    unused `org.gradle.api.file.FileCollection` import.
11. **Empty-source skipping now rests on `stableSources` alone** (§3.1). The
    behaviour is unchanged — `@SkipWhenEmpty` moved from an inherited annotation on
    `source` to a declared one on `stableSources` — but the two existing tests that
    pin it (`ExclusionFilterTest`, `SourceSetsModificationTest`, both asserting
    `NO_SOURCE` on `compileTestGosu`) are now the only guard, and neither exercises
    the empty-directory case that `@IgnoreEmptyDirectories` covers.

---
