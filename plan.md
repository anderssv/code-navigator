# Plan

Items are ordered for sequential execution: each item can be done independently, top-to-bottom.
Value and effort are qualitative assessments to aid prioritization, not estimates.

---

## Filter Kotlin compiler annotations from output

**Value: very high** | **Effort: low** | **Bug**

From v0.1.44 field test (7 Kotlin microservice repos): `@Metadata`, `@DebugMetadata`, and `@SourceDebugExtension` annotation content leaks into output for `cnavClassDetail`, `cnavFindCallers`, `cnavFindCallees`, `cnavContext`, `cnavFindUsages`. Makes bytecode-inspection results nearly unreadable for Kotlin projects — the actual useful information is buried in walls of encoded metadata strings.

- **Root cause**: Neither `ClassDetailExtractor` nor `AnnotationExtractor` filter internal annotations. All annotations present in bytecode are included verbatim.
- **Fix**: Define a blocklist (`kotlin.Metadata`, `kotlin.jvm.internal.SourceDebugExtension`, `kotlin.coroutines.jvm.internal.DebugMetadata`) and filter at both extraction sites. Add `AnnotationName.isInternal()` predicate in a shared location (e.g. `KotlinMethodFilter` or `DomainTypes`). Note: `InlineMethodDetector` reads `@Metadata` via its own ASM visitor, so filtering in the extractors does not affect it.
- **Scope**: `ClassDetailExtractor.kt` (class/field/method annotations), `AnnotationExtractor.kt` (class/method annotations).

---

## Fix `cnavDead -Pprod-only=true` — ensure test classes are compiled

**Value: high** | **Effort: low** | **Bug**

From v0.1.44 field test: `-Pprod-only=true` has no effect — output is identical with and without the flag.

- **Root cause**: `DeadCodeTask` only builds the test call graph when test class directories physically exist at execution time. But `DEAD.requiresCompilation = true` only ensures main classes are compiled (Gradle `dependsOn("classes")`). If tests were never compiled, `testGraph` is null, every dead item gets `reason = NO_REFERENCES`, and the `prod-only` filter becomes a no-op.
- **Fix**: Add `dependsOn("testClasses")` in `DeadCodeTask` (Gradle). For Maven, change the lifecycle phase from `COMPILE` to `TEST_COMPILE` in `DeadCodeMojo`. This ensures test bytecode exists so the test call graph can be built and items can be tagged `TEST_ONLY`.
- **Alternative**: Add a `requiresTestCompilation: Boolean` field to `TaskDef` and wire it in `CodeNavigatorPlugin` alongside the existing `requiresCompilation` logic.

---

## Add `include-test` to `cnavAnnotations`

**Value: high** | **Effort: low** | **Bug**

From v0.1.44 field test: `cnavAnnotations -Ppattern=Test -Pmethods=true` returns empty despite `@Test` annotations existing in every repo's test sources.

- **Root cause**: `AnnotationsTask` (Gradle) hard-codes `sourceSets.getByName("main")` — test classes are never scanned. `AnnotationsMojo` (Maven) only scans `project.build.outputDirectory` and triggers `LifecyclePhase.COMPILE`. The `INCLUDETEST` ParamDef exists in TaskRegistry but is not wired into the `ANNOTATIONS` task.
- **Fix**: Add `INCLUDETEST` to `ANNOTATIONS.params` in TaskRegistry. Parse it in `AnnotationQueryConfig`. Add test source set directories in `AnnotationsTask.kt` and `AnnotationsMojo.kt`, following the pattern used by `FindInterfaceImplsTask`/`FindInterfaceImplsMojo`. For Maven, use `TEST_COMPILE` lifecycle phase when include-test is enabled.
- **Consider**: Default `include-test=true` for annotations, since annotation queries often target test annotations (`@Test`, `@ParameterizedTest`, `@Disabled`).

---

## Add `include-test` to `cnavFindSymbol`

**Value: medium** | **Effort: low** | **Bug**

From v0.1.44 field test: `cnavFindSymbol -Ppattern=verify` returned nothing in bass-auth despite verify methods existing. Most likely cause: the methods are in test sources.

- **Root cause**: `FindSymbolTask` hard-codes `sourceSets.getByName("main")` — test classes are never scanned. Unlike `FindCallers`/`DeadCode` which use `taggedClassDirectories()` (both main and test), `FindSymbolTask` only looks at main.
- **Fix**: Add `INCLUDETEST` to `FIND_SYMBOL.params` in TaskRegistry. Parse in `FindSymbolConfig`. Add test source set directories in `FindSymbolTask.kt` and `FindSymbolMojo.kt`.
- **Secondary cause**: Stale cache (`build/cnav/symbol-index.cache`) could serve old results. Document cache invalidation or add `--no-cache` flag.

---

