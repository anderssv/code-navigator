# Plan

Items are ordered for sequential execution: each item can be done independently, top-to-bottom.
Value and effort are qualitative assessments to aid prioritization, not estimates.

---

## `cnavDead` baseline diff — confirm cleanup was complete

**Value: medium** | **Effort: low**

After triaging dead code and removing items, re-run `cnavDead` and see what changed.

- **Approach**: `-Pbaseline=<path>` parameter pointing to a saved JSON output from a previous run. On re-run, show: items removed since baseline, items still present, new items.
- **Alternative**: Just save JSON and use `jq` to diff. Built-in support is more ergonomic but the alternative is viable.

---

## Extract ConfidenceScorer from DeadCodeFinder

**Value: medium** | **Effort: low**

`DeadCodeFinder` inlines all confidence-scoring logic (annotation checks, interface checks, method name heuristics, caller count thresholds). Extract a `ConfidenceScorer` class that takes a `DeadCode` candidate and returns its `DeadCodeConfidence` + `DeadCodeReason`.

- Makes scoring rules independently testable and easier to extend (e.g., meta-annotation traversal, Spring Data awareness).
- **Prerequisite for**: Meta-annotation traversal and transitive dead code detection benefit from clean scoring separation.

---

## `cnavWhyDepends` — dependency edge explanation

**Value: high** | **Effort: medium**

The DSM tells you package A depends on package B, but not *why*. To break a cycle you need to know the specific fields, method parameters, return types, and local variable types that create the dependency.

- **Builder**: `DependencyExplainer.explain(callGraph, from, to) -> List<DependencyEdge(sourceClass, targetClass, kind: FIELD|PARAMETER|RETURN_TYPE|LOCAL_VAR|METHOD_CALL, detail: String)>`
- **Parameters**: `-Pfrom=<class-or-package>` (required), `-Pto=<class-or-package>` (required), `-Pproject-only=true`
- **Why useful**: The missing link between "the DSM says there's a dependency" and "here's what to move/extract to break it."

---

## Cycle fix suggestions in DSM

**Value: high** | **Effort: medium**

The DSM tells you which cycles exist, but not how to fix them. When `-Pcycles=true`, also show which specific class-level edges would need to move to break the cycle, and suggest which direction the dependency should flow.

- **Prerequisite**: Benefits from `cnavWhyDepends` infrastructure — same edge-explanation logic.
- **Separate from DSM what-if**: What-if simulation (`-Pwhat-if=<class>:<target-package>`) is a distinct, higher-effort feature. Evaluate need after cycle fix suggestions ship.

---

## `cnavRisk` — composite risk analysis

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

---

## `cnavTestHealth` — verify all test methods actually ran

**Value: high** | **Effort: medium**

From user feedback: a project had 19 silently skipped tests because test methods had non-`Unit` return types. Count `@Test`-annotated methods from bytecode, compare against JUnit XML results, flag the delta.

1. **Bytecode scan**: Find all methods annotated with `@Test` (JUnit 4/5, Kotlin Test). This is the "expected" set.
2. **JUnit XML scan**: Parse test result XML files (`build/test-results/test/TEST-*.xml` or `target/surefire-reports/TEST-*.xml`). This is the "actual" set.
3. **Diff**: Report methods present in bytecode but absent from XML results — the silently skipped tests.

- **Lifecycle**: `dependsOn("test")` — runs after tests complete
- **Additional checks** (bytecode-only): test methods missing `@Test` annotation but named `test*`, test classes with no `@Test` methods, `@Disabled`/`@Ignore` inventory
- Both Gradle and Maven write the same JUnit XML format, so one parser handles both.

---

## `cnavJar` — inspect library class signatures

**Value: high** | **Effort: medium**

Inspect the methods and signatures of classes inside a JAR file, whether or not the JAR is on the project classpath.

```bash
./gradlew cnavJar -Partifact=com.fasterxml.jackson.core:jackson-databind -Ppattern=ObjectMapper
./gradlew cnavJar -Pjar=/path/to/some.jar -Ppattern=SomeClass
```

