# Plan

## 6. `cnavLayerCheck` — architecture conformance (High value, ambitious)

Allow declaring layer rules and validate them against the actual call graph. Like ArchUnit but without writing test code. Catches architecture violations early and is complementary to the DSM.

```kotlin
codeNavigator {
    layers {
        "domain" dependsOn nothing
        "services" dependsOn "domain"
        "ktor.routes" dependsOn "services", "domain"
    }
}
```

Also support simpler forbidden-dependency rules for projects that don't want full layer declarations:

```kotlin
codeNavigator {
    rules {
        "services" mustNotDependOn "ra"
        "domain" mustNotDependOn "ktor"
    }
}
```

Output: list of violations with the specific class-level edges that break the rule, so the user knows exactly what to fix.

## 9. Write JSON output to file instead of stdout (Medium value)

When `-Pformat=json` is used, write the JSON to a file (e.g., under `build/cnav/`) and print the file path to stdout. Agents and scripts can then read the file directly instead of parsing Gradle's stdout, which mixes task output with Gradle lifecycle noise (e.g., `> Task :cnavCallers`, configuration cache messages). This makes the JSON output reliable regardless of Gradle's verbosity settings.

## 10. Maven plugin support (High value)

Create a Maven plugin equivalent in the same repo. The core bytecode analysis logic (scanning, call graph building, formatting) is build-tool-agnostic — it operates on `.class` file directories. Factor the core into a shared module and wire it into both a Gradle plugin and a Maven plugin (Mojo). This would make code-navigator available to the large Maven user base without duplicating the analysis code.

## 11. Git log infrastructure — foundation for behavioral analysis (High value)

Inspired by Code Maat / CodeScene. All git-history-based analyses share a common parsing layer:

- `GitLogRunner` — executes `git log --all --numstat --date=short --pretty=format:'--%h--%ad--%aN' --no-renames --after=<date>` via `ProcessBuilder`. Uses the Code Maat `git2` format for tolerant, fast parsing.
- `GitLogParser` — parses raw git log output into `List<GitCommit>` data class: hash, date, author, list of `FileChange(added: Int, deleted: Int, path: String)`.
- Shared parameter: `-Pafter=YYYY-MM-DD` to limit the temporal window (default: 1 year). Matches Code Maat's recommendation to avoid old data confounding analysis.
- No new dependencies — just `ProcessBuilder` + string parsing.
- These tasks do NOT depend on `classes` — they read git history, not bytecode, so they work even before compilation.

Architecture follows the existing three-layer pattern:
1. **Parsing**: `GitLogParser` (shared) produces `List<GitCommit>`
2. **Resolution**: One builder per analysis produces its result data structure
3. **Formatting**: Text/JSON/LLM formatters per analysis, using existing `OutputFormat` and `OutputWrapper`

Each builder is independently testable with synthetic `GitCommit` lists (no git repo needed in tests), mirroring how existing tests use synthetic `CallGraph` instances.

## 12. `cnavHotspots` — change frequency analysis (High value)

Inspired by Code Maat's `revisions` analysis. Ranks files by how often they change. The most-changed files are where development effort concentrates — if they also have structural problems (visible via `cnavDeps`, `cnavCallers`), they're priority refactoring targets.

- `HotspotBuilder.build(commits) -> List<Hotspot(file, revisions, totalChurn)>`
- Parameters: `-Pafter=YYYY-MM-DD`, `-Pmin-revs=N` (default 1), `-Ptop=N` (default 50)
- Text table (File | Revisions | Churn), JSON, LLM formatters
- Sorted by revision count descending

## 13. `cnavCoupling` — change coupling / logical coupling (High value)

Inspired by Code Maat's `coupling` analysis. Finds files that change together in the same commits — implicit dependencies invisible in call graphs. Complements structural `cnavDeps` with behavioral coupling data.

- `ChangeCouplingBuilder.build(commits, minSharedRevs, minCoupling, maxChangesetSize) -> List<CoupledPair(entity, coupled, degree, sharedRevs, avgRevs)>`
- Degree = (shared commits / avg individual commits) × 100
- Large changeset filtering (default: skip commits touching >30 files) — these are usually automated refactorings/renames and create misleading coupling signals. This is a Code Maat best practice.
- Parameters: `-Pafter`, `-Pmin-shared-revs=N` (default 5), `-Pmin-coupling=N` (default 30%), `-Pmax-changeset-size=N` (default 30), `-Ppattern=<regex>` to filter to specific files
- Text table (Entity | Coupled | Degree% | Shared Revs), JSON, LLM formatters