## Centralize command config — auto-generate help text, plugin registration, and validation from TaskRegistry

**Value: high** | **Effort: medium** | **Status: DONE**

Task/parameter metadata is spread across 5+ locations that must stay in sync manually. Adding a parameter today requires touching TaskRegistry, Config, HelpText, Gradle task, Maven mojo, and sometimes AgentHelpText. Descriptions drift (e.g., `cnavCycles` plugin description says `-Pdepth=N` but the actual param is `dsm-depth`; `cnavDsm` description still references deprecated `root-package`). Default values are bypassed in some Config.parse() methods. Required-ness validation is ad-hoc.

All sub-tasks completed — see plan-completed.md for details.

---

## `cnavWhyDepends` — dependency edge explanation

**Value: high** | **Effort: medium**

The DSM tells you package A depends on package B, but not *why*. To break a cycle you need to know the specific fields, method parameters, return types, and local variable types that create the dependency.

- **Builder**: `DependencyExplainer.explain(callGraph, from, to) -> List<DependencyEdge(sourceClass, targetClass, kind: FIELD|PARAMETER|RETURN_TYPE|LOCAL_VAR|METHOD_CALL, detail: String)>`
- **Parameters**: `-Pfrom=<class-or-package>` (required), `-Pto=<class-or-package>` (required), `-Pproject-only=true`
- **Why useful**: The missing link between "the DSM says there's a dependency" and "here's what to move/extract to break it."

---

## Better error messages on task parameter validation

**Value: high** | **Effort: low**

From user feedback (v0.38): `cnavCallers` failed with "Missing required property" without saying which property was missing. The user passed `-Pmethod` and `-Pclass`, but the task expects `-Ppattern` for the class — the error message didn't say so.

- **Approach**: Generate usage hints from `TaskDef.params` instead of hardcoding strings. Each `ParamDef` already has `name`, `valuePlaceholder`, and `defaultValue` (null = required). The error message becomes: `"Missing required parameter 'pattern'. Usage: ./gradlew cnavCallers -Ppattern=<regex> [-Pmaxdepth=3]"` — generated from the task definition, so it can never drift.
- **Also check**: When an unknown parameter is passed (e.g., `-Pclass`), suggest the closest valid parameter name ("Did you mean `-Ppattern`?")
- **Scope**: All tasks with required parameters. Centralize in `TaskDef` or `GradleSupport.buildPropertyMap()`.

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

## `cnavDead` baseline diff — confirm cleanup was complete

**Value: medium** | **Effort: low**

After triaging dead code and removing items, re-run `cnavDead` and see what changed.

- **Approach**: `-Pbaseline=<path>` parameter pointing to a saved JSON output from a previous run. On re-run, show: items removed since baseline, items still present, new items.
- **Alternative**: Just save JSON and use `jq` to diff. Built-in support is more ergonomic but the alternative is viable.

---

## Cycle fix suggestions in DSM

**Value: high** | **Effort: medium**

The DSM tells you which cycles exist, but not how to fix them. When `-Pcycles=true`, also show which specific class-level edges would need to move to break the cycle, and suggest which direction the dependency should flow.

- **Prerequisite**: Benefits from `cnavWhyDepends` infrastructure — same edge-explanation logic.
- **Separate from DSM what-if**: What-if simulation (`-Pwhat-if=<class>:<target-package>`) is a distinct, higher-effort feature. Evaluate need after cycle fix suggestions ship.

---

## Extract ConfidenceScorer from DeadCodeFinder

**Value: medium** | **Effort: low**

`DeadCodeFinder` inlines all confidence-scoring logic (annotation checks, interface checks, method name heuristics, caller count thresholds). Extract a `ConfidenceScorer` class that takes a `DeadCode` candidate and returns its `DeadCodeConfidence` + `DeadCodeReason`.

- Makes scoring rules independently testable and easier to extend (e.g., meta-annotation traversal, Spring Data awareness).
- **Prerequisite for**: Meta-annotation traversal benefits from clean scoring separation.

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

## Full classpath scanning option

**Value: high** | **Effort: medium**

Add `-Pclasspath=true` to scan the full runtime classpath (project classes + all dependency JARs).

- **Applies to**: `cnavListClasses`, `cnavFindClass`, `cnavFindSymbol`, `cnavClass`, `cnavInterfaces`, `cnavUsages`
- **Reuses**: Classpath resolution infrastructure from `cnavJar`.
- **Considerations**: Significantly slower (thousands of classes). Combine with existing `-Ppattern` / `-Powner` filters to narrow scope. Consider caching scanned JARs by checksum.
- **Why**: AI agents frequently need to check library API signatures to write correct code.

---

## `cnavDiff` — structural diff between builds

**Value: medium** | **Effort: medium**

Compare two compiled states and show structural changes: added/removed/changed classes, methods, and dependency edges.

