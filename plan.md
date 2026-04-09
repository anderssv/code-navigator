# Plan

Items are ordered for sequential execution: each item can be done independently, top-to-bottom.
Value and effort are qualitative assessments to aid prioritization, not estimates.

---

## Test suite health: coverage, speed, and duplication

**Value: high** | **Effort: medium**

The test suite is growing and builds are slow. Investigate and improve:

- **Measure coverage**: Identify which code paths lack tests and which have redundant coverage.
- **Measure speed**: Find the slowest tests and root-cause them (e.g., repeated Gradle compilation in rewriter tests, unnecessary full-project copies).
- ~~**Reduce duplication**: Simplify test data setup — extract shared helpers, use object mothers with `.copy()` for variations, avoid recreating identical fixtures across test classes.~~ **DONE** — see `plan-completed.md`.
- **Align with kotlin-tdd**: Adopt TestContext/fakes patterns, extension functions on companion objects for test data, and Testing Through The Domain where applicable.

---

## Filter non-source files from git analysis recommendations

**Value: medium** | **Effort: low**

Coupling and hotspot recommendations flag build/config/doc files (e.g., `gradle-wrapper.properties`, `deployment/configmap.yaml`, `pom.xml`, `README.md`) with advice meant for source code ("unclear responsibilities", "consider merging"). These are noise — config files co-changing is expected, and build files don't have "responsibilities" in the code sense.

- **Approach**: Add an optional source-file filter at the builder level. Default to files matching `src/**` (or configurable pattern). Non-source files still appear in results but don't get recommendation annotations.
- **Affects**: `ChangeCouplingFormatter`, `HotspotFormatter`.
- **Note**: Test+main pairs in coupling are already suppressed (v0.1.52).

---

## Add interpretation section to all analysis task output

**Value: high** | **Effort: medium**

All analysis tasks should include a short (2-4 sentences) interpretation section in their output to help LLMs pick up the right context for results. Show to both humans and LLMs.

- Each task formatter appends a brief "Interpretation" block explaining what the results mean and how to act on them.
- Keeps agents from misreading output (e.g., treating 0-caller framework entry points as dead code, or confusing stripped package prefixes with actual package names).

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

## Introduce query/config objects for complex finders

**Value: medium** | **Effort: low**

`DeadCodeFinder.find()` has 18 parameters. Other finders have similarly long signatures. When a method takes that many parameters, it signals a missing domain object.

- **Approach**: Introduce a `DeadCodeQuery` (or reuse/extend `DeadCodeConfig`) that bundles all parameters into a single typed object. Apply the same pattern to other finders with complex signatures.
- **Benefits**: Makes the API self-documenting, simplifies testing (build query objects with sensible defaults and `.copy()` for variations), and makes parameter additions non-breaking.
- **Ordering**: Natural companion to `Extract ConfidenceScorer` — both simplify `DeadCodeFinder` internals.

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

## Revise peerLimit and testInfrastructure expressiveness

**Value: medium** | **Effort: low**

The current `peerLimit` and `testInfrastructure` attributes work but may not be the most intuitive way to express the intent. Evaluate whether there's a better way to express:

- **Peer dependencies**: `peerLimit = 0` (forbidden), `peerLimit = 3` (max 3 per class), `peerLimit = -1` (unlimited). Is per-class counting the right granularity? Should there be a layer-level total?
- **Test infrastructure exemptions**: `testInfrastructure: true` on a layer lets test classes depend on it. Is there a more general "exemption" model?
- **Layer groups**: The old package-based plan had "peer groups" (same-index arrays). The pattern-based model uses layer ordering only. Is there demand for peer groups?

Low priority — the current model works. Revisit when real-world usage reveals friction.

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

## Per-package health dashboard — `[Balanced Coupling]`

**Value: medium** | **Effort: medium**

Aggregate all per-package metrics into a single view: volatility, coupling strength breakdown, distance profile, cycle involvement, and balance assessment.

- **Where**: New builder that combines outputs from `DsmMatrixBuilder`, volatility, and the balance heuristic.
- **Output**: One row per package with columns for each metric dimension. Highlight packages that are imbalanced.
- **Why**: Currently `MetricsBuilder` only produces project-level aggregates. Per-package breakdown is needed to act on Balanced Coupling findings.
- **Could be**: A mode of `cnavMetrics` (`-Pby-package=true`) or a separate `cnavPackageHealth` task.
- **Prerequisites**: Volatility per package, `cnavBalance`.