## 14. `cnavAge` — code age analysis (Medium value)

Inspired by Code Maat's `age` analysis. Measures time since last modification per file. Stable old code is good; frequently-changing old code with structural problems is a hotspot. One way to measure the stability of a software architecture.

- `CodeAgeBuilder.build(commits) -> List<FileAge(file, ageMonths, lastChangeDate)>`
- Parameters: `-Pafter` (for filtering which files to consider), `-Ptop=N`
- Sorted by age descending (oldest/most stable first)
- Text table (File | Age (months) | Last Changed), JSON, LLM formatters

## 15. `cnavAuthors` — authors per module (Medium value)

Inspired by Code Maat's `authors` analysis. Number of distinct contributors per file — the more developers working on a module, the larger the communication challenges. High author counts correlate with defects and quality issues.

- `AuthorAnalysisBuilder.build(commits) -> List<ModuleAuthors(file, authors: Int, revisions: Int)>`
- Parameters: `-Pafter`, `-Pmin-revs=N`, `-Ptop=N`
- Sorted by author count descending
- Text table (File | Authors | Revisions), JSON, LLM formatters

## 16. `cnavChurn` — code churn analysis (Medium value)

Inspired by Code Maat's `abs-churn` and `entity-churn` analyses. Pre-release churn of a module is a good predictor of post-release defects. Measures lines added/deleted per file.

- `ChurnBuilder.build(commits) -> List<FileChurn(file, added: Int, deleted: Int, commits: Int)>`
- Parameters: `-Pafter`, `-Ptop=N`, optional `-Pby-date=true` for daily aggregation
- Sorted by total churn (added + deleted) descending
- Text table (File | Added | Deleted | Net | Commits), JSON, LLM formatters

## 38. Full classpath scanning option for searches and identification (High value, medium effort)

Most searches and identifications should have an option to include the full classpath, not just the project code. Currently `cnavListClasses`, `cnavFindClass`, `cnavFindSymbol`, `cnavClass`, `cnavCallers`, `cnavCallees`, `cnavInterfaces`, and `cnavUsages` only scan the project's compiled output directories. But when checking what is available on the classpath — e.g., verifying a library class's method signatures, finding all implementations of a framework interface, or understanding what types are available — scanning only project code is insufficient.

- **Question**: "What does this library class look like? What methods does it have? Who on my full classpath implements this interface?"
- **Parameter**: `-Pclasspath=true` — scan the full runtime classpath (project classes + all dependency JARs) instead of only project output directories
- **Needs**: Gradle's `configurations.runtimeClasspath.resolve()` / Maven's `project.runtimeClasspathElements` to enumerate all JAR files and class directories
- **Applies to**: `cnavListClasses`, `cnavFindClass`, `cnavFindSymbol`, `cnavClass`, `cnavInterfaces`, `cnavUsages`, potentially `cnavCallers`/`cnavCallees`
- **Considerations**:
  - Classpath scanning is significantly slower (thousands of classes in typical dependency trees). Consider caching scanned JARs by checksum.
  - Output can be very large. May want to combine with existing `-Ppattern` / `-Powner` filters to narrow scope.
  - `cnavFindSymbol -Pclasspath=true -Ppattern="LocalDate"` would show all `LocalDate` symbols across every JAR — useful for finding the right FQN.
  - `cnavClass -Pclasspath=true -Ppattern="kotlinx.datetime.LocalDate"` would show the full signature of a library class without needing to look up docs.
- **Why high value**: AI agents frequently need to check library API signatures to write correct code. Currently they must rely on training data or web lookups. Classpath scanning gives ground-truth answers from the actual dependency versions in the project.

## 39. `cnavTypeChanges` — detect API signature changes between compilations (High value, medium effort)

From migration feedback: after changing a dependency version (e.g., `kotlinx-datetime` → `kotlin.time`), cascading type changes ripple through the codebase. A `xTimestamp()` column returning `Column<kotlinx.datetime.Instant>` silently becomes `Column<kotlin.time.Instant>`, breaking every domain model, repository, and service that consumed it. A tool that diffs "before" and "after" API surfaces would map the blast radius instantly.