- **Two modes**: `-Partifact=<group:name>` (resolve from runtime classpath) or `-Pjar=<path>` (arbitrary JAR)
- **Implementation**: Reuse `ClassDetailExtractor` / `ClassDetailScanner` but feed entries from a `JarFile`. For `-Partifact`, resolve via Gradle's `configurations.runtimeClasspath.resolvedConfiguration` / Maven's `project.runtimeClasspathElements`.
- **Why**: AI agents frequently need to check library API signatures. Bytecode gives ground-truth for the exact version in the project.
- **Note**: This builds classpath resolution infrastructure reused by full classpath scanning and meta-annotation traversal.

---

## Meta-annotation traversal for dead code filtering

**Value: high** | **Effort: medium**

`@RestController` is meta-annotated with `@Controller` which is meta-annotated with `@Component`. Currently, excluding `Component` does NOT exclude `@RestController`.

- **Approach**: In `AnnotationExtractor`, also scan annotation `.class` files from classpath JARs and resolve meta-annotations transitively.
- **Reuses**: Classpath resolution from `cnavJar` / full classpath scanning.
- **Why**: Covers custom stereotype annotations automatically. A project defining `@DomainService` (meta-annotated with `@Component`) would be handled without configuration.
- **Prerequisite**: `cnavJar` (classpath resolution infrastructure). Benefits from `Extract ConfidenceScorer`.

---

## Full classpath scanning option

**Value: high** | **Effort: medium**

Add `-Pclasspath=true` to scan the full runtime classpath (project classes + all dependency JARs).

- **Applies to**: `cnavListClasses`, `cnavFindClass`, `cnavFindSymbol`, `cnavClass`, `cnavInterfaces`, `cnavUsages`
- **Reuses**: Classpath resolution infrastructure from `cnavJar`.
- **Considerations**: Significantly slower (thousands of classes). Combine with existing `-Ppattern` / `-Powner` filters to narrow scope. Consider caching scanned JARs by checksum.
- **Why**: AI agents frequently need to check library API signatures to write correct code.

---

## `cnavLayerCheck` — architecture conformance

**Value: high** | **Effort: ambitious**

Declare layer rules and validate them against the actual call graph. Like ArchUnit but without writing test code.

```kotlin
codeNavigator {
    rules {
        "services" mustNotDependOn "ra"
        "domain" mustNotDependOn "ktor"
    }
}
```

- Output: list of violations with the specific class-level edges that break the rule.
- **Prerequisite**: Benefits from `cnavWhyDepends` for edge explanation.

---

## Split JsonFormatter and LlmFormatter per-feature

**Value: medium** | **Effort: medium**

Self-analysis found `JsonFormatter` (217 outgoing dependencies, 47 referenced types) and `LlmFormatter` (177 outgoing, 46 types) are god classes. They change together 96% of the time.

- **Approach**: Split into per-feature formatters (e.g., `CallTreeJsonFormatter`, `DeadCodeJsonFormatter`). Top-level formatters become thin dispatchers.
- **Ordering**: `LlmFormatter` first (primary agent-facing format), then `JsonFormatter`. `TableFormatter` is smaller and can follow later.
- **Benefits**: Adding a new feature means adding a new formatter file, not editing a shared god class.

---

## `cnavReport` — consolidated full analysis

**Value: medium** | **Effort: low**

Run all analysis tasks and produce a single consolidated report. `cnavMetrics` already exists for a summary snapshot; `cnavReport` runs everything and outputs all results in one pass.

- **Parameters**: Inherits from constituent tasks. `-Pformat=json` produces a single JSON object with sections per analysis.
- **Why useful**: Agents often want the full picture. A single task is faster (shared caching, one compilation) and produces a coherent snapshot.

---

## `cnavDiff` — structural diff between builds

**Value: medium** | **Effort: medium**

Compare two compiled states and show structural changes: added/removed/changed classes, methods, and dependency edges.

- **Use cases**: API signature changes from dependency upgrades; verifying a refactoring was purely structural.
- **Builder**: `StructuralDiff.diff(baselineClassDir, currentClassDir) -> List<Change(className, memberName, kind: ADDED|REMOVED|SIGNATURE_CHANGED, oldSignature?, newSignature?)>`
- **Parameters**: `-Pbaseline=<path>` (path to baseline class directory), `-Paffected=true` (also list affected call sites)

---

## `cnavUnused` — unused build dependencies