---

## Extract shared orchestration from Gradle tasks and Maven mojos

**Value: medium** | **Effort: low**

`IntegrationStrengthTask.kt` and `IntegrationStrengthMojo.kt` duplicate ~17 lines of identical orchestration logic (extract → classify → format → output). `PackageDistanceTask.kt` and `PackageDistanceMojo.kt` duplicate ~20 lines. The only differences are config preamble (reading Gradle `-P` properties vs Maven `@Parameter` fields), report file path, and logging mechanism.

- **Approach**: Extract shared orchestration functions (e.g., `runStrengthAnalysis(config, classDirs, logger) -> String`) into core. Gradle tasks and Maven mojos become thin wrappers that build the config from their respective property mechanisms and delegate to the shared function.
- **Scope**: Start with Distance and Strength (smallest, most recently touched). If the pattern works well, apply to other task/mojo pairs.
- **Benefits**: Bug fixes apply once. New features (e.g., a new output format) only need one code path.

---

## Evaluated and rejected: CLI-first architecture

Shipping the core as a standalone CLI was considered to eliminate Gradle/Maven duplication. **Rejected** because Gradle and Maven plugins provide frictionless installation (`plugins { id("...") }` / `<plugin>`), automatic version management, transitive dependency resolution, and zero separate install. A CLI would require separate installation, manual upgrades, and JVM version coordination -- a significant DX regression.

The shared orchestration extraction (above) achieves most of the deduplication benefit while keeping the build-tool distribution advantage.

---

## Make `DsmDependencyExtractor.packageFilter` nullable

**Value: low** | **Effort: low**

Four task/mojo files pass `PackageName("")` to `DsmDependencyExtractor.extractFromClassWithProjectFilter()` as a workaround for "no filter." The downstream APIs (`filterByPackage`, `StrengthClassifier.classify`) already accept `PackageName?` with null semantics. The extractor's `packageFilter` parameter should be `PackageName?` with null meaning "no filter."

- **Approach**: Change `extractFromClassWithProjectFilter(packageFilter: PackageName)` to `PackageName?`. Update callers to pass `null` instead of `PackageName("")`. Update internal logic to skip `startsWith` check when null.
- **Benefits**: Eliminates a magic value. Makes the API self-documenting.

---

## Lazy JAR scanning for external class classification

**Value: medium** | **Effort: medium**

When `include-external=true`, `ClassTypeCollector` only scans project class directories. External library classes aren't in the `classTypeRegistry`, so `classifyTarget` returns null and they're counted as `unknown`. The current workaround (FIX 5) tracks these as `unknownCount` and defaults all-unknown pairs to CONTRACT strength.

- **Approach**: When a target class is not in the registry, lazily resolve its `.class` file from the runtime classpath JARs and classify it. Only scan the specific classes that appear in dependencies, not entire JARs.
- **Reuses**: Classpath resolution infrastructure from the planned `cnavJar` feature. Consider doing `cnavJar` first.
- **Benefits**: Eliminates `unknownCount` entirely. Strength classifications for external dependencies become accurate instead of defaulting to CONTRACT.
- **Trade-off**: Adds JAR I/O during classification. Mitigate with a per-run cache of resolved classes.

---

## Parked

Items below are low-priority or may not be worth building. Revisit if demand emerges.

