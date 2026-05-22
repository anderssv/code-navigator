# Plan

Items are grouped by theme. Within each group, items are ordered for sequential execution.
Value and effort are qualitative assessments to aid prioritization, not estimates.

---

## ~~Stale class file warning and drop forced compilation~~ — DONE

`ClassFileStaleness.check()` compares newest source vs class mtime. Warns when stale, errors when no class files. Gradle: removed `dependsOn("classes")`/`dependsOn("testClasses")`. Maven: `checkStaleness()` added to all bytecode mojos (`@Execute` still forces compilation). AgentHelpText updated with staleness guidance.

---

## MoveClass / file operations

Related improvements to class and file moving. Ordered by dependency.

### `cnavMoveClass`: top-level Kotlin declarations not updated when moving a file with a named class

**Value: high** | **Effort: medium**

From field test: moving `Metrics.kt` (which contains class `Metrics` plus top-level `val metricsRegistry`) correctly updated the class and its references, but the top-level `metricsRegistry` property was ignored. Files referencing `no.bankid.selvbetjening.metricsRegistry` still had stale imports after the move.

v0.1.65 added `*Kt` facade class support (when `-Pfrom` ends with `Kt`), but that only handles the case where the *entire file* is top-level declarations. When a file has both a named class and top-level declarations, moving the named class doesn't update the top-level declaration references.

- **Approach**: When moving a class from a file, also detect top-level declarations in the same file and update their `*Kt` facade references in consumer files. May need to run `ChangeType` for both the named class and the `*Kt` facade class in a single operation.

### `cnavMoveClass` / `cnavRenameClass`: handle files with multiple class declarations

**Value: high** | **Effort: medium**

When a Kotlin file contains multiple class declarations (e.g., a sealed class hierarchy, or a class with closely related types), `cnavMoveClass` currently only handles the single class specified in `-Pfrom`. The other classes in the file are left behind or not updated correctly.