**Value: medium** | **Effort: medium**

Find entire libraries that could be removed. For each declared dependency JAR, extract the package list. Scan project bytecode for references. Dependencies with zero references are candidates for removal.

- **Caveats**: Runtime-only dependencies (JDBC drivers, logging backends) will show as "unused." Needs an exclusion mechanism.
- **Reuses**: Classpath enumeration infrastructure from `cnavJar` / meta-annotation traversal.

---

## Transitive dead code detection

**Value: medium** | **Effort: high**

From user feedback (v0.38): `cnavDead` found nothing even for `GenericError.from(RAResponseError)` which manual analysis suggested has unused paths. Dead code detection is too conservative — it sees a method called from a companion object and marks it as live, even if the callers of those callers are shrinking or dead.

- **Approach**: After initial dead code pass, trace callers transitively. A method is "transitively dead" if all its callers are themselves dead. Iterate until fixed point.
- **Extension — shrinking usage**: Track caller count over git history. Methods where caller count is declining over time are "shrinking" — not dead yet, but trending toward dead. Different from dead code detection (point-in-time) — this is trend analysis.
- **Extension — test-only callers**: A production method whose only callers are in the test source set is effectively dead from a production perspective.
- **Confidence levels**: `DEAD` (zero callers), `TRANSITIVELY_DEAD` (all callers dead), `TEST_ONLY` (only test callers), `SHRINKING` (declining caller trend).
- **Prerequisite**: Extract ConfidenceScorer makes this cleaner to implement.

---

## Structured cache format

**Value: medium** | **Effort: medium**

`FileCache` subclasses serialize as tab-separated positional fields. Adding a field requires updating both `serialize()` and `deserialize()` and any field order mismatch silently corrupts data.

- **Approach**: Replace with a self-describing format that tolerates field additions without breaking existing caches.
- **Note**: Consider removing cache entirely — benchmarking on ~20k LOC / 488-class project showed zero measurable difference. Needs testing on larger projects.

---

## Gradle incremental task support

**Value: medium** | **Effort: high**

Support Gradle's incremental task API (`@InputFiles`, `@OutputFile`, `InputChanges`) to skip unchanged files. Call graph analysis is inherently whole-program, so incremental support is most beneficial for leaf tasks (`cnavListClasses`, `cnavFindSymbol`, `cnavFindClass`).

---

## DSM what-if simulation

**Value: medium** | **Effort: high**

`-Pwhat-if=<class>:<target-package>` — simulate moving a class to a different package and re-evaluate cycles without actually making the change.

- **Prerequisite**: Cycle fix suggestions should ship first.

---

## Abstractness per package — `[Balanced Coupling]`

**Value: medium** | **Effort: low**

Capture `ACC_ABSTRACT` and `ACC_INTERFACE` flags from bytecode during scanning. Compute per-package abstractness ratio: `A = (abstract classes + interfaces) / total classes`.

- **Where**: Extend `DsmDependencyExtractor` (or `ClassInfoExtractor`) to record whether each class is abstract/interface.
- **Output**: Per-package abstractness as a value between 0.0 (all concrete) and 1.0 (all abstract/interface).
- **Why**: Abstractness is a prerequisite for integration strength classification and the balanced coupling heuristic. Also independently useful for Robert C. Martin package metrics (instability/abstractness).

---

## Integration strength classification — `[Balanced Coupling]`

**Value: high** | **Effort: medium**

Classify each inter-package dependency edge by the type of knowledge shared. Balanced Coupling defines four levels (from weakest to strongest):

1. **Contract coupling**: dependency is against an interface or abstract type only.
2. **Model coupling**: dependency is against a shared domain model (concrete type, but stable).
3. **Functional coupling**: dependency involves calling behavior/logic, not just referencing types.
4. **Intrusive coupling**: dependency accesses internal/private implementation details.

- **Where**: Extend `DsmDependencyExtractor` to tag each `PackageDependency` with a strength level. ASM provides access flags (`ACC_INTERFACE`, `ACC_ABSTRACT`, `ACC_PUBLIC`) and the dependency kind (field type vs. method call vs. type cast) to approximate this classification.
- **Heuristic**: Target is interface/abstract → contract. Target is concrete type used as field/parameter/return → model. Target is concrete type with method calls → functional. Target accessed via reflection or non-public members → intrusive.
- **Prerequisite**: Abstractness per package (for detecting interface/abstract targets).