- **Question**: "After recompiling with an updated dependency, which method/field signatures changed and what project code is affected?"
- **Needs**: Bytecode from two compilation passes (before and after the dependency change)
- **Approach**: Scan class directories twice — once from a cached/saved baseline, once from the current build — and diff the extracted signatures. For each changed signature, use `cnavCallers`/`cnavUsages` to find all affected call sites.
- **Builder**: `TypeChangeDetector.diff(baselineClassDir, currentClassDir) -> List<SignatureChange(className, memberName, oldSignature, newSignature, kind: METHOD|FIELD|SUPERTYPE)>`
- **Parameters**:
  - `-Pbaseline=<path>` — path to baseline class directory (or auto-save on first run)
  - `-Paffected=true` — also list all project call sites affected by each change (combines with `cnavUsages`/`cnavCallers`)
- **Real-world example**: `LocalDate.month` changed return type from `Int` to `kotlinx.datetime.Month` — this caused 48 test failures. Bytecode analysis would have flagged the signature change and listed every caller expecting `Int`.
- **Why high value**: Dependency upgrades are one of the most common sources of subtle breakage. The blast radius is currently discovered only at test time (or worse, runtime). Bytecode diffing gives you the full migration checklist before you even run tests.

## 40. `cnavFindSymbol` should support external type references (Medium value, low effort)

From migration feedback: running `cnavFindSymbol -Ppattern=Instant` only finds project-defined symbols (methods/fields named "Instant"), not import sites or references to external types like `kotlinx.datetime.Instant`. For migrations, you need to search for *references to* external types, not just *definitions of* symbols.

This is partially addressed by item 38 (full classpath scanning) which would let `cnavFindSymbol -Pclasspath=true` find symbols defined in external JARs. But the migration use case is different — you want to find *project code that references* an external type, not the external type's own definition. `cnavUsages` (item 37) addresses this for method calls and field accesses, but `cnavFindSymbol` could also be enhanced to show project methods/fields whose *signatures* reference a given external type (parameters, return types, field types).

- **Question**: "Which project methods have `Instant` in their signature?"
- **Approach**: When scanning project bytecode for symbols, also extract types from method descriptors and field types. Allow `-Ppattern` to match against referenced types, not just symbol names.
- **Overlap**: This overlaps with `cnavUsages -Ptype=<class>` which already finds type references in signatures. Consider whether this should just be better documentation pointing users to `cnavUsages` for this use case, rather than duplicating functionality in `cnavFindSymbol`.

## 43. DSM "what-if" mode and cycle fix suggestions (High value, medium effort)

From real-world feedback: the DSM tells you which cycles exist, but you still have to reason about the fix yourself.

### Cycle fix suggestions (lower effort, do first)

When `-Pcycles=true`, also show which specific class-level edges would need to move to break the cycle, and suggest which direction the dependency should flow. This turns cycle detection from "here's a problem" into "here's what to do about it."

### What-if simulation (higher effort)

A "what-if" mode would let you say "if I move class X to package Y, would the cycle break?" and get an answer without actually making the change.

- **Question**: "Would moving `locateResourceFile` from `root` to `util` break the cycle between `root` and `web.plugins`?"
- **Parameter**: `-Pwhat-if=<class>:<target-package>` — simulate moving a class to a different package and re-evaluate cycles
- **Approach**: Take the existing dependency graph, rewrite the package assignment for the specified class, and re-run cycle detection. Report which cycles would be resolved and which would remain.
- **Builder**: `WhatIfSimulator.simulate(dsmResult, move: Pair<String, String>) -> WhatIfResult(resolvedCycles, remainingCycles, newCycles)`
- **Why**: Turns the DSM from a diagnostic tool into a planning tool. The user reported "you still have to reason about the fix yourself" — this automates that reasoning.

## 46. `cnavTestHealth` — verify all test methods actually ran (High value, medium effort)

From user feedback: a project had 19 silently skipped tests because test methods had non-`Unit` return types. The test framework silently ignored them — the suite looked green while tests weren't running.