Scenarios to handle:
- **Moving one class out of a multi-class file**: Should the other classes stay? Should the file be split? What happens to imports that referenced both classes?
- **Renaming a class in a multi-class file**: Other classes in the same file may reference the renamed class by simple name (no import needed since they're in the same file). These references need updating.
- **File naming**: Kotlin convention is that a file with a single public class is named after that class. If a class is moved out, should the remaining file be renamed?

Needs investigation to determine the right behavior for each scenario before implementation.

### `cnavMoveFile` — file-level move for Kotlin files with mixed declarations

**Value: high** | **Effort: medium**

From field test: a common Kotlin pattern is a `.kt` file containing a class plus top-level functions/properties. Currently you'd need to run `cnavMoveClass` for the class and then manually handle the top-level declarations. A file-level move command would handle both in one operation.

- **Parameters**: `-Pfrom-file=<relative-path>` (e.g., `src/main/kotlin/com/example/Metrics.kt`), `-Pto-package=<package>` (target package).
- **Behavior**: Move the file, update package declaration, run `ChangeType` for every class in the file AND the `*Kt` facade class, rewrite imports in all consumer files.
- **Relates to**: Top-level declaration handling and multi-class file handling above. Could subsume both if designed as the primary move mechanism, with `cnavMoveClass` as the single-class shorthand.

### `cnavMoveClass`: no support for merging into an existing file

**Value: medium** | **Effort: high**

From field test: moving `CryptoProvider` object to `selvbetjening.di` package failed because `CryptoProvider.kt` already existed in the target package (containing a `createCryptoProvider()` function that called the object being moved). `cnavMoveClass` only creates new files — it has no concept of merging a class into an existing file.

This is an inherently complex operation:
- Import deduplication between the moved class and the existing file.
- Handling conflicts (e.g., both files define a class with the same name).
- Deciding placement within the target file.

May not be worth automating — manual merge with cnav handling the reference updates could be the pragmatic answer. Consider adding detection: if the target file already exists, warn and suggest manual merge rather than silently failing or overwriting.

---

## Dead code improvements

Related improvements to `cnavDead`. Ordered by dependency — ConfidenceScorer extraction enables the others.

### ~~Extract ConfidenceScorer from DeadCodeFinder~~ — DONE

Confidence scoring logic extracted to `ConfidenceScorer` object. `DeadCodeFinder` delegates to it. Independently testable with `ConfidenceScorerTest`.

### ~~Introduce query/config objects for complex finders~~ — DONE

`DeadCodeQuery` data class bundles 21 parameters. `DeadCodeFinder.find(query)` accepts it. Old overload preserved for backward compatibility.

### ~~Dead class count mismatch between `cnavMetrics` and `cnavDead`~~ — DONE

Extracted `DeadCodeOrchestrator` as single source of truth for dead code scanning. `MetricsTask`/`MetricsMojo` now use `DeadCodeConfig` + `DeadCodeOrchestrator` with same defaults as `DeadCodeTask`. Removed `excludeAnnotated` from `MetricsConfig`. Verified on realworld-springboot: metrics and dead both report 2 dead classes (was 8 vs 2).

### ~~`@ControllerAdvice` not recognized as Spring entry point~~ — DONE

Added `@RestControllerAdvice` to `FrameworkPresets.SPRING` (the actual missing annotation — `@ControllerAdvice` was already present).

### ~~OVER_ENGINEERED false positives for standard domain layer packages~~ — DONE

Nearby packages with MODEL/CONTRACT coupling are now classified as BALANCED (intentional layering) or TOLERABLE (if volatile). The OVER_ENGINEERED verdict is effectively retired. Help text updated.

### Meta-annotation traversal for dead code filtering

**Value: high** | **Effort: medium**

`@RestController` is meta-annotated with `@Controller` which is meta-annotated with `@Component`. Currently, excluding `Component` does NOT exclude `@RestController`.

- **Approach**: In `AnnotationExtractor`, also scan annotation `.class` files from classpath JARs and resolve meta-annotations transitively.
- **Reuses**: Classpath resolution from `cnavJar` / full classpath scanning.
- **Why**: Covers custom stereotype annotations automatically. A project defining `@DomainService` (meta-annotated with `@Component`) would be handled without configuration.
- **Prerequisite**: `cnavJar` (classpath resolution infrastructure). Benefits from `Extract ConfidenceScorer`.

### Transitive dead code detection

**Value: medium** | **Effort: high**

From user feedback (v0.38): `cnavDead` found nothing even for `GenericError.from(RAResponseError)` which manual analysis suggested has unused paths. Dead code detection is too conservative — it sees a method called from a companion object and marks it as live, even if the callers of those callers are shrinking or dead.

- **Approach**: After initial dead code pass, trace callers transitively. A method is "transitively dead" if all its callers are themselves dead. Iterate until fixed point.
- **Extension — shrinking usage**: Track caller count over git history. Methods where caller count is declining over time are "shrinking" — not dead yet, but trending toward dead. Different from dead code detection (point-in-time) — this is trend analysis.
- **Confidence levels**: `DEAD` (zero callers), `TRANSITIVELY_DEAD` (all callers dead), `TEST_ONLY` (only test callers), `SHRINKING` (declining caller trend).
- **Prerequisite**: Extract ConfidenceScorer makes this cleaner to implement.

### `cnavDead` baseline diff — confirm cleanup was complete

**Value: medium** | **Effort: low**

After triaging dead code and removing items, re-run `cnavDead` and see what changed.

- **Approach**: `-Pbaseline=<path>` parameter pointing to a saved JSON output from a previous run. On re-run, show: items removed since baseline, items still present, new items.
- **Alternative**: Just save JSON and use `jq` to diff. Built-in support is more ergonomic but the alternative is viable.

---

## DSM / dependency analysis

Related improvements to package dependency analysis. `cnavWhyDepends` is a prerequisite for cycle fix suggestions, which in turn is a prerequisite for what-if simulation.

### ~~`cnavWhyDepends` — dependency edge explanation~~ — DONE

Implemented class-level dependency edge explanation. `WhyDependsBuilder` filters `PackageDependency` list by from/to package, collapses inner classes to top-level via `topLevelClass()`, groups by (source, target) pair with counts. Registered as `why-depends` goal with `from-package` and `to-package` params. Gradle task, Maven mojo, help text, and agent help all updated.

### Cycle fix suggestions in DSM

**Value: high** | **Effort: medium**

The DSM tells you which cycles exist, but not how to fix them. When `-Pcycles=true`, also show which specific class-level edges would need to move to break the cycle, and suggest which direction the dependency should flow.

- **Prerequisite**: Benefits from `cnavWhyDepends` infrastructure — same edge-explanation logic.
- **Separate from DSM what-if**: What-if simulation is a distinct, higher-effort feature. Evaluate need after cycle fix suggestions ship.

### `cnavFileDeps` — file-level dependency tree

**Value: high** | **Effort: medium**

Current dependency analysis works at package granularity (`cnavPackageDeps`, `cnavDsm`) or method/symbol granularity (`cnavFindCallers`, `cnavFindUsages`). There is no file-level view, which is the granularity humans and agents actually navigate at. File-level deps also capture type references (field types, parameters, return types, generics, annotations) that call graphs miss.

- **Builder**: Aggregate class-level bytecode references up to `UsageSite.sourceFile`. One edge = "file A references file B." Kotlin files with multiple top-level classes map all classes to the same file node.
- **Single-file mode**: `-Pfile=OrderService.kt` — direct deps (files this file uses). `-Preverse=true` — files that depend on this one. `-Pdepth=N` — transitive tree with cycle-collapse (reuse `CallTreeBuilder` cycle handling).
- **All-files mode**: `-Pall=true` — emit the full file-dep graph, no root.
- **Parameters**: `-Pproject-only=true`, `-Ppattern=<regex>`, `-Pformat=text|llm|json`.

Use cases for all-files mode:
- **Architecture overview / DSM at file granularity** — spot tangles and highly-coupled files across the whole project.
- **Fan-in/fan-out ranking** — rank files by incoming deps (coupling hotspots, risky to change) or outgoing deps (files doing too much).
- **File-level cycle detection** — file cycles are more actionable refactoring targets than package cycles.
- **Orphan detection** — files with zero incoming and zero outgoing project deps (likely dead or misplaced).
- **Graph export** — JSON feed for visualization tools (Gephi, d3), LLM context selection, or trend analysis over time (combine with git history: "coupling growing").
- **Batch impact queries** — "which files depend on anything in package X?" in one pass instead of N per-file queries.

- **Alternative framing**: Extend `cnavDsm` with `-Pgranularity=file|package|class` instead of a separate task. Worth evaluating — same matrix/cycle infrastructure, same what-if semantics.
- **Overlap**: `cnavFindUsages -Pgroup-by=file` (v0.1.71) gives file-level reverse deps for a single symbol. `cnavFileDeps -Pfile=X -Preverse=true` gives file-level reverse deps for all symbols in a file. Complementary.

### DSM what-if simulation

**Value: medium** | **Effort: high**

`-Pwhat-if=<class>:<target-package>` — simulate moving a class to a different package and re-evaluate cycles without actually making the change.

- **Prerequisite**: Cycle fix suggestions should ship first.

---

## Classpath / JAR scanning

Related improvements to scanning library classes and the runtime classpath.

### Maven: `-Djar` support for Mojos

**Value: medium** | **Effort: low**

The `-Pjar=<path-or-artifact>` parameter is implemented for Gradle tasks but not yet for Maven Mojos.
Add `@Parameter(property = "jar")` to `ListClassesMojo`, `FindClassMojo`, `ClassDetailMojo`, and `FindSymbolMojo`,
with Maven-native artifact resolution via `project.runtimeClasspathElements`.

### Full classpath scanning option

**Value: high** | **Effort: medium**

Add `-Pclasspath=true` to scan the full runtime classpath (project classes + all dependency JARs).

- **Applies to**: `cnavListClasses`, `cnavFindClass`, `cnavFindSymbol`, `cnavClass`, `cnavInterfaces`, `cnavUsages`
- **Reuses**: Classpath resolution infrastructure from `cnavJar`.
- **Considerations**: Significantly slower (thousands of classes). Combine with existing `-Ppattern` / `-Powner` filters to narrow scope. Consider caching scanned JARs by checksum.
- **Why**: AI agents frequently need to check library API signatures to write correct code.

### Lazy JAR scanning for external class classification

**Value: medium** | **Effort: medium**

When `include-external=true`, `ClassTypeCollector` only scans project class directories. External library classes aren't in the `classTypeRegistry`, so `classifyTarget` returns null and they're counted as `unknown`. The current workaround (FIX 5) tracks these as `unknownCount` and defaults all-unknown pairs to CONTRACT strength.

- **Approach**: When a target class is not in the registry, lazily resolve its `.class` file from the runtime classpath JARs and classify it. Only scan the specific classes that appear in dependencies, not entire JARs.
- **Reuses**: Classpath resolution infrastructure from `cnavJar`.
- **Benefits**: Eliminates `unknownCount` entirely. Strength classifications for external dependencies become accurate instead of defaulting to CONTRACT.
- **Trade-off**: Adds JAR I/O during classification. Mitigate with a per-run cache of resolved classes.

---

## Find-usages output quality

From field test (v0.1.72): `cnavFindUsages` output is noisy at bytecode level. A single logical call site (e.g., constructing `RAClientImpl`) produces 3-4 lines (`.new` type ref + `.<init>` method call + `.checkcast` + field access). Lambda classes like `MonitorService$getCurrentStatus$2$raClientStatusDeferred$1` obscure the actual caller. Users pipe through `grep -v` to find meaningful results.

Ordered by dependency — collapsing enables the summary mode, and smart usages builds on the cleaner output.

### ~~Collapse bytecode noise in find-usages output~~ — DONE (v0.1.73)

Implemented in `UsageCollapser`. Collapsed output is the default; `-Praw=true` for bytecode-level detail.

### ~~Call-site summary mode for find-usages~~ — DONE (v0.1.73)

Merged into the collapsing step. Each line is flat and self-contained with combined kind tags.

### ~~Smart usages — auto-include interface implementations~~ — DONE (v0.1.73)

Implemented: when `cnavFindUsages -Ptype=X` targets an interface, `[impl]` lines are auto-included. `-Pinclude-impls` expands the search to include usages of each implementor.

---

## Output & UX improvements

Improvements to task output, discoverability, and agent experience.

### Refactoring task discoverability

**Value: high** | **Effort: low**

From field test: agent ran `cnavFindUsages` at the start of a class move and missed that `cnavMoveClass` exists — would have done the entire refactor in one call. The help text lists `cnavMoveClass` parameters but doesn't position it as the right tool for class moves. Agents read parameter tables linearly and miss intent-oriented guidance.

Partial progress (v0.1.72):
- ✅ "Common Refactoring Tasks" section added to `cnavAgentHelp` compact output (parallel to "Common Questions → Which Task").
- ✅ Install section (`-Psection=install`) upgraded to name write commands first (`cnavMoveClass`, `cnavRenameMethod`), followed by the read commands. "Do not manually edit imports" warning added.
- ✅ Task descriptions in `cnavHelp` already state outcomes (verified in `TaskRegistry`: `MOVE_CLASS_TASK`, `RENAME_METHOD_TASK`, etc.).

Remaining:
- Audit whether `cnavAgentHelp` mentions refactoring outcomes in other sections (`workflow`, `interpretation`) — currently they focus on analysis flow.
- Consider a short "refactoring cheat sheet" as its own section (see "Goal-oriented task discovery" below).

### ~~Goal-oriented task discovery — `-Psection=refactor`~~ — DONE

Added `-Psection=refactor` to `cnavAgentHelp`. Groups tasks by intent: move/rename, explore before refactoring, find targets, verify after. Listed in the "More Detail" section of compact output.

### Preview-by-default for write commands

**Value: medium** | **Effort: low**

From field test feedback: safer default for agents would be preview mode on by default with explicit `-Papply=true` to commit. Currently `cnavMoveClass` / `cnavRenameMethod` / etc. apply changes unless `-Ppreview` is passed.

- **Breaking change**: flips the default. Mitigate with a deprecation cycle: warn loudly when apply-mode is invoked without explicit `-Papply=true` for one release, then flip.
- **Alternative**: keep current default, but `cnavAgentHelp` examples always show `-Ppreview` first and recommend running preview → review → re-run with apply. Less safe, non-breaking.
- **Decide**: breaking flip vs. doc-only nudge. Breaking flip aligns with the "agents should not surprise users" principle.

### Consistent `-Pproject-only` support across all tasks

**Value: medium** | **Effort: low**

From user feedback: `cnavFindUsages -Ptype=SignatureContext` failed with a short name because `-Pproject-only` isn't supported on that task. The error message was clear, but it cost a round-trip. Users expect consistent parameter support across tasks.

- **Approach**: Audit all tasks for `-Pproject-only` / `-Pscope` support. Add missing support where it makes sense. Document which tasks support which filters in `cnavAgentHelp`.
- **Note**: Some tasks may intentionally not support certain filters — document why.

### ~~Investigate `[prod]`/`[test]` misclassification on Maven projects~~ — DONE

Fixed: `getOrBuildTagged` now validates that cached source sets match requested tags. Previously, a non-tagged `getOrBuild` call (from MetricsMojo, PackageDepsMojo, etc.) would write the cache with all classes tagged as MAIN, and subsequent `getOrBuildTagged` calls would read the stale cache with wrong source set tags.

### ~~Default `cnavDead` to exclude test classes~~ — DONE

Default scope for `cnavDead` changed from ALL to PROD. Test classes are excluded by default. Output includes notice: "Test classes excluded. Use scope=all to include test classes." Applies to TEXT and LLM formats.

### ~~Filter non-source files from git analysis recommendations~~ — DONE

Non-source files (paths not starting with `src/`) no longer get recommendation annotations in coupling and hotspot output. Files still appear in results but without `←` advice meant for source code.

### Add interpretation section to all analysis task output

**Value: high** | **Effort: medium**

All analysis tasks should include a short (2-4 sentences) interpretation section in their output to help LLMs pick up the right context for results. Show to both humans and LLMs.

- Each task formatter appends a brief "Interpretation" block explaining what the results mean and how to act on them.
- Keeps agents from misreading output (e.g., treating 0-caller framework entry points as dead code, or confusing stripped package prefixes with actual package names).

---

## Composite analysis tasks

Tasks that combine multiple existing analyses into a single view.

### `cnavRisk` — composite risk analysis

**Value: high** | **Effort: medium**

From user feedback (v0.38): the user had to mentally cross-reference 6 separate task outputs (hotspots, churn, coupling, age, complexity, authors) to identify that RAClient.kt was the riskiest file. A single task should do this automatically.

- **Formula**: `risk = change_frequency * complexity * coupling_degree`. Weight factors configurable. Based on Adam Tornhill's "Your Code as a Crime Scene" approach.
- **Inputs**: Reuses existing builders — `HotspotBuilder`, `ChurnBuilder`, `ChangeCouplingBuilder`, `CodeAgeBuilder`, `AuthorBuilder`, `ClassComplexityAnalyzer`.
- **Output**: Ranked list of files/classes by composite risk score, with breakdown showing which factors contributed most. Example:
  ```
  #1  RAClient.kt          risk=0.94  (hotspot=51rev, churn=+421, complexity=738loc, coupling=57%, authors=2)
  #2  SearchService.kt     risk=0.71  (hotspot=34rev, churn=+180, complexity=320loc, coupling=52%, authors=3)
  ```
- **Parameters**: `-Ptop=20` (default), `-Pformat=llm|json|text`, `-Psince=<git-ref>` (optional time window)
- **Hybrid task**: Requires both git history and compiled bytecode (for complexity).

### `cnavReport` — consolidated full analysis

**Value: medium** | **Effort: low**

Run all analysis tasks and produce a single consolidated report. `cnavMetrics` already exists for a summary snapshot; `cnavReport` runs everything and outputs all results in one pass.

- **Parameters**: Inherits from constituent tasks. `-Pformat=json` produces a single JSON object with sections per analysis.
- **Why useful**: Agents often want the full picture. A single task is faster (shared caching, one compilation) and produces a coherent snapshot.

### Per-package health dashboard — `[Balanced Coupling]`

**Value: medium** | **Effort: medium**

Aggregate all per-package metrics into a single view: volatility, coupling strength breakdown, distance profile, cycle involvement, and balance assessment.

- **Where**: New builder that combines outputs from `DsmMatrixBuilder`, volatility, and the balance heuristic.
- **Output**: One row per package with columns for each metric dimension. Highlight packages that are imbalanced.
- **Why**: Currently `MetricsBuilder` only produces project-level aggregates. Per-package breakdown is needed to act on Balanced Coupling findings.
- **Could be**: A mode of `cnavMetrics` (`-Pby-package=true`) or a separate `cnavPackageHealth` task.
- **Prerequisites**: Volatility per package (DONE), `cnavBalance` (DONE).

---

## Standalone new tasks

### `cnavCohesion` / `cnavSuggestStructure` — prescriptive package structure analysis

**Value: high** | **Effort: high**

Current DSM-family goals (`cnavDsm`, `cnavStrength`, `cnavDistance`, `cnavCycles`) describe what the structure *is* and where it's unhealthy. None answer "what structure *should* we have?" — they identify problems without suggesting solutions.

**Three complementary analyses:**

1. **Cohesion scoring** — For each package, measure how much classes within it actually depend on each other vs. reaching outside. Low internal cohesion = package should be split. High cross-package affinity = packages should be merged.
   - Metric: ratio of internal edges to total edges per package
   - Output: ranked list of packages by cohesion score, with "split" or "merge" suggestions

2. **Move suggestions** — Identify misplaced classes based on dependency gravity. "ClassX depends on 5 classes in package B and 0 in its current package A → suggest move to B."
   - Uses existing `PackageDependency` and call graph data
   - Filters out ubiquitous types (high fan-in) that would distort suggestions
   - Output: list of (class, current package, suggested package, reason)

3. **Cluster analysis** — Group classes by actual dependency affinity (classes that depend on each other more than on outsiders form a natural cluster). Compare actual packages to optimal clusters → quantify structural drift.
   - Algorithm: community detection on the class dependency graph (e.g. Louvain or label propagation)
   - Output: proposed package groupings, diff against current structure

**Incremental delivery:**
- Start with cohesion scoring (simplest, uses existing DSM data)
- Add move suggestions (builds on cohesion + existing TypeMatcher/ClassIndex)
- Cluster analysis last (requires graph algorithm, most complex)

**Relation to existing goals:**
- `cnavLayerCheck` enforces a *declared* structure — these goals would help you *discover* what to declare
- `cnavStrength` identifies strong coupling — cohesion analysis explains whether that coupling means "merge" or "add an interface"
- `cnavDistance` measures abstract/stable balance — move suggestions address the concrete "what to do about it"

### Fix `type-hierarchy` to show full supertype/interface chain

**Value: high** | **Effort: medium**

From evaluation on spring-petclinic and realworld-springboot: `type-hierarchy` only shows the class itself — not the inheritance chain. For `OwnerRepository` extending `JpaRepository`, the output is nearly empty. For framework types with deep hierarchies, the command is near-useless.

- **Expected**: Show the full chain of supertypes and interfaces, including library types resolved from classpath JARs.
- **Minimum**: Show supertypes/interfaces found in project bytecode. Extend with classpath scanning when that infrastructure is available.
- **Relates to**: Classpath/JAR scanning section — full hierarchy requires resolving library types.

### `cnavTestHealth` — verify all test methods actually ran

**Value: high** | **Effort: medium**

From user feedback: a project had 19 silently skipped tests because test methods had non-`Unit` return types. Count `@Test`-annotated methods from bytecode, compare against JUnit XML results, flag the delta.

1. **Bytecode scan**: Find all methods annotated with `@Test` (JUnit 4/5, Kotlin Test). This is the "expected" set.
2. **JUnit XML scan**: Parse test result XML files (`build/test-results/test/TEST-*.xml` or `target/surefire-reports/TEST-*.xml`). This is the "actual" set.
3. **Diff**: Report methods present in bytecode but absent from XML results — the silently skipped tests.

- **Lifecycle**: `dependsOn("test")` — runs after tests complete
- **Additional checks** (bytecode-only): test methods missing `@Test` annotation but named `test*`, test classes with no `@Test` methods, `@Disabled`/`@Ignore` inventory
- Both Gradle and Maven write the same JUnit XML format, so one parser handles both.

### `cnavTestCoverage` — per-test-class coverage proximity analysis

**Value: medium** | **Effort: medium**

Identify which production classes are only tested "at distance" (no dedicated test, only exercised transitively by tests for other classes). Helps find brittle test coupling where a change to a utility breaks distant tests without a close test isolating the cause.

**Approach** (prototyped on cnav's own codebase):
1. A JUnit `TestExecutionListener` calls JaCoCo agent's `getExecutionData(reset=false)` / `reset()` per test class, producing one `.exec` file per test class in a single test run.
2. A builder reads all exec files + class directories, produces a coverage matrix: for each production class, which test classes cover it and at what "distance" (same-name dedicated test = close, same package = near, cross-package = distant).
3. Flags production classes covered **only** at distance with no close test — these are the coupling risk.

**Findings from prototype** (cnav self-analysis, 170 test classes, 383 production classes):
- 129 classes had no dedicated test (only distant coverage). Most were harmless: data classes, enums, ubiquitous types (`ClassName`, `PackageName`), and shared infrastructure.
- Actual coupling risk concentrated in: refactor support utilities (4 files), `CallTreeFormatter` (8 distant tests, no dedicated test), and orchestrators.
- `JsonFormatterTest` and `LlmFormatterTest` appeared as distant coverage for nearly every result type — this is expected and healthy (tests serialization).

**Open questions**:
- Should this integrate with JaCoCo (requires agent access), or use a simpler heuristic (file name matching only, no coverage data)?
- Line-level overlap analysis (which specific lines are redundantly covered) would require comparing probe data across exec files — significantly more complex.
- The JUnit listener approach requires `output=file` (default) not `output=tcpserver`. May need to detect agent configuration.

### `cnavDiff` — structural diff between builds

**Value: medium** | **Effort: medium**

Compare two compiled states and show structural changes: added/removed/changed classes, methods, and dependency edges.

- **Use cases**: API signature changes from dependency upgrades; verifying a refactoring was purely structural.
- **Builder**: `StructuralDiff.diff(baselineClassDir, currentClassDir) -> List<Change(className, memberName, kind: ADDED|REMOVED|SIGNATURE_CHANGED, oldSignature?, newSignature?)>`
- **Parameters**: `-Pbaseline=<path>` (path to baseline class directory), `-Paffected=true` (also list affected call sites)

### `cnavUnused` — unused build dependencies

**Value: medium** | **Effort: medium**

Find entire libraries that could be removed. For each declared dependency JAR, extract the package list. Scan project bytecode for references. Dependencies with zero references are candidates for removal.

- **Caveats**: Runtime-only dependencies (JDBC drivers, logging backends) will show as "unused." Needs an exclusion mechanism.
- **Reuses**: Classpath enumeration infrastructure from `cnavJar` / meta-annotation traversal.

---

## Internal code quality

Improvements to cnav's own codebase — not user-facing features.

### Potentially dead code in cnav's own codebase (from self-analysis)

**Value: medium** | **Effort: low**

Self-analysis with `cnavDead` found high-confidence dead code in core:

- `CallGraphCache.build()`, `ClassIndexCache.build()`, `InterfaceRegistryCache.build()`, `SymbolIndexCache.build()` — `build` methods on cache classes have no callers (`getOrBuild` is used instead). If these are truly dead, remove them.
- `FileCache.build()`, `FileCache.getOrBuild()`, `FileCache.isFresh()`, `FileCache.read()`, `FileCache.readLines()`, `FileCache.write()`, `FileCache.writeLines()` — multiple FileCache methods flagged. These may be called via subclass dispatch (check if cnav's analysis misses this).
- `UsageScanner.scan()` — no callers found. Investigate if this is superseded.
- `DsmDependencyExtractor.extractFromClass-Ue68T7I()` — inline class mangled name. May be a false positive from name mangling.
- `CollapsedUsage.copy-NT0O-NM()` — Kotlin copy() with inline class param. Expected false positive from name mangling.

The Gradle task methods (`showDeadCode`, `findCallers`, etc.) are all correctly flagged low-confidence — they're invoked by the Gradle runtime via reflection, not via direct calls. This confirms framework-entry-point detection is working correctly for Gradle `@TaskAction`.

### Test suite health: coverage, speed, and duplication

**Value: high** | **Effort: medium**

**Investigation results** (2,214 tests, 162 classes, ~15s total — as of v0.1.70):

#### Speed

71% of execution time is in 5 test classes. The remaining classes total ~3s — not worth optimizing.

| Test Class | Time | Root Cause |
|---|---|---|
| `MoveClassRewriterTest` | 6.5s | KotlinParser + classpath + ChangeType recipe, re-initialized per test (15 full parse cycles × 24 files) |
| `RenameParamRewriterTest` | 1.4s | KotlinParser re-initialized per test (14 parse cycles × 24 files) |
| `RenameMethodRewriterTest` | 0.95s | Same pattern (8 parse cycles) |
| `GitLogRunnerTest` | 0.84s | ~40 real `git` subprocess spawns |
| `RenamePropertyRewriterTest` | 0.70s | Same pattern (8 parse cycles) |

- **Cache KotlinParser / parsed ASTs in rewriter tests**: Share a parsed AST across tests using the same source roots. Could cut top-5 test time from 10.4s to ~3-4s. Single biggest speed win available.

#### Coverage

Overall: ~84% instruction, ~83% line, ~74% branch. **Excluding Gradle task wrappers: ~95%.**

The gap is almost entirely the Gradle task layer (63+ classes, all at 0%). These are thin wrappers around core logic, only exercisable via Gradle TestKit.

Non-Gradle gaps to address:

- **`FieldExtractor`** — 0% coverage. Only non-Gradle core class at zero. **Add tests.** (low effort)
- **`LlmFormatter`** — ~80%. Primary agent-facing formatter. **Investigate uncovered branches.** (medium effort)
- **`JsonFormatter`** — ~81%. **Investigate uncovered branches.** (medium effort)
- **`RenameMethodRewriter` / `RenamePropertyRewriter`** — ~72% each. Some code paths untested. (low effort)
- **`GitDiffRunner`** — ~45%. Subprocess-based. (low effort)

#### Remaining action items

- **Cache KotlinParser in rewriter tests** (high impact, medium effort)
- **Add `FieldExtractor` tests** (high impact, low effort)
- **Cover `LlmFormatter` / `JsonFormatter` uncovered branches** (medium impact, medium effort)
- ~~**Reduce duplication**~~ **DONE** — see `plan-completed.md`.
- ~~**Align with kotlin-tdd**~~ **DONE** — already aligned.

### ~~Break `formatting` ↔ `navigation.dsm` cycle~~ — DONE

Orchestrators (`DistanceOrchestrator`, `StrengthOrchestrator`) now return result data classes instead of formatted strings. Formatting moved to `DsmOutputFormatter` in the `formatting` package. Callers (Gradle tasks / Maven mojos) use `DsmOutputFormatter.format(output, config.format)`. No production cycles remain.

### ~~Align test packages with production packages~~ — DONE

Moved ~95 test files from flat `navigation/` test package to sub-packages matching production structure (`annotation/`, `bytecode/`, `changedsince/`, `classinfo/`, `complexity/`, `context/`, `deadcode/`, `dsm/`, `metrics/`, `rank/`, `relations/callgraph/`, `relations/hierarchy/`, `relations/implementors/`, `stringconstant/`, `symbol/`, `types/`). Shared test utilities (`TestClassWriter`, `TestCallGraphBuilder`) remain in `navigation/` and are accessed via wildcard import.

### ~~Break 6-package core cycle~~ — DONE

Resolved by the package restructure (commit `a3c3bf9`). Split `navigation.core` into `types/`, `bytecode/`, `cache/`. Moved `PatternEnhancer` to `types/`, `CacheFreshness` to `cache/`, `FrameworkPresets` to `types/`. No production cycles remain.

### Split JsonFormatter and LlmFormatter per-feature

**Value: medium** | **Effort: medium**

Self-analysis found `JsonFormatter` (347 outgoing dependencies, 72 referenced types, fanIn=257) and `LlmFormatter` (similar scale) are the highest-complexity classes in the entire codebase. They change together 96% of the time.

- **Approach**: Split into per-feature formatters (e.g., `CallTreeJsonFormatter`, `DeadCodeJsonFormatter`). Top-level formatters become thin dispatchers.
- **Ordering**: `LlmFormatter` first (primary agent-facing format), then `JsonFormatter`. `TableFormatter` is smaller and can follow later.
- **Benefits**: Adding a new feature means adding a new formatter file, not editing a shared god class.

### Reduce Gradle/Maven duplication via orchestrator extraction

**Value: medium** | **Effort: medium**

Every Gradle Task / Maven Mojo pair duplicates its full orchestration logic. The pairs differ only in property reading, output channel (`logger.lifecycle` vs `println`), build dir resolution, and Maven's "classes not found" guard. ~590 lines of duplicated orchestration across 14 pairs.

Three pairs already follow the correct pattern — `StrengthOrchestrator`, `DistanceOrchestrator`, and `DeadCodeOrchestrator` live in `core/` and their Task/Mojo are thin ~10-line adapters. Extend this to the rest.

Variations of input and output should be tested on the orchestrators — they are the natural place to verify that config options (filters, scopes, raw mode, etc.) flow through correctly to the underlying components.

**Priority order** (by duplicated lines):
1. `FindUsagesTask ↔ FindUsagesMojo` (~90 lines) — extract `FindUsagesOrchestrator`
2. `MetricsTask ↔ MetricsMojo` (~70 lines) — extract `MetricsOrchestrator`
3. `BalanceTask ↔ BalanceMojo` (~55 lines) — extract `BalanceOrchestrator`
4. `CallTreeTaskSupport ↔ CallTreeMojoSupport` (~55 lines) — merge into single `CallTreeOrchestrator` in core
5. `DeadCodeTask ↔ DeadCodeMojo` (~45 lines) — already has `DeadCodeOrchestrator`, wire mojos to use it
6. `LayerCheckTask ↔ LayerCheckMojo` (~45 lines) — extract `LayerCheckOrchestrator`
7. `DsmTask ↔ DsmMojo` (~40 lines) — extract `DsmOrchestrator`
8. `PackageDepsTask ↔ PackageDepsMojo` (~40 lines) — extract `PackageDepsOrchestrator`
9. `FindClassTask ↔ FindClassMojo` (~40 lines) — extract `FindClassOrchestrator`
10. `ComplexityTask ↔ ComplexityMojo` (~30 lines) — extract `ComplexityOrchestrator`
11. `RankTask ↔ RankMojo` (~25 lines) — extract `RankOrchestrator`
12. `WhyDependsTask ↔ WhyDependsMojo` (~25 lines) — extract `WhyDependsOrchestrator`
13. Remaining small pairs (Hotspot, Churn, CodeAge, AuthorAnalysis, etc.) — ~15 lines each

**Also extract shared micro-patterns:**
- `taggedDirs → scope filter → classDirectories` (3 lines × 16 files) — shared helper function
- `SkippedFileReporter` boilerplate (2-3 lines × 24 files) — helper
- Maven "classes not found" guard (3 lines × 12 mojos) — shared guard in `MavenSupport`
- `isArtifactCoordinate` pure function duplicated in `GradleSupport` and `MavenSupport` — move to `core/`

Also found core duplication (lower priority):
- `InlineMethodDetector.kt` ↔ `DelegationMethodDetector.kt` (227 tokens) — similar visitor patterns
- `RenameMethodRewriter.kt` ↔ `RenamePropertyRewriter.kt` ↔ `RenameParamRewriter.kt` (117 tokens each) — shared rewriter boilerplate
- `RenamePropertyFormatter.kt` ↔ `RenameMethodFormatter.kt` (101 tokens)

### ~~Extract shared orchestration from Gradle tasks and Maven mojos~~ — DONE

`StrengthOrchestrator` and `DistanceOrchestrator` extracted to core. Gradle tasks and Maven mojos are thin wrappers handling config parsing, directory resolution, and output routing.

### ~~Make `DsmDependencyExtractor.packageFilter` nullable~~ — DONE

Changed `packageFilter` from `PackageName` with `PackageName("")` magic value to `PackageName?` with null meaning "no filter." Updated all callers, config classes, and tests. No default values on parameters.

---

## Infrastructure

### Structured cache format

**Value: medium** | **Effort: medium**

`FileCache` subclasses serialize as tab-separated positional fields. Adding a field requires updating both `serialize()` and `deserialize()` and any field order mismatch silently corrupts data.

- **Approach**: Replace with a self-describing format that tolerates field additions without breaking existing caches.
- **Note**: Consider removing cache entirely — benchmarking on ~20k LOC / 488-class project showed zero measurable difference. Needs testing on larger projects.

### Gradle incremental task support

**Value: medium** | **Effort: high**

Support Gradle's incremental task API (`@InputFiles`, `@OutputFile`, `InputChanges`) to skip unchanged files. Call graph analysis is inherently whole-program, so incremental support is most beneficial for leaf tasks (`cnavListClasses`, `cnavFindSymbol`, `cnavFindClass`).

---

## Layer check

### Revise peerLimit and testInfrastructure expressiveness

**Value: medium** | **Effort: low**

The current `peerLimit` and `testInfrastructure` attributes work but may not be the most intuitive way to express the intent. Evaluate whether there's a better way to express:

- **Peer dependencies**: `peerLimit = 0` (forbidden), `peerLimit = 3` (max 3 per class), `peerLimit = -1` (unlimited). Is per-class counting the right granularity? Should there be a layer-level total?
- **Test infrastructure exemptions**: `testInfrastructure: true` on a layer lets test classes depend on it. Is there a more general "exemption" model?
- **Layer groups**: The old package-based plan had "peer groups" (same-index arrays). The pattern-based model uses layer ordering only. Is there demand for peer groups?

Low priority — the current model works. Revisit when real-world usage reveals friction.

---

## Hexagonal architecture analysis (loose thoughts — not to be acted upon yet)

**Value: TBD** | **Effort: TBD**

Analyze code-navigator's own codebase through a hexagonal architecture lens. The goal is to ensure outer layers are well-defined and that the core uses data classes carrying relevant metadata rather than leaking formatting or parsing concerns inward.

Preliminary thinking on port/adapter boundaries:

- **Input edge (driving)**: Command parsing — reading `-P`/`-D` properties, building config objects. Currently split across Gradle tasks and Maven mojos with some duplication.
- **Output edge (driven)**: Printing formats — TEXT, LLM, JSON. Currently in `JsonFormatter`, `LlmFormatter`, `TableFormatter` (already identified as god classes in the internal code quality section).
- **Bytecode edge (driven)**: Interfacing with compiled classes — listing, exact matching, simple search patterns, possibly hierarchy traversal. This is the primary data source and may warrant its own port abstraction.
- **Core**: Analysis logic operating on data classes with relevant metadata. Should not know about property parsing or output formatting.

This is a planning entry to discuss and design together before any implementation. Questions to resolve:
- What are the right port abstractions?
- Does the bytecode scanning layer belong inside or outside the core?
- How does this relate to the existing "Extract shared orchestration" and "Split formatters" items?
- Is the current `Config → Builder → Formatter` separation already close enough, or does it need restructuring?

---

## Evaluated and rejected: CLI-first architecture

Shipping the core as a standalone CLI was considered to eliminate Gradle/Maven duplication. **Rejected** because Gradle and Maven plugins provide frictionless installation (`plugins { id("...") }` / `<plugin>`), automatic version management, transitive dependency resolution, and zero separate install. A CLI would require separate installation, manual upgrades, and JVM version coordination -- a significant DX regression.

The shared orchestration extraction (above) achieves most of the deduplication benefit while keeping the build-tool distribution advantage.

---

## Parked

Items below are low-priority or may not be worth building. Revisit if demand emerges.

- **Abstractness per package** `[Balanced Coupling]`: Per-package ratio of abstract/interface classes to total classes (Robert C. Martin's `A` metric). Not needed for the Balanced Coupling pipeline — integration strength classification only needs per-class abstract/interface flags, not a package-level aggregate. Add as a standalone metric if demand emerges.
- **Custom entry-point config file** (`.cnav-entry-points`): Framework presets + `exclude-annotated` + `treat-as-dead` cover most cases. A config file adds marginal value over the existing parameters. Revisit if users request it.
- **DI-aware `cnavInjectors`**: Largely solvable with `cnavUsages -Ptype=X` combined with interface dispatch resolution. High effort for marginal gain.
- **Stable JSON schemas** (`cnavSchema`): JSON output is already self-describing. Agents infer schema from examples.
- **Split root package** (S9): Lower priority now that `navigation/` has been split into sub-packages. Dependency direction is already clear enough.

## Future ideas (not yet planned)

- **Scope decision: tool vs. platform**: With 40 tasks today and 20+ planned, code-navigator is approaching "code intelligence platform" territory. This is fine, but worth an explicit decision: stay as a focused collection of tasks, or invest in extensibility infrastructure (plugin system, composable analysis pipelines, third-party task registration)? The answer affects architectural choices going forward. If staying focused, the current `TaskRegistry` approach scales well enough. If building for extensibility, consider a plugin API where tasks are discovered rather than registered.
- **`cnavFindCallees` callee explosion**: `CallTreeBuilder` expands ALL polymorphic implementors as separate children with no collapsing. Default maxdepth=3 causes >51KB output for methods touching deep hierarchies. Root cause: `resolveInterfaceDispatch` adds every implementor, no deduplication of identical subtrees, no output truncation anywhere. Solutions: collapse dispatch groups into a single "N implementors" node with expand-on-demand, add a max-children limit per node, lower default depth for callees. v0.1.47 field test.
- **`cnavFindCallers` class-match UX hint**: `CallGraph.findMethods()` uses `Regex.containsMatchIn` on `qualifiedName` (className.methodName). Pattern "Parser" matches every method in `Parser` class, producing separate caller trees per method. User expected "who references Parser as a type" which is `cnavFindUsages -Ptype=Parser`. Fix: when pattern matches only class-name portions of multiple methods in the same class, add a hint suggesting `cnavFindUsages -Ptype=`. v0.1.47 field test.
- **Improve `cnavAnnotations` discoverability**: Field test (v0.1.44) reported "no inverse annotation search" but the feature exists — `cnavAnnotations -Ppattern=Serializable` finds all classes with that annotation. The task name is ambiguous. Consider a task alias (`cnavFindByAnnotation`), better no-results guidance mentioning retention policy / `methods` flags, or more prominent placement in `cnavAgentHelp`. v0.1.45 re-test clarified: `cnavAnnotations` only finds RUNTIME and CLASS retention annotations present in bytecode. SOURCE retention annotations (e.g. `@Suppress`) are invisible — this is inherent to bytecode analysis, but should be documented in no-results guidance. v0.1.46: no-results hint now suggests `-Pmethods=true` and retention policy (bug #8 FIXED). Remaining: task alias and retention policy documentation in help text.
- **`cnavDead -Pscope=prod` no-visible-effect guidance**: When `scope=prod` is set and filtering has no effect, add a note to output explaining why.
- **Remove `junit` from `FrameworkPresets` or document it's a no-op for `cnavDead`**: v0.1.45 analysis suite reported `-Ptreat-as-dead=junit` has no observable effect. Root cause: dead code analysis scans both source sets by default now, but JUnit annotations on test classes are typically excluded from dead code results because test classes are considered live (they have callers from the test runner). The junit preset may still be useful for edge cases. Options: (a) remove `junit` from presets (it's misleading), (b) document in help that `treat-as-dead` only applies to production-class annotations, (c) keep as-is.
- **`cnavChangedSince` parameter naming**: v0.1.45 analysis suite noted `-Pref=<git-ref>` is unintuitive — users expect `-Psince=<date>`. Low priority, but a `-Psince` alias or date-to-ref conversion would improve ergonomics.
- **Kotlin name-mangled method display**: Methods like `validateAndParse-IoAF18A` are Kotlin inline class return types. Low priority but a note in output (e.g. `[inline-class-mangled]`) would reduce confusion.
- **Dead code: flag methods called only from test scope**: Use source set tagging to identify production methods/classes whose only callers are in the test source set. These are candidates for removal since no production code depends on them. Replaces the current separate `testGraph` approach in `DeadCodeFinder` with a unified call graph that has source set metadata.
- **Remove cnav disk cache entirely**: Zero measurable difference on ~20k LOC. Reduces complexity. Needs testing on larger projects.
- **Fail fast on wrong bytecode**: Replace `ScanResult<T>` partial-fail with hard failure + clear error.
- **Cross-reference hotspots with bytecode**: Combine `cnavHotspots` with `cnavCallers`/`cnavDeps`.
- **Entity ownership / main developer**: Who "owns" each file by contribution weight. Mode on `cnavAuthors`.
- **Architectural-level grouping**: Aggregate file-level results by logical component/layer.
- **Source-level structural analysis**: Analyze imports from source files without requiring compilation. `cnavSize` and `cnavDuplicates` are source-level tasks; import/dependency analysis from source would be the next step.
- **Deterministic refactorings**: See dedicated section below.

---

## Future deterministic refactorings for LLMs

`cnavRenameParam` (DONE), `cnavRenameMethod` (DONE — v0.1.55), `cnavRenameProperty` (DONE — v0.1.59), and `cnavMoveClass` (DONE — v0.1.56) are deterministic refactorings using OpenRewrite for AST-based source transformation. The key insight: LLMs are unreliable at multi-file refactorings because they guess at call sites, miss named arguments, forget string templates, and hallucinate file paths. A tool that knows all callers, implementors, and dependencies from bytecode can emit precise, correct source edits every time. The LLM's job reduces to deciding *what* to rename/move/extract — the tool handles the *how*.

All candidates below share the same properties:
- **Deterministic**: Given input parameters, the output is fully determined — no heuristics, no AI judgment needed for the transformation itself.
- **Whole-project**: Finds and updates all affected files (call sites, imports, string references) via bytecode analysis + OpenRewrite AST.
- **Verifiable**: Compile before and after to prove correctness.

### Extract interface — `cnavExtractInterface`

**Value: high** | **Effort: high**

Extract an interface from a class, choosing which methods to include, and optionally update callers to use the interface type instead.

- `-Ptarget-class=com.example.UserService -Pinterface-name=UserOperations -Pmethods=findUsers,createUser`
- Creates: new interface file with selected method signatures.
- Updates: class declaration to add `implements`/`:` clause, optionally updates field/parameter types at call sites from concrete class to interface.
- **Why LLMs fail at this**: They create the interface but forget to handle generic type parameters, miss default method implementations, don't update callers' type declarations, and produce interfaces that don't compile due to missing imports.
- **Reuses**: `ClassDetailExtractor` (method signatures), `InterfaceRegistry`, `cnavUsages -Ptype=X` for caller type updates.

### Change method signature — `cnavChangeSignature`

**Value: medium** | **Effort: high**

Add, remove, or reorder parameters on a method, updating all call sites with default values or reordered arguments.

- `-Ptarget-class=com.example.UserService -Pmethod=findUsers -Padd-param="limit: Int = 50" -Pposition=2`
- Updates: method declaration, all call sites (adding default value for new param), named argument order.
- **Why LLMs fail at this**: They update the declaration but miss call sites in other modules, don't handle overloads correctly, and frequently break named argument ordering.
- **Reuses**: `RenameParamRewriter` visitor structure, `CallGraph` for call site discovery.

### Inline function / extract function — `cnavExtractFunction`

**Value: medium** | **Effort: very high**

Extract a code block into a new function, or inline a function's body into its call sites. Requires source-level analysis beyond what bytecode provides — needs OpenRewrite's full AST.

- More complex because it requires understanding local variable scope, control flow, and return semantics.
- **Probably not worth building**: IDEs already do this well interactively. The LLM value-add is lower here because extract/inline is usually a single-file operation where LLMs are adequate.

### Priority order

1. **Extract interface** — high value for architecture improvement workflows, but more complex.
2. **Change signature** — medium value, complex parameter manipulation.
3. **Extract function** — low priority, IDEs handle this well already.