---

## Volatility per package — `[Balanced Coupling]`

**Value: high** | **Effort: low-medium**

Aggregate file-level git metrics (change frequency, churn) to the package level. Produces a per-package volatility score.

- **Where**: New `PackageVolatilityBuilder` that maps file paths from `HotspotBuilder`/`ChurnBuilder` output to package names (using source set root + directory structure).
- **Output**: `PackageVolatility(packageName, revisions, totalChurn, fileCount, avgRevisionsPerFile)` sorted by descending volatility.
- **Parameters**: `-Psince=<git-ref>` (time window), `-Ptop=N`.
- **Why**: Volatility is one of the three dimensions of Balanced Coupling. High-volatility packages require loose coupling; low-volatility packages can tolerate tight coupling without harm.

---

## Structural distance between packages — `[Balanced Coupling]`

**Value: medium** | **Effort: low**

Compute the distance between coupled packages. Distance represents how far knowledge must travel and how much coordination is needed to implement a change.

- **Structural distance**: Package hierarchy hops between two coupled packages (e.g., `com.app.order` → `com.app.order.validation` = 1 hop, `com.app.order` → `com.app.infra.db` = 3 hops). Computable directly from package names.
- **Module distance** (stretch goal): Whether packages live in the same Gradle subproject / Maven module or different ones. Requires multi-module project awareness.
- **Where**: Utility function on top of `DsmMatrix` package pairs.
- **Output**: Distance value per package-pair dependency edge.

---

## `cnavBalance` — balanced coupling analysis — `[Balanced Coupling]`

**Value: high** | **Effort: medium**

The composite Balanced Coupling heuristic. For each package pair, evaluate the three dimensions and flag imbalances:

```
modularity = strength XOR distance
balance    = modularity OR NOT volatility
```

- **High strength + high distance + high volatility** → danger zone: tightly coupled across boundaries in fast-changing code. Suggest reducing distance (co-locate) or reducing strength (introduce contract/interface).
- **Low strength + low distance + low volatility** → over-engineering: unnecessary indirection in stable, loosely coupled code. Suggest simplifying or merging.
- **Balanced combinations** → no action needed.

- **Inputs**: Integration strength (from classification), distance (from structural distance), volatility (from package volatility).
- **Output**: Ranked list of package pairs by imbalance severity, with per-pair breakdown of all three dimensions and actionable suggestions.
- **Parameters**: `-PpackageFilter=<prefix>`, `-Pformat=llm|json|text`, `-Ptop=N`.
- **Prerequisites**: Integration strength classification, structural distance, volatility per package.

---

## Per-package health dashboard — `[Balanced Coupling]`

**Value: medium** | **Effort: medium**

Aggregate all per-package metrics into a single view: abstractness, volatility, coupling strength breakdown, distance profile, cycle involvement, and balance assessment.

- **Where**: New builder that combines outputs from `DsmMatrixBuilder`, abstractness, volatility, and the balance heuristic.
- **Output**: One row per package with columns for each metric dimension. Highlight packages that are imbalanced.
- **Why**: Currently `MetricsBuilder` only produces project-level aggregates. Per-package breakdown is needed to act on Balanced Coupling findings.
- **Could be**: A mode of `cnavMetrics` (`-Pby-package=true`) or a separate `cnavPackageHealth` task.
- **Prerequisites**: Abstractness per package, volatility per package, `cnavBalance`.

---

## Parked

Items below are low-priority or may not be worth building. Revisit if demand emerges.

- **Custom entry-point config file** (`.cnav-entry-points`): Framework presets + `exclude-annotated` + `treat-as-dead` cover most cases. A config file adds marginal value over the existing parameters. Revisit if users request it.
- **DI-aware `cnavInjectors`**: Largely solvable with `cnavUsages -Ptype=X` combined with interface dispatch resolution. High effort for marginal gain.
- **Stable JSON schemas** (`cnavSchema`): JSON output is already self-describing. Agents infer schema from examples.
- **Split root package** (S9): Lower priority now that `navigation/` has been split into sub-packages. Dependency direction is already clear enough.