Rather than trying to heuristically detect "suspicious" signatures (which is fragile and framework-specific), the robust approach is **count-and-verify**: count `@Test`-annotated methods from bytecode, compare against JUnit XML results from the actual test run, and flag the delta.

### Approach: bytecode expected count vs. JUnit XML actual count

1. **Bytecode scan** (pre-existing infrastructure): Use ASM `visitAnnotation()` on test class directories to find all methods annotated with `@Test` (JUnit 4: `org.junit.Test`, JUnit 5: `org.junit.jupiter.api.Test`, Kotlin Test: `kotlin.test.Test`). This is the "expected" set.
2. **JUnit XML scan** (new): Parse the test result XML files written after `test` runs. Both Gradle (`build/test-results/test/TEST-*.xml`) and Maven (`target/surefire-reports/TEST-*.xml`) use the same JUnit XML format. Each `<testcase>` element represents a method that the framework actually attempted. This is the "actual" set.
3. **Diff**: For each test class, report methods present in bytecode but absent from XML results. These are the silently skipped tests — regardless of *why* they were skipped.

### Why this is more robust than signature heuristics

- Catches **any** reason for silent skipping: wrong return type, wrong parameters, framework version incompatibilities, visibility issues, misconfigured test engines, etc.
- No need to maintain a list of "known bad patterns" per framework version
- Works for JUnit 4, JUnit 5, Kotlin Test, and any framework that writes JUnit XML results
- Zero false positives: if a method ran, it appears in the XML

### Task design

- **Question**: "Did all `@Test`-annotated methods actually execute?"
- **Needs**: Bytecode (test class dirs) + JUnit XML results (post-test-run)
- **Lifecycle**: `dependsOn("test")` in Gradle / `@Execute(phase = LifecyclePhase.TEST)` in Maven — runs *after* tests complete
- **Builder**: `TestHealthAnalyzer.analyze(testClassDirs, junitXmlDir) -> TestHealthReport(expected: Int, actual: Int, missing: List<MissingTest(className, methodName)>, extras: List<...>)`
- **Parameters**:
  - `-Pfilter=<regex>` to scope to specific test classes
  - `-Presults-dir=<path>` to override the JUnit XML location (defaults to convention)
- **Output**:
  ```
  Test Health: 142 expected, 123 ran, 19 missing

  Missing tests (found in bytecode but not in test results):
    com.example.UserServiceTest.shouldValidateEmail
    com.example.UserServiceTest.shouldHashPassword
    com.example.OrderTest.shouldCalculateTotal
    ...
  ```
- **Additional checks** (secondary, from bytecode only — no XML needed):
  - Test methods missing `@Test` annotation but named `test*` (possible forgotten annotation)
  - Test classes with no `@Test` methods (empty test class)
  - `@Disabled`/`@Ignore` inventory for awareness (expected to be absent from XML, but worth reporting)

### Implementation notes

- The `ClassDetailExtractor` already extracts method signatures and return types. It needs extension with `visitAnnotation()` to detect test annotations.
- JUnit XML parsing is straightforward — the `<testsuite>` element has `tests`/`skipped`/`failures`/`errors` counts, and each `<testcase>` has `name` + `classname`.
- Test source set directories are already accessed by `FindInterfaceImplsTask` when `includetest=true` — same pattern applies.
- Both Gradle and Maven write the same XML format, so one parser handles both.

## 49. `cnavWhyDepends` — dependency edge explanation (High value, medium effort)

From user feedback: the DSM tells you package A depends on package B, but not *why*. To break a cycle you need to know the specific fields, method parameters, return types, and local variable types that create the dependency. Currently this requires manual grepping.

- **Question**: "Why does class/package A depend on class/package B? What are the specific code-level references?"
- **Needs**: Bytecode only (reuses existing `CallGraph` + `ClassDetailExtractor`)
- **Builder**: `DependencyExplainer.explain(callGraph, from, to) -> List<DependencyEdge(sourceClass, targetClass, kind: FIELD|PARAMETER|RETURN_TYPE|LOCAL_VAR|METHOD_CALL, detail: String)>`
- **Parameters**: `-Pfrom=<class-or-package>` (required), `-Pto=<class-or-package>` (required), `-Pprojectonly=true`
- **Output example**:
  ```
  Why services.interfaces.RAClient depends on ra.SignatureContext:

    FIELD        RAClient.signatureCtx: SignatureContext
    PARAMETER    RAClient.sign(context: SignatureContext): SignedRequest
    RETURN_TYPE  RAClient.getContext(): SignatureContext
    METHOD_CALL  RAClient.init() -> SignatureContext.<init>()
  ```