- **Abstractness per package** `[Balanced Coupling]`: Per-package ratio of abstract/interface classes to total classes (Robert C. Martin's `A` metric). Not needed for the Balanced Coupling pipeline — integration strength classification only needs per-class abstract/interface flags, not a package-level aggregate. Add as a standalone metric if demand emerges.
- **Custom entry-point config file** (`.cnav-entry-points`): Framework presets + `exclude-annotated` + `treat-as-dead` cover most cases. A config file adds marginal value over the existing parameters. Revisit if users request it.
- **DI-aware `cnavInjectors`**: Largely solvable with `cnavUsages -Ptype=X` combined with interface dispatch resolution. High effort for marginal gain.
- **Stable JSON schemas** (`cnavSchema`): JSON output is already self-describing. Agents infer schema from examples.
- **Split root package** (S9): Lower priority now that `navigation/` has been split into sub-packages. Dependency direction is already clear enough.

## Future ideas (not yet planned)

- **Scope decision: tool vs. platform**: With 35 tasks today and 20+ planned, code-navigator is approaching "code intelligence platform" territory. This is fine, but worth an explicit decision: stay as a focused collection of tasks, or invest in extensibility infrastructure (plugin system, composable analysis pipelines, third-party task registration)? The answer affects architectural choices going forward. If staying focused, the current `TaskRegistry` approach scales well enough. If building for extensibility, consider a plugin API where tasks are discovered rather than registered.
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
- **Source-level structural analysis**: Analyze imports from source files without requiring compilation. `cnavSize` (DONE) is the first source-level task; import/dependency analysis from source would be the next step.
- **Deterministic refactorings**: See dedicated section below.

---

## Future deterministic refactorings for LLMs

`cnavRenameParam` (DONE), `cnavRenameMethod` (DONE — v0.1.55), and `cnavMoveClass` (DONE — v0.1.56) are deterministic refactorings using OpenRewrite for AST-based source transformation. The key insight: LLMs are unreliable at multi-file refactorings because they guess at call sites, miss named arguments, forget string templates, and hallucinate file paths. A tool that knows all callers, implementors, and dependencies from bytecode can emit precise, correct source edits every time. The LLM's job reduces to deciding *what* to rename/move/extract — the tool handles the *how*.

All candidates below share the same properties:
- **Deterministic**: Given input parameters, the output is fully determined — no heuristics, no AI judgment needed for the transformation itself.
- **Whole-project**: Finds and updates all affected files (call sites, imports, string references) via bytecode analysis + OpenRewrite AST.
- **Verifiable**: Compile before and after to prove correctness.
- **Known gaps addressed**: ~~Companion object methods~~ (DONE — v0.1.58), ~~constructor parameter warnings~~ (DONE — v0.1.58), ~~Maven mojo support~~ (DONE — all three tasks have mojos), ~~full constructor `val`/`var` property rename~~ (DONE — `cnavRenameProperty`).

### ~~Rename class~~ — via `cnavMoveClass` DONE

DONE — Added optional `-Pnew-name` parameter to `cnavMoveClass`. Class renaming is handled by OpenRewrite's `ChangeType` recipe. Can move, rename, or both in a single operation.

### ~~Move class to different package~~ — `cnavMoveClass` DONE

See `plan-completed.md`.

### Extract interface — `cnavExtractInterface`

**Value: high** | **Effort: high**

Extract an interface from a class, choosing which methods to include, and optionally update callers to use the interface type instead.

- `-Ptarget-class=com.example.UserService -Pinterface-name=UserOperations -Pmethods=findUsers,createUser`
- Creates: new interface file with selected method signatures.
- Updates: class declaration to add `implements`/`:` clause, optionally updates field/parameter types at call sites from concrete class to interface.
- **Why LLMs fail at this**: They create the interface but forget to handle generic type parameters, miss default method implementations, don't update callers' type declarations, and produce interfaces that don't compile due to missing imports.
- **Reuses**: `ClassDetailExtractor` (method signatures), `InterfaceRegistry`, `cnavUsages -Ptype=X` for caller type updates.

### Inline function / extract function — `cnavExtractFunction`

**Value: medium** | **Effort: very high**

Extract a code block into a new function, or inline a function's body into its call sites. Requires source-level analysis beyond what bytecode provides — needs OpenRewrite's full AST.

- More complex because it requires understanding local variable scope, control flow, and return semantics.
- **Probably not worth building**: IDEs already do this well interactively. The LLM value-add is lower here because extract/inline is usually a single-file operation where LLMs are adequate.

### Change method signature — `cnavChangeSignature`

**Value: medium** | **Effort: high**

Add, remove, or reorder parameters on a method, updating all call sites with default values or reordered arguments.

- `-Ptarget-class=com.example.UserService -Pmethod=findUsers -Padd-param="limit: Int = 50" -Pposition=2`
- Updates: method declaration, all call sites (adding default value for new param), named argument order.
- **Why LLMs fail at this**: They update the declaration but miss call sites in other modules, don't handle overloads correctly, and frequently break named argument ordering.
- **Reuses**: `RenameParamRewriter` visitor structure, `CallGraph` for call site discovery.

### Priority order

1. ~~**Rename method**~~ — DONE (v0.1.55)
2. ~~**Rename class**~~ — DONE (via `cnavMoveClass` `-Pnew-name`)
3. ~~**Move class**~~ — DONE (v0.1.56)
4. **Extract interface** — high value for architecture improvement workflows, but more complex.
5. **Change signature** — medium value, complex parameter manipulation.
6. **Extract function** — low priority, IDEs handle this well already.