## Future ideas (not yet planned)

- **`cnavFindCallees` callee explosion**: `CallTreeBuilder` expands ALL polymorphic implementors as separate children with no collapsing. Default maxdepth=3 causes >51KB output for methods touching deep hierarchies. Root cause: `resolveInterfaceDispatch` adds every implementor, no deduplication of identical subtrees, no output truncation anywhere. Solutions: collapse dispatch groups into a single "N implementors" node with expand-on-demand, add a max-children limit per node, lower default depth for callees. v0.1.47 field test.
- **`cnavFindCallers` class-match UX hint**: `CallGraph.findMethods()` uses `Regex.containsMatchIn` on `qualifiedName` (className.methodName). Pattern "Parser" matches every method in `Parser` class, producing separate caller trees per method. User expected "who references Parser as a type" which is `cnavFindUsages -Ptype=Parser`. Fix: when pattern matches only class-name portions of multiple methods in the same class, add a hint suggesting `cnavFindUsages -Ptype=`. v0.1.47 field test.
- **Improve `cnavAnnotations` discoverability**: Field test (v0.1.44) reported "no inverse annotation search" but the feature exists — `cnavAnnotations -Ppattern=Serializable` finds all classes with that annotation. The task name is ambiguous. Consider a task alias (`cnavFindByAnnotation`), better no-results guidance mentioning retention policy / `methods` flags, or more prominent placement in `cnavAgentHelp`. v0.1.45 re-test clarified: `cnavAnnotations` only finds RUNTIME and CLASS retention annotations present in bytecode. SOURCE retention annotations (e.g. `@Suppress`) are invisible — this is inherent to bytecode analysis, but should be documented in no-results guidance. v0.1.46: no-results hint now suggests `-Pmethods=true` and retention policy (bug #8 FIXED). Remaining: task alias and retention policy documentation in help text.
- **`cnavDead -Pprod-only` no-visible-effect guidance**: v0.1.46 field test showed `-Pprod-only=true` had no effect on TAC (146→146) because dead items are test classes tagged `NO_REFERENCES`. This is now tracked as a top-level plan item ("Dead code: test source classes should be tagged TEST_ONLY"). Separate improvement: when `prod-only` is set and filtering has no effect, add a note to output explaining why.
- **Remove `junit` from `FrameworkPresets` or document it's a no-op for `cnavDead`**: v0.1.45 analysis suite reported `-Ptreat-as-dead=junit` has no observable effect. Root cause: dead code analysis scans both source sets by default now, but JUnit annotations on test classes are typically excluded from dead code results because test classes are considered live (they have callers from the test runner). The junit preset may still be useful for edge cases. Options: (a) remove `junit` from presets (it's misleading), (b) document in help that `treat-as-dead` only applies to production-class annotations, (c) keep as-is.
- **`cnavChangedSince` parameter naming**: v0.1.45 analysis suite noted `-Pref=<git-ref>` is unintuitive — users expect `-Psince=<date>`. Low priority, but a `-Psince` alias or date-to-ref conversion would improve ergonomics.
- **Kotlin name-mangled method display**: Methods like `validateAndParse-IoAF18A` are Kotlin inline class return types. Low priority but a note in output (e.g. `[inline-class-mangled]`) would reduce confusion.
- **Dead code: flag methods called only from test scope**: Use source set tagging to identify production methods/classes whose only callers are in the test source set. These are candidates for removal since no production code depends on them. Replaces the current separate `testGraph` approach in `DeadCodeFinder` with a unified call graph that has source set metadata.
- **Remove cnav disk cache entirely**: Zero measurable difference on ~20k LOC. Reduces complexity. Needs testing on larger projects.
- **Fail fast on wrong bytecode**: Replace `ScanResult<T>` partial-fail with hard failure + clear error.
- **Cross-reference hotspots with bytecode**: Combine `cnavHotspots` with `cnavCallers`/`cnavDeps`.
- **Entity ownership / main developer**: Who "owns" each file by contribution weight. Mode on `cnavAuthors`.
- **Architectural-level grouping**: Aggregate file-level results by logical component/layer.
- **Source-level structural analysis**: Analyze imports from source files without requiring compilation.