- **Why useful**: This is the missing link between "the DSM says there's a dependency" and "here's what to move/extract to break it." Eliminates manual code searches during cycle-breaking.

## 51. `cnavMetrics` / `cnavReport` — summary dashboard and full analysis (Medium value, low effort)

Two related needs from user feedback:

### cnavMetrics — quick health snapshot (lower effort, do first)

A single task that combines key metrics: total classes, package count, average fan-in/fan-out, cycle count, dead code count, hotspot top-5. Quick project health snapshot in one command. `./gradlew cnavMetrics -Pllm=true`

### cnavReport — full combined report (higher effort)

Run all analysis tasks (both bytecode and git history) and produce a consolidated report. Instead of running `cnavHotspots`, `cnavCoupling`, `cnavAge`, `cnavAuthors`, `cnavChurn`, `cnavRank`, `cnavDead`, and `cnavDsm` individually, `cnavReport` runs them all and outputs a consolidated summary.

- **Question**: "Give me a full health overview of this codebase."
- **Needs**: Both bytecode and git history
- **Parameters**: Inherits parameters from constituent tasks (e.g., `-Pafter`, `-Ptop`, `-Proot-package`). `-Pformat=json` produces a single JSON object with sections per analysis.
- **Output**: Sections for each analysis, clearly delimited. TEXT format uses headers; JSON uses a top-level object with keys like `hotspots`, `coupling`, `rank`, `dead`, `dsm`.
- **Why useful**: Agents and humans often want the full picture. Running 8 separate tasks is tedious and each invocation has Gradle/Maven startup overhead. A single task is faster (shared caching, one compilation) and produces a coherent snapshot.

## 53. `cnavDead` — entry point awareness (Medium value, medium effort)

From user feedback: dead code detection has no built-in concept of "entry points" beyond the `-Pexclude` regex. Common patterns like Ktor route handlers, `@Scheduled` methods, or serialization-invoked constructors show up as false positives.

- **Suggestion**: Support annotation-based exclusion (`-Pexclude-annotated=Serializable,Route`) so users don't need to hand-craft regexes
- **Alternative**: Support named presets (`-Pentry-points=ktor`) for common frameworks
- **Needs**: `visitAnnotation()` in the bytecode scanner to detect annotations on classes/methods. The `ClassDetailExtractor` already visits methods but doesn't extract annotations.
- **Implementation note**: Annotation-based exclusion is more general and framework-agnostic than named presets. Start with annotation exclusion.

## 54. `cnavDead` — severity/confidence scoring (Medium value, medium effort)

From user feedback: all dead code results are presented equally. A method called nowhere is more likely dead than one only called by reflection.

- **Suggestion**: Add a confidence indicator:
  - **high** — truly unreferenced (no callers in the entire call graph)
  - **medium** — only referenced in test code (not called from production code)
  - **low** — potentially reflection-invoked (class has framework annotations, or method name matches common reflection patterns)
