# Compilation Approaches for gradle-gosu-plugin: Comparison

**Date:** January 2026
**Status:** Design Discussion

---

## Context

The gradle-gosu-plugin currently uses a two-phase compilation approach where Java sources are compiled first (via Gradle's `compileJava` task), then Gosu sources are compiled second (via the `compileGosu` task). This creates an over-compilation problem: when ANY Java class ABI changes, Gradle's `@CompileClasspath` annotation triggers, and ALL Gosu files are recompiled conservatively.

**The Problem:**
- Java generates many types (e.g., entity classes, DTOs)
- Change one Java class → ALL Gosu files recompile
- In large Guidewire codebases, this causes significant build time issues

**Example:**
- 1000 Gosu files depend on 100 different Java classes
- Change 1 Java class used by only 10 Gosu files
- Current behavior: All 1000 Gosu files recompile
- Desired behavior: Only 10 dependent Gosu files recompile

---

## Three Possible Solutions

### Approach A: Two-Phase with Fine-Grained Dependency Tracking

**Description:** Keep the two-phase compilation but add dependency tracking to know which Gosu files depend on which Java classes.

**Architecture:**
```
compileJava (Gradle JavaCompile)
    → produces .class files
    → Gradle detects ABI changes via @CompileClasspath
    ↓
compileGosu (gosuc with dependency tracking)
    → receives list of changed Java classes
    → tracks which Gosu files depend on which Java classes
    → only recompiles affected Gosu files
```

**Key Components:**

1. **Gradle Plugin Changes:**
   - Add `@Incremental` to `GosuCompile.getClasspath()`
   - Use `inputChanges.getFileChanges(getClasspath())` to get ABI-changed Java classes
   - Pass list to gosuc via `-changed-java-classes` CLI flag

2. **gosuc Changes:**
   - Remove Java type skip in dependency tracking (GosuCompiler.java:696-700)
   - Track Java class dependencies during compilation
   - Store in dependency file (JSON format, version 2.0)
   - Calculate recompilation set based on changed Java classes

**Pros:**
- ✅ Leverages Gradle's existing ABI tracking (no duplicate work)
- ✅ Follows proven ij-gosu pattern (used since 2014)
- ✅ Minimal architectural changes
- ✅ Incremental improvement over current behavior
- ✅ gosuc already has 80% of infrastructure in place

**Cons:**
- ❌ Maintains two-phase compilation (Java first, then Gosu)
- ❌ Requires changes to both gradle-gosu-plugin AND gosuc
- ❌ Dependency file management adds complexity
- ❌ Must handle dependency file versioning and migration
- ❌ Two separate compilers = two sets of configuration

**Complexity:** Medium
**Risk:** Low (proven approach)
**Coordination:** Requires gosu-lang team involvement

---

### Approach B: Single-Pass Joint Compilation via gosuc

**Description:** Have gosuc compile both Java and Gosu sources in a single compilation pass.

**Architecture:**
```
compileGosu (gosuc with BOTH .java and .gs sources)
    → compiles Java sources via embedded javac
    → compiles Gosu sources
    → single compiler sees all types together
    → automatic dependency tracking
```

**Key Components:**

1. **Gradle Plugin Changes:**
   - Configure `compileGosu` task to include Java sources
   - Disable or skip `compileJava` task
   - Pass both .java and .gs files to gosuc

2. **gosuc Capabilities (Already Exists!):**
   - GosuCompiler.compileJavaSources() (line 325)
   - Uses IJavaParser from Manifold
   - Produces .class files for both Java and Gosu

**Pros:**
- ✅ Eliminates two-phase problem entirely
- ✅ Single compiler sees all sources together
- ✅ Natural dependency tracking (types resolved together)
- ✅ gosuc already has Java compilation capability
- ✅ Simpler task dependency graph

**Cons:**
- ❌ Loses Gradle's sophisticated Java compilation features
- ❌ gosuc's Java compiler may not be as robust as Gradle's
- ❌ Configuration complexity (Java compiler options, annotation processors)
- ❌ Diverges from standard Gradle conventions
- ❌ May break existing Java-only tooling expectations
- ❌ Still needs dependency tracking for incrementality

**Complexity:** Medium-High
**Risk:** Medium (unconventional approach)
**Coordination:** Mostly gradle-gosu-plugin changes

---

### Approach C: Single-Pass Joint Compilation via Manifold + javac

**Description:** Use Manifold's javac plugin to make javac understand Gosu sources. Single compilation pass with javac as the driver.

**Architecture:**
```
compileJava (Gradle JavaCompile + Manifold plugin)
    → javac encounters .java files → compiles normally
    → javac encounters .gs files → Manifold intercepts
    → Manifold generates Java stub for type-checking
    → javac validates types against stubs
    → javac ready to write .class → ManClassWriter intercepts
    → Calls Gosu's compile() method → produces real bytecode
    → All compilation happens in single javac invocation
```

**Key Components:**

1. **Gradle Plugin Changes:**
   - Configure Gradle's `compileJava` to include .gs sources
   - Add Manifold JAR to annotation processor path (where javac discovers plugins)
   - Add `-Xplugin:Manifold` compiler argument to activate the plugin
   - Skip or remove `compileGosu` task

2. **Gosu-Manifold Integration (Already Exists!):**
   - GosuTypeManifold implements ITypeManifold
   - Generates stubs for type-checking
   - `isSelfCompile() = true` → Gosu compiles its own bytecode
   - ManClassWriter intercepts → calls Gosu compiler

3. **For Incremental Compilation:**
   - Implement GradleChangedResourceFiles adapter
   - Hook into Gradle's InputChanges API
   - Provide changed .gs files to Manifold
   - Manifold filters: changed files recompile, others use existing .class

**Pros:**
- ✅ Single compilation pass (javac is the driver)
- ✅ Leverages Gradle's full Java compilation infrastructure
- ✅ Manifold already integrated with Gosu
- ✅ No dependency file needed (file system is the state)
- ✅ Incremental infrastructure already exists in Manifold
- ✅ Standard Gradle JavaCompile task = better IDE integration
- ✅ Proven approach (Manifold used in production)
- ✅ Simpler than custom dependency tracking

**Cons:**
- ❌ Requires understanding Manifold's architecture
- ❌ Different execution model (javac-driven vs gosuc-driven)
- ❌ Need to adapt Manifold's incremental tracking to Gradle
- ❌ Debugging may be more complex (stub → actual compilation)
- ❌ Dependency on Manifold plugin continued maintenance
- ✅ ~~**RISK: May break Gradle's incremental Java compilation**~~ - **VALIDATED: Manifold does NOT break incremental compilation** ([Phase 0 test results](../../test-projects/manifold-incremental-test/PHASE0_TEST_REPORT.md))

**Complexity:** Low-Medium
**Risk:** Low (Phase 0 validated incremental compilation compatibility)
**Coordination:** Mostly gradle-gosu-plugin changes

---

## Comparison Matrix

| Criteria | Approach A: Two-Phase | Approach B: gosuc Joint | Approach C: Manifold |
|----------|----------------------|------------------------|---------------------|
| **Architectural Simplicity** | Current + tracking | Single compiler | Single compiler |
| **Implementation Effort** | Medium | Medium-High | Low-Medium |
| **Changes Required** | Both projects | Mostly plugin | Mostly plugin |
| **Gradle Integration** | Good | Custom | Excellent |
| **Java Feature Support** | Full (Gradle's) | Limited (gosuc's) | Full (Gradle's) |
| **Incremental Complexity** | Dependency file | Dependency file | File system state |
| **IDE Support** | Good | Custom | Excellent |
| **Risk Level** | Low | Medium | Low |
| **Proven in Production** | Yes (ij-gosu) | No | Yes (Manifold) |

---

## Recommendation

**Approach C (Manifold + javac)** appears to be the strongest candidate:

1. **Simpler implementation** - No dependency file management
2. **Better Gradle integration** - Uses standard JavaCompile task
3. **Proven technology** - Manifold is production-tested
4. **Lower coordination** - Mostly plugin changes, no gosuc modifications
5. **Superior incrementality** - File system as state (no custom tracking)

**Implementation Plan:**
1. **Phase 0:** ✅ **COMPLETE** (Jan 7, 2026) - Validated Manifold + Gradle incremental compilation compatibility
   - Created toy project to test if Manifold breaks Gradle's incremental Java compilation
   - **Result:** ✅ SUCCESS - Manifold does NOT break incremental compilation
   - See [test-projects/manifold-incremental-test/PHASE0_TEST_REPORT.md](../../test-projects/manifold-incremental-test/PHASE0_TEST_REPORT.md) for details
   - **Decision:** GREEN LIGHT for Phase 1
2. **Phase 1:** Validate correctness - Ensure Manifold produces identical outputs
3. **Phase 2:** Add incrementality - Implement GradleChangedResourceFiles adapter
4. **Phase 3:** Optimize and tune - Performance testing and refinement

**RISK MITIGATION:** Phase 0 validation successful - original risk mitigated. Proceed with confidence.

---

## Next Steps

1. ✅ **COMPLETE:** Phase 0 validation
   - Created `gradle-gosu-plugin/test-projects/manifold-incremental-test/`
   - Tested Manifold's impact on Gradle incremental compilation
   - Documented findings - **Manifold is compatible!**
2. **NEXT:** Proceed with Phase 1 implementation
3. Prototype basic Manifold integration (no Gosu incrementality yet)
4. Validate compilation outputs match current two-phase approach
5. Add incremental compilation support for Gosu sources (Phase 2)
6. Performance testing and benchmarking
7. Socialize approach with team before full implementation

---

## Open Questions

1. **Manifold maturity:** Is Manifold's Gosu integration production-ready for Guidewire scale?
2. **Configuration migration:** How to migrate existing build configurations?
3. **Edge cases:** Annotation processors, multi-module builds, custom source sets?
4. **Performance:** Does single-pass javac+Manifold match gosuc performance?
5. **Debugging:** How does debugging work with stub generation?

---

## References

- [Manifold GitHub Repository](https://github.com/manifold-systems/manifold)
- [Gradle Incremental Build Documentation](https://docs.gradle.org/current/userguide/incremental_build.html)
- [Gradle JavaCompile Task](https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/compile/JavaCompile.html)
- Gosu's GosuTypeManifold implementation
- ij-gosu dependency tracking implementation