- **Use cases**: API signature changes from dependency upgrades; verifying a refactoring was purely structural.
- **Builder**: `StructuralDiff.diff(baselineClassDir, currentClassDir) -> List<Change(className, memberName, kind: ADDED|REMOVED|SIGNATURE_CHANGED, oldSignature?, newSignature?)>`
- **Parameters**: `-Pbaseline=<path>` (path to baseline class directory), `-Paffected=true` (also list affected call sites)

---

## Meta-annotation traversal for dead code filtering

**Value: high** | **Effort: medium**

`@RestController` is meta-annotated with `@Controller` which is meta-annotated with `@Component`. Currently, excluding `Component` does NOT exclude `@RestController`.

- **Approach**: In `AnnotationExtractor`, also scan annotation `.class` files from classpath JARs and resolve meta-annotations transitively.
- **Reuses**: Classpath resolution from `cnavJar` / full classpath scanning.
- **Why**: Covers custom stereotype annotations automatically. A project defining `@DomainService` (meta-annotated with `@Component`) would be handled without configuration.

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

## `cnavUnused` — unused build dependencies

**Value: medium** | **Effort: medium**

Find entire libraries that could be removed. For each declared dependency JAR, extract the package list. Scan project bytecode for references. Dependencies with zero references are candidates for removal.

- **Caveats**: Runtime-only dependencies (JDBC drivers, logging backends) will show as "unused." Needs an exclusion mechanism.
- **Reuses**: Classpath enumeration infrastructure from `cnavJar` / meta-annotation traversal.

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

## Transitive dead code detection

**Value: medium** | **Effort: high**

From user feedback (v0.38): `cnavDead` found nothing even for `GenericError.from(RAResponseError)` which manual analysis suggested has unused paths. Dead code detection is too conservative — it sees a method called from a companion object and marks it as live, even if the callers of those callers are shrinking or dead.

- **Approach**: After initial dead code pass, trace callers transitively. A method is "transitively dead" if all its callers are themselves dead. Iterate until fixed point.
- **Extension — shrinking usage**: Track caller count over git history. Methods where caller count is declining over time are "shrinking" — not dead yet, but trending toward dead. Different from dead code detection (point-in-time) — this is trend analysis.
- **Extension — test-only callers**: A production method whose only callers are in the test source set is effectively dead from a production perspective.
- **Confidence levels**: `DEAD` (zero callers), `TRANSITIVELY_DEAD` (all callers dead), `TEST_ONLY` (only test callers), `SHRINKING` (declining caller trend).
- **Prerequisite**: Extract ConfidenceScorer makes this cleaner to implement.

---

## Parked

Items below are low-priority or may not be worth building. Revisit if demand emerges.

- **Custom entry-point config file** (`.cnav-entry-points`): Framework presets + `exclude-annotated` + `exclude-framework` cover most cases. A config file adds marginal value over the existing parameters. Revisit if users request it.
- **DI-aware `cnavInjectors`**: Largely solvable with `cnavUsages -Ptype=X` combined with interface dispatch resolution. High effort for marginal gain.
- **Stable JSON schemas** (`cnavSchema`): JSON output is already self-describing. Agents infer schema from examples.
- **Split root package** (S9): Lower priority now that `navigation/` has been split into sub-packages. Dependency direction is already clear enough.

## Future ideas (not yet planned)

- **Audit all tasks for `include-test` support**: From v0.1.44 field test, multiple tasks only scan main source set. Systematic audit: which tasks should support `-Pinclude-test`? Candidates: `cnavFindClass`, `cnavListClasses`, `cnavClassDetail`, `cnavContext`, `cnavFindStringConstant`, `cnavTypeHierarchy`, `cnavComplexity`, `cnavRank`. Some already support it (`cnavFindInterfaces`, `cnavCallers`, `cnavCallees`, `cnavDead`).
- **Dead code: flag methods called only from test scope**: Use source set tagging to identify production methods/classes whose only callers are in the test source set. These are candidates for removal since no production code depends on them. Replaces the current separate `testGraph` approach in `DeadCodeFinder` with a unified call graph that has source set metadata.
- **Remove cnav disk cache entirely**: Zero measurable difference on ~20k LOC. Reduces complexity. Needs testing on larger projects.
- **Fail fast on wrong bytecode**: Replace `ScanResult<T>` partial-fail with hard failure + clear error.
- **Cross-reference hotspots with bytecode**: Combine `cnavHotspots` with `cnavCallers`/`cnavDeps`.
- **Entity ownership / main developer**: Who "owns" each file by contribution weight. Mode on `cnavAuthors`.
- **Architectural-level grouping**: Aggregate file-level results by logical component/layer.
- **Source-level structural analysis**: Analyze imports from source files without requiring compilation.