- **Needs**: Test source set scanning (already supported via `includetest`), annotation detection (same as #53)
- **Implementation**: The `DeadCodeFinder` already has the call graph — checking whether callers are in test vs. production packages is straightforward once test class directories are available.

## 55. `cnavChangedSince` — impact analysis for a branch/commit (Very high value, medium effort)

From user feedback: the most common agent question is "what could this PR break?" Given a git ref (branch, commit, or `HEAD~5`), show which classes changed and then automatically run `cnavCallers` on all changed methods to show the blast radius.

```bash
./gradlew cnavChangedSince -Pref=main
```

- **Question**: "What does this PR affect? What could break?"
- **Needs**: Git history (to find changed files) + bytecode (to map files to classes and run caller analysis)
- **Approach**:
  1. `git diff --name-only <ref>..HEAD` to find changed source files
  2. Map source files to class names via existing `ClassScanner` source file metadata
  3. For each changed class, find changed methods (diff class signatures against baseline or use git hunk line numbers + bytecode line number tables)
  4. Run `cnavCallers` on each changed method to find the blast radius
- **Output**: Changed classes with their affected callers, grouped by change type (added/modified/removed)
- **Why high value**: Directly answers the most common code review question. Combines git + bytecode analysis in a way that neither can do alone.

## 56. `cnavContext` — smart context gathering for AI agents (High value, medium effort)

From user feedback: AI agents typically need 4-5 sequential tool calls to understand a class — class signature, callers, callees, interface implementations, source path. Each `./gradlew` invocation has ~0.5s startup overhead.

Given a class or method, automatically gather "everything an agent needs": the class signature, its callers (depth 2), its callees (depth 2), interface implementations, and the source file path. One command instead of 4-5 sequential calls.

```bash
./gradlew cnavContext -Ppattern=ResetPasswordService -Pformat=json
```

- **Builder**: Orchestrates existing `ClassDetailScanner`, `CallTreeBuilder` (callers + callees), and `InterfaceRegistry` into a single result
- **Parameters**: `-Ppattern=<class>` (required), `-Pmaxdepth=N` (default 2), `-Pformat=json|text|llm`
- **Output**: Combined JSON/text with sections for signature, callers, callees, implementations
- **Why high value**: Reduces agent round-trips from 4-5 to 1, saving significant wall-clock time

## 57. `cnavTypeHierarchy` — inheritance tree traversal (Medium value, low effort)

From user feedback: `cnavInterfaces` finds implementors (downward), but there's no upward traversal. `cnavClass` shows direct supertypes but doesn't recurse.

```bash
./gradlew cnavTypeHierarchy -Ppattern=RAClientImpl
```

- **Question**: "What is the full type hierarchy for this class?"
- **Needs**: Bytecode only (superclass and interface info already extracted by `ClassInfoExtractor`)
- **Builder**: Walk supertypes recursively using existing class metadata. For each supertype, show its own supertypes and interfaces.
- **Output**: Tree showing supertypes upward and implementors downward (combines with `InterfaceRegistry`)
- **Why useful**: Complex inheritance chains (common in frameworks) are hard to understand from `cnavClass` alone

## 58. `cnavUnused` — unused build dependencies (Medium value, medium effort)

From user feedback: analyze which declared Gradle/Maven dependencies have zero references in bytecode. Different from dead code — this finds entire libraries that could be removed from `build.gradle.kts` or `pom.xml`.

- **Question**: "Which declared dependencies are not actually used?"
- **Needs**: Full classpath resolution (to enumerate declared dependencies) + bytecode (to check which external types are referenced)
- **Approach**: For each declared dependency JAR, extract the package list. Then scan project bytecode for references to those packages. Dependencies with zero references are candidates for removal.
- **Caveats**: Runtime-only dependencies (JDBC drivers, logging backends, annotation processors) will show as "unused" even though they're needed. Need an exclusion mechanism.
- **Overlap**: Related to #38 (full classpath scanning) — reuses the classpath enumeration infrastructure

## 59. `cnavDiff` — structural diff between two builds (Medium value, medium effort)

From user feedback: compare two compiled states (e.g., before/after a refactoring) and show added/removed/changed classes, methods, and dependency edges. Useful for verifying that a refactoring was purely structural.

- **Overlap**: Related to #39 (`cnavTypeChanges`) which focuses on signature changes from dependency upgrades. `cnavDiff` is broader — it covers all structural changes including class additions/removals, method additions/removals, and dependency edge changes.
- **Approach**: Save a baseline class scan (class index + call graph) and diff against current. Report changes categorized by type.

## 60. Composite queries — reduce agent round-trip overhead (Medium value, medium effort)

From user feedback: each `./gradlew` invocation has ~0.5s startup overhead that adds up in agent workflows. Allow chaining tasks in a single Gradle invocation.

- **Approach options**:
  - A `cnavBatch` task that accepts multiple sub-task specs as a JSON parameter
  - Support multiple `-Ptask=` parameters in a single invocation
  - Use Gradle's built-in multi-task execution (already works: `./gradlew cnavClass cnavCallers -Ppattern=Foo -Pmethod=bar`) but improve output separation
- **Key challenge**: Different tasks need different parameters. Need a way to specify per-task parameters.
- **Alternative**: The `cnavContext` task (#56) handles the most common composite case. General-purpose batching may be over-engineering.

## 61. Stable JSON schemas — machine-fetchable schema documentation (Low value, low effort)

From user feedback: the JSON output works well but schemas aren't versioned or documented in a machine-fetchable way.

- **Suggestion**: Add a `cnavSchema -Ptask=cnavDead` command that outputs the JSON schema for a given task's output
- **Alternative**: Include schemas in `cnavHelpConfig` or `cnavAgentHelp` output
- **Why lower priority**: The JSON output is already self-describing, and agents can infer the schema from one example. Formal schemas mainly help tool integrations that want to pre-validate.

## 62. Separate prod/test in output (High value, medium effort)

From user feedback: all bytecode tasks mix production and test callers in a single list. For a class like `Poll` with 167 incoming references, it's hard to tell which are prod dependencies and which are test code. Users must mentally scan for "Test", "Fake", "Mother" etc.

- **Approach**: Tag each caller/reference with `[test]` or `[prod]` based on which source set the class came from. The `ClassScanner` already receives separate class directories for main vs test source sets — propagate this metadata through to the call graph and formatters.
- **Parameters**: `-Pprod-only=true` (filter to production callers only), `-Ptest-only=true` (filter to test callers only). Without either flag, show all with tags.
- **Applies to**: `cnavCallers`, `cnavCallees`, `cnavUsages`, `cnavComplexity`, `cnavDead`, `cnavRank` — any task that reports caller/reference lists
- **Implementation**: Add a `sourceSet: SourceSet` (enum: MAIN, TEST) field to `ClassInfo` during scanning. When building the call graph, propagate the source set to call edges. Formatters append `[test]`/`[prod]` tags.
- **Why high value**: Makes every bytecode task output immediately actionable without manual filtering. Test-only dependencies on a class are normal and expected; only prod dependencies indicate coupling.

## 64. Fan-in/fan-out interpretation guidance in agentHelp (Low effort, high polish)

From user feedback: `cnavAgentHelp` explains what the tasks do but not how to interpret results. Agents (and humans) need guidelines to avoid false alarms — e.g., `PollsRepository` with high fan-in is normal for a core repository, not a god object.

- **Add to agentHelp**: A "Result interpretation" section with heuristics:
  - Fan-in > 20 on a concrete class → may indicate a god object or central service (investigate)
  - Fan-in > 20 on an interface/abstract class → normal for core domain abstractions
  - Fan-in > 20 on a repository → normal, repositories are meant to be widely used
  - High fan-out → class depends on too many things, candidate for decomposition
  - Dead code with framework annotations → likely false positive (entry point via reflection)
  - Coupled files with high coupling degree but low shared revs → may be coincidence
- **Key file**: `AgentHelpText.kt`

## Future ideas (not yet planned)

- **Consider just failing on first file with wrong bytecode**: The current `ScanResult<T>` partial-fail approach adds complexity across scanners, caches, tasks, and mojos. A simpler alternative: fail fast on the first unsupported bytecode file with a clear error message. Less graceful but dramatically less code.
- **Expand support for older JDKs**: The plugin currently requires JDK 21+ (Gradle 9.x minimum). Explore lowering the bytecode target or supporting Gradle 8.x to reach projects still on JDK 17 or JDK 11.
- **Cross-referencing hotspots with bytecode data**: Combine `cnavHotspots` with `cnavCallers`/`cnavDeps` to answer "hotspot files and their structural dependencies". Would require mapping git file paths to bytecode class names via the source file metadata already extracted.
- **Entity ownership / main developer**: Who "owns" each file by contribution weight (`-a entity-ownership` and `-a main-dev` in Code Maat). Useful for "who should I ask about this code?" Could be added as a mode on `cnavAuthors`.
- **Architectural-level grouping**: Code Maat's `-g` flag to aggregate file-level results by logical component/layer. Would allow running hotspots, coupling, etc. at the sub-system level instead of individual files.
- **Source-level structural analysis**: For faster iteration during cycle-breaking, analyze imports and type references directly from source files without requiring compilation. This would allow running dependency analysis before a successful build — useful when mid-refactoring with compile errors. Fundamentally different approach from bytecode analysis, so this is a future exploration rather than an incremental feature.
