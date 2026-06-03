# Plan

Items grouped by functional area. Each item has:
- **Status**: ACTIVE (next up) / FUTURE (someday) / PARKED (low priority, revisit if demand) / REJECTED
- **Source**: internal / field-test(project) / user-feedback(version)

---

## Bugs

### cnavSafeDelete crashes with JSON parse error
**PARKED** | **Value: high** | **Effort: low** | Source: field-test(bass-ra, v0.1.97)

Not reproducible in v0.1.102. May have been fixed as a side-effect of other changes. Reopen if it recurs.

### cnavChangeSignature can't find suspend functions
**REJECTED** | **Value: medium** | **Effort: medium** | Source: field-test(bass-ra, v0.1.97)

Already works — test exists and passes. Likely was a field-test environment issue.

### cnavMoveFile produces no output
~~**ACTIVE**~~ **DONE (v0.1.102)** | **Value: low** | **Effort: low** | Source: field-test(bass-ra, v0.1.97)

Fixed: error handling added so failures produce proper CNAV_BEGIN/CNAV_END output.

### cnavRenameProperty inconsistent resolution
~~**ACTIVE**~~ **DONE (v0.1.102)** | **Value: medium** | **Effort: high** | Source: field-test(bass-ra, v0.1.97)

Fixed: constructor params without `val/var` that initialize body properties are now renamed correctly (both the param name and the initializer reference).

### ~~cnavDead false positive on extension functions~~ — DONE (v0.1.102)
~~**ACTIVE**~~ **DONE (v0.1.102)** | **Value: medium** | **Effort: low** | Source: field-test(bass-ra, v0.1.97)

Fixed: `*Kt` facade classes excluded from class-level dead code detection entirely (`DeadCodeFinder.kt:203`). Method-level dead code on facades is still reported. `ConfidenceScorer` cleaned up.

---

## Cycle & dependency analysis

### Cycle actionability — fix suggestions, edge ranking, and direction clarity
~~**ACTIVE**~~ **DONE (v0.1.103)** | **Value: high** | **Effort: high** | Source: field-test(bass-ra+greitt)

Implemented:
1. **Edge direction + counts**: Per-edge ref counts shown in both TEXT and LLM formats.
2. **Edge ranking — "which edge to break first"**: `CycleBreakAnalyzer` computes break-score (edge removal splits/shrinks SCC) and ranks by weight. Top 3 weakest links shown.
3. **Fix suggestions**: Weakest links section tells user which edges to target.

Remaining (deferred to future iteration):
- Ring degeneration guidance (identify easiest-to-extract package in giant cycles)
- Test-only edge flagging in cycle output

### Test-source separation — exclude test edges from structural analysis
~~**ACTIVE**~~ **DONE (already implemented)** | **Value: high** | **Effort: low** | Source: field-test(greitt+bass-ra)

All structural tasks (cnavDsm, cnavCycles, cnavBalance, cnavRings) already support `--scope=prod` which filters class directories by source set. Verified on greitt: 90→0 ring violations, 1→0 cycles when excluding test sources. No code change needed.

### DSM what-if simulation (`cnavSimulateMove`)
~~**ACTIVE**~~ **DONE (v0.1.103)** | **Value: medium** | **Effort: medium** | Source: field-test(bass-ra)

Implemented. Predicts cycle impact of moving a class to a different package without modifying code:
- `cnavSimulateMove --type=Cache --to-package=no.example.ra --scope=prod`
- Mutates dependency graph in memory, re-runs cycle detection, diffs before/after
- Shows removed/added cycles. Validated on bass-ra-backend.

### `cnavFileDeps` — file-level dependency tree
**FUTURE** | **Value: medium** | **Effort: medium** | Source: internal

Current dependency analysis works at package granularity (`cnavPackageDeps`, `cnavDsm`) or method/symbol granularity (`cnavFindCallers`, `cnavFindUsages`). There is no file-level view, which is the granularity humans and agents actually navigate at.

- **Builder**: Aggregate class-level bytecode references up to `UsageSite.sourceFile`.
- **Single-file mode**: `--file=OrderService.kt` — direct deps. `--reverse=true` — files that depend on this one. `--depth=N` — transitive tree.
- **All-files mode**: `--all=true` — full file-dep graph (fan-in/fan-out ranking, orphan detection, file-level cycle detection).
- **Alternative framing**: Extend `cnavDsm` with `--granularity=file|package|class` instead of a separate task.

### Class-level ring detection for `cnavRings`
~~**ACTIVE**~~ **DONE (v0.1.103-SNAPSHOT)** | **Value: high** | **Effort: high** | Source: field-test(greitt, v0.1.102)

Implemented `--mode=emergent` which classifies each class into a ring by import shape (framework imports = adapter, SCC collapse for cycles, longest-path for layering). Shows mixed-ring package summaries. Ring detection is by dependency shape only, never naming conventions.

**Remaining follow-ups** (not blocking, separate items):
- Structural mode improvement (detect ring subpackages by shape)
- Intra-package ring violations (domain class depending on adapter within same package)
- Actionable guidance (suggest port extraction)

### Structural ring mode improvement
**FUTURE** | **Value: medium** | **Effort: medium** | Source: field-test(greitt, v0.1.102)

Improve the default `--mode=package` to detect ring subpackages by their dependency shape (not naming). A subpackage is Ring 0 if it has no framework deps, Ring 2 if it imports I/O — regardless of what it's called. Validates that detected ring boundaries are respected.

### Intra-package ring violations for emergent mode
**FUTURE** | **Value: medium** | **Effort: medium** | Source: field-test(greitt, v0.1.102)

In emergent mode, report violations where a domain class (ring 0) within a package depends on an adapter class (ring 2) — upward dependencies within a package. Currently SCC collapse makes this hard to detect (cycles hide direction). May need pre-SCC edge analysis.

### High violation count warning for `cnavRings`
**PARKED** | **Value: low** | **Effort: low** | Source: internal

When violation count exceeds a threshold (>50), add a note: "High violation count may indicate the project doesn't follow concentric ring architecture. Consider `--scope=prod` or `cnavLayerCheck` with explicit layer config."

---

## Refactoring operations

### cnavMovePackage — batch move all classes in a package
**ACTIVE** | **Value: high** | **Effort: low** | Source: field-test(bass-ra)

`cnavMovePackage --from=no.example.domain.ra --to=no.example.ra` — iterates cnavMoveClass for each class in the package. Infrastructure exists; this is orchestration.

### cnavMoveFunction — move top-level Kotlin functions
**FUTURE** | **Value: medium** | **Effort: high** | Source: field-test(bass-ra)

Move top-level functions (e.g. `objectMapper()` from `di/Serialization.kt` to `config/Serialization.kt`). Top-level functions in Kotlin compile to `*Kt` facade classes, so bytecode-level tracking works, but the refactoring operation needs to handle the source differently (no class declaration to move, just function bodies).

### PSI-based refactoring operations
**FUTURE** | **Value: high** | **Effort: varies** | Source: internal

Now that `cnavRenameMethod` has been migrated to PSI (v0.1.90), the compiler integration barrier is gone — we have a working two-phase architecture (ASM location finding → PSI editing in isolated classloader) that new operations can reuse.

Inspired by [Martin](https://github.com/audunstrand/martin) by Audun Fauchald Strand.

**Effort key (post-PSI migration):**
- **Low** = PSI tree walking + text replacement, no type resolution needed.
- **Medium** = Needs bytecode-guided location finding or cross-file coordination.
- **High** = Needs `BindingContext` / full type resolution (not yet implemented).

**Recommended implementation order (quick wins first):**
1. `cnavConvertToExpressionBody` / `cnavConvertToBlockBody` — trivial, validates pattern (low)
2. `cnavExtractVariable` / `cnavExtractConstant` — single-file (low)
3. `cnavSafeDelete` — leverages existing dead code detection (low)
4. `cnavConvertToDataClass` — single-file with PSI validation (low)
5. `cnavChangeSignature` — reuses RenameLocationFinder, high value for agents (medium)
6. `cnavExtractParameter` — combines single-file + cross-file (medium)
7. `cnavExtractInterface` — medium, high value for architecture improvements

**Full catalog:**

Extract operations:
- `cnavExtractFunction` (high — needs BindingContext)
- `cnavExtractVariable` (low)
- `cnavExtractConstant` (low)
- `cnavExtractParameter` (medium)
- `cnavExtractInterface` (medium)
- `cnavExtractSuperclass` (medium)

Inline / simplify:
- `cnavInline` (medium)
- `cnavConvertToExpressionBody` / `cnavConvertToBlockBody` (low)

Signature & structure:
- `cnavChangeSignature` (medium)
- `cnavAddNamedArguments` (medium)
- `cnavIntroduceParameterObject` (medium)
- `cnavPullUpMethod` (medium)
- `cnavReplaceConstructorWithFactory` (medium)

Type conversions:
- `cnavConvertToDataClass` (low)
- `cnavConvertToExtensionFunction` (medium)
- `cnavConvertToSealedClass` (medium)
- `cnavTypeMigration` (high — needs BindingContext)
- `cnavConvertPropertyToFunction` (medium)

Safety:
- `cnavSafeDelete` (low)
- `cnavEncapsulateField` (medium)

**Notes:**
- All "medium" operations follow the two-phase pattern proven by `cnavRenameMethod`: ASM finds locations → PSI edits.
- "High" operations blocked on BindingContext. Consider Martin delegation until we add support.

---

## Dead code & metrics

### Meta-annotation traversal for dead code filtering
**ACTIVE** | **Value: medium** | **Effort: high** | Source: internal

`@RestController` is meta-annotated with `@Controller` which is meta-annotated with `@Component`. Currently, excluding `Component` does NOT exclude `@RestController`.

- In `AnnotationExtractor`, scan annotation `.class` files from classpath JARs and resolve meta-annotations transitively.
- Covers custom stereotype annotations automatically.
- **Prerequisite**: `cnavJar` (classpath resolution infrastructure) — non-trivial to resolve annotation classes from JARs.

### Transitive dead code detection
**FUTURE** | **Value: medium** | **Effort: high** | Source: user-feedback(v0.38)

A method is "transitively dead" if all its callers are themselves dead. Iterate until fixed point.
- Confidence levels: `DEAD` (zero callers), `TRANSITIVELY_DEAD` (all callers dead), `TEST_ONLY` (only test callers), `SHRINKING` (declining caller trend).

### `cnavDead` baseline diff — confirm cleanup was complete
**FUTURE** | **Value: low** | **Effort: low** | Source: internal

`--baseline=<path>` parameter pointing to saved JSON output. On re-run, show diff. Alternative: just use `jq` to diff JSON externally.

### Dead code: flag methods called only from test scope
**FUTURE** | **Value: medium** | **Effort: low** | Source: internal

Use source set tagging (already available) to identify production methods/classes whose only callers are in the test source set.

### `cnavRisk` — composite risk analysis
**FUTURE** | **Value: medium** | **Effort: low** | Source: user-feedback(v0.38)

`risk = change_frequency * complexity * coupling_degree`. Combines existing builders (Hotspot, Churn, Complexity) into ranked output. Low effort since all inputs exist — just composition and sorting.

### Per-package health dashboard
**FUTURE** | **Value: low** | **Effort: medium** | Source: internal

Aggregate all per-package metrics into a single view: volatility, coupling strength breakdown, distance profile, cycle involvement, balance assessment. Could be a mode of `cnavMetrics` (`--by-package=true`) or separate `cnavPackageHealth` task.

---

## Standalone new tasks

### `cnavTestHealth` — verify all test methods actually ran
**ACTIVE** | **Value: medium** | **Effort: medium** | Source: user-feedback

Count `@Test`-annotated methods from bytecode, compare against JUnit XML results, flag the delta. Catches silently skipped tests (e.g., non-`Unit` return types).

### `cnavTestCoverage` — per-test-class coverage proximity analysis
**FUTURE** | **Value: low** | **Effort: high** | Source: internal

Identify production classes only tested "at distance". Requires JaCoCo integration (TestExecutionListener + per-test exec files). High effort for specialized use case.

### `cnavDiff` — structural diff between builds
**FUTURE** | **Value: medium** | **Effort: medium** | Source: internal

Compare two compiled states: added/removed/changed classes, methods, dependency edges. `--baseline=<path>`, `--affected=true`.

### `cnavUnused` — unused build dependencies
**FUTURE** | **Value: medium** | **Effort: medium** | Source: internal

For each declared dependency JAR, extract package list. Scan project bytecode for references. Dependencies with zero references are candidates for removal. Caveat: runtime-only deps (JDBC drivers, logging backends) need exclusion mechanism.

---

## TDD practice enforcement

### `cnavFakeCoverage` — verify all ports have fakes
**FUTURE** | **Value: medium** | **Effort: low** | Source: internal

Scan interfaces matching a pattern (`*Repository`, `*Client`), check each has at least one implementation in test source set. InterfaceRegistry + test source filter — infrastructure exists.

### `cnavTestCoupling` — remaining improvements
**PARKED** | **Value: medium** | **Effort: low** | Source: field-test(greitt+terms-and-conditions)

- **DAO test threshold**: adapter tests where port calls are <50% due to assertion noise. Consider counting only non-framework calls in denominator.
- **Concise "all clear" output**: When no violations found, one-liner confirmation instead of ~15 lines of guidance.

### `cnavContextUsage` — verify consistent test context usage
**FUTURE** | **Value: low** | **Effort: medium** | Source: internal

Check that test classes use a shared test context rather than constructing dependencies ad-hoc.

### `cnavInterfacePurity` — check interfaces use domain types
**FUTURE** | **Value: low** | **Effort: medium** | Source: internal

For interfaces matching a pattern, check method signatures reference only domain-package types (not DTO/infrastructure types).

---

## Output & UX

### ~~Per-task help with usage on error~~ — DONE
~~**ACTIVE**~~ **DONE** | **Value: medium** | **Effort: low** | Source: field-test(bass-ra, v0.1.97)

All refactoring tasks (Gradle + Maven) now catch `IllegalArgumentException` on config parse and show `usageHint()` + `renderExamples()` instead of a raw stack trace.

### `TaskGuidance` — structured context output for all tasks
**ACTIVE** | **Value: medium** | **Effort: high** | Source: internal

When an LLM runs a task without context parameters, include guidance explaining what the task checks, how to determine the right parameter values, and how to read results.

```kotlin
data class TaskGuidance(
    val purpose: String,
    val parameterGuidance: String,
    val interpretation: String,
)
```

Replaces scattered `*_INTERPRETATION` constants with single source of truth per task. Reused in LLM output header, AgentHelpText, and no-params fallback.

### Refactoring result LLM hints for follow-up actions
**ACTIVE** | **Value: medium** | **Effort: low** | Source: internal

After successful refactoring, include contextual hints suggesting further analysis (e.g., after `cnavMoveClass` → suggest `cnavPackageDeps` to verify improvement).

### ~~Refactoring task discoverability~~ — DONE
~~**ACTIVE**~~ **DONE** | **Value: low** | **Effort: low** | Source: field-test(bass-ra, v0.1.72)

Covered: `generateCompact()` has "When Refactoring" block (line 150) + "Common Refactoring Tasks" section + explicit `"I'm about to rename/move/delete → move-class/rename-method --preview"` hint in the Exploring section.

### `cnavAnnotations`: methods=true as default for common annotations
**ACTIVE** | **Value: low** | **Effort: low** | Source: field-test(bass-ra, v0.1.97)

Searching for `@Test` returns empty without `--methods=true`. Workaround is easy (pass the flag). Auto-enable when results are empty.

### `cnavFindCallees`: hide library internals by default
**ACTIVE** | **Value: medium** | **Effort: medium** | Source: field-test(bass-ra, v0.1.97)

Output noisy for methods calling Java library code. Collapse or hide library-internal methods by default (`--project-only`).

### `cnavFindCallees` callee explosion
**ACTIVE** | **Value: medium** | **Effort: medium** | Source: field-test(v0.1.47)

`CallTreeBuilder` expands ALL polymorphic implementors. Solutions: collapse dispatch groups into "N implementors" node, add max-children limit, lower default depth.

### Preview-by-default for write commands
**FUTURE** | **Value: medium** | **Effort: low** | Source: field-test(bass-ra)

Safer default: preview mode on by default, explicit `--apply=true` to commit. Breaking change — needs deprecation cycle.

### Consistent `--project-only` support across all tasks
**FUTURE** | **Value: medium** | **Effort: low** | Source: user-feedback

Audit all tasks for `--project-only`/`--scope` support. Add where missing.

### `cnavMoveSuggest`: hexagonal architecture awareness
**FUTURE** | **Value: low** | **Effort: high** | Source: field-test(bass-ra, v0.1.97)

Suggests moving ports into domain packages. Algorithm optimizes for proximity without understanding ring boundaries. Could use `cnavRings` to suppress suggestions violating ring boundaries.

### `cnavMoveSuggest`: label/exclude test classes
**FUTURE** | **Value: low** | **Effort: low** | Source: field-test(greitt)

Suggestions like "move `MenuItemTest` to `web.components`" are confusing. Default to excluding test classes.

### `cnavRings`: external protocol classes misclassified
**FUTURE** | **Value: low** | **Effort: low** | Source: field-test(bass-ra, v0.1.97)

External protocol Java classes placed in Ring 0. Fix: filter classes in packages not matching project root package.

### `cnavBalance` volatility values lack context
**PARKED** | **Value: low** | **Effort: low** | Source: internal(v0.1.83)

Raw volatility numbers meaningless without scale. Show percentile rank or relative to project mean.

### Suppress root-package deprecation warning when auto-detection matches
**PARKED** | **Value: low** | **Effort: low** | Source: internal

If configured `rootPackage` matches auto-detection, suppress warning.

### `cnavChangedSince` parameter naming
**PARKED** | **Value: low** | **Effort: low** | Source: field-test(v0.1.45)

`--ref=<git-ref>` is unintuitive. Consider `--since` alias or date-to-ref conversion.

### Kotlin name-mangled method display
**PARKED** | **Value: low** | **Effort: low** | Source: internal

Methods like `validateAndParse-IoAF18A` — add `[inline-class-mangled]` note in output.

### Improve `cnavAnnotations` discoverability
**PARKED** | **Value: low** | **Effort: low** | Source: field-test(v0.1.44)

Task alias (`cnavFindByAnnotation`), retention policy documentation in help text. No-results hint for `-Pmethods=true` already done (v0.1.46).

### `cnavDead --scope=prod` no-visible-effect guidance
**PARKED** | **Value: low** | **Effort: low** | Source: internal(v0.1.45)

When `scope=prod` filtering has no effect, explain why.

### Remove `junit` from `FrameworkPresets` or document it's a no-op
**PARKED** | **Value: low** | **Effort: low** | Source: internal(v0.1.45)

`--treat-as-dead=junit` has no observable effect since test classes are considered live.

---

## Find-usages output quality
**FUTURE** | **Value: medium** | **Effort: medium** | Source: field-test(v0.1.72)

A single logical call site produces 3-4 lines (`.new` + `.<init>` + `.checkcast` + field access). Lambda classes obscure actual callers. Need collapsing/deduplication and smart summary mode.

---

## Classpath / JAR scanning

### ~~Maven: `--jar` support for Mojos~~ — DONE
~~**ACTIVE**~~ **DONE** | **Value: medium** | **Effort: low** | Source: internal

~~Add `@Parameter(property = "jar")` to `ListClassesMojo`, `FindClassMojo`, `ClassDetailMojo`, `FindSymbolMojo`.~~

Already implemented: all four mojos have `@Parameter(property = "jar")` and full jar branch logic.

### Full classpath scanning option
**FUTURE** | **Value: medium** | **Effort: medium** | Source: internal

`--classpath=true` to scan full runtime classpath. Applies to list/find/class/interfaces/usages. AI agents frequently need library API signatures.

---

## CI & enforcement

### CI fail-on-violation mode
**ACTIVE** | **Value: high** | **Effort: low** | Source: internal

Allow `cnavLayerCheck`, `cnavCycles`, `cnavCohesion` to fail the build when violations exceed threshold. `--fail-on-violation=true`, `--max-cycles=0`.

---

## Behavioral + structural fusion

### Port volatility lockstep detector
**FUTURE** | **Value: medium** | **Effort: medium** | Source: internal

Cross-reference interfaces with volatility. If port interface changes as frequently as its adapter, the abstraction isn't stable.

### `cnavChangedSince` → layered impact predictor
**FUTURE** | **Value: medium** | **Effort: high** | Source: internal

Expand from single-hop blast radius into multi-signal impact predictor: direct callers → transitive callers → interface implementor callers → historically co-changed → same-package peers. Each with confidence tier.

---

## Internal code quality

### Review implementations for spread logic + shared lookup extraction
**ACTIVE** | **Value: medium** | **Effort: high** | Source: internal

**Spread logic**: Several tasks span multiple concerns. Principle: orchestrator calls single-purpose tasks.
- `RenameMethodRewriter`/`RenameMethodEditor`: location finding + PSI editing separation
- `MoveClassRewriter`: are import updating, content extraction, file writing reusable?
- Formatter classes: some contain query logic belonging in builders

**Shared lookup**: Class resolution and method finding duplicated across ChangeSignature, RenameMethod, RenameProperty, SafeDelete. Extract shared `DeclarationFinder`. Design: stream-like — general lookup first, then filtered down.

### Split JsonFormatter and LlmFormatter per-feature
**ACTIVE** | **Value: medium** | **Effort: high** | Source: internal(v0.1.83)

`JsonFormatter` (364 outgoing, 77 types, fanIn=261) and `LlmFormatter` are highest-complexity classes. Change together 96% of the time. Split into per-feature formatters; top-level becomes thin dispatcher. Wide change touching many files.

### Reduce Gradle/Maven duplication via orchestrator extraction
**ACTIVE** | **Value: medium** | **Effort: high** | Source: internal

Every Gradle/Maven pair duplicates orchestration. Three pairs already have orchestrators. Extend to rest (~590 lines duplicated across 14 pairs). Tedious but mechanical.

### Potentially dead code in cnav's own codebase
**PARKED** | **Value: medium** | **Effort: low** | Source: internal

Self-analysis found: `CallGraphCache.build()`, `ClassIndexCache.build()`, `InterfaceRegistryCache.build()`, `SymbolIndexCache.build()`, `UsageScanner.scan()`, various `FileCache` methods. Investigate if truly dead or called via dispatch.

### Test suite health
**ACTIVE** | **Value: medium** | **Effort: medium** | Source: internal

- **Cache KotlinParser in rewriter tests** — 71% of test time in 5 classes. Sharing parsed AST could cut from 10.4s to ~3-4s.
- **Add `FieldExtractor` tests** — 0% coverage, only non-Gradle core class at zero.
- **Cover `LlmFormatter`/`JsonFormatter` uncovered branches** — primary agent-facing formatters at ~80%.

### Break `formatting` ↔ `navigation.relations` cycle
**PARKED** | **Value: medium** | **Effort: low** | Source: internal

Move `UsageFormatterTest` to `formatting` test package.

### Move misplaced root-package test classes
**PARKED** | **Value: low** | **Effort: low** | Source: internal

`ClassFileStalenessTest`, `TaskDefTest`, `TaskRegistryTest` etc. belong in sub-packages.

### Fix DANGER balance: root package → callgraph/implementors
**PARKED** | **Value: medium** | **Effort: low** | Source: internal(v0.1.83)

Root package references concrete callgraph/implementor types. May resolve when misplaced test classes are moved.

### Evaluate other JVM languages to support
**PARKED** | **Value: medium** | **Effort: low (research)** | Source: internal

Groovy and Scala support via `LanguageRenameRewriter`. Is PSI available? Is the language common enough?

---

## Infrastructure

### Structured cache format
**PARKED** | **Value: medium** | **Effort: medium** | Source: internal

Replace tab-separated positional fields with self-describing format. Consider removing cache entirely — zero measurable difference on ~20k LOC projects.

### Gradle incremental task support
**FUTURE** | **Value: medium** | **Effort: high** | Source: internal

`@InputFiles`/`@OutputFile`/`InputChanges`. Most beneficial for leaf tasks (`cnavListClasses`, `cnavFindSymbol`).

---

## Layer check

### Revise peerLimit and testInfrastructure expressiveness
**PARKED** | **Value: medium** | **Effort: low** | Source: internal

Current model works. Revisit when real-world usage reveals friction.

---

## Martin integration & compiler infrastructure

### ~~Embedded Kotlin Compiler Frontend~~ — DONE (v0.1.90)

Two-phase architecture: ASM location finding → PSI editing in isolated classloader. `kotlin-compiler-embeddable:2.0.21`. Remaining: BindingContext not yet used.

### Martin as external tool integration
**FUTURE** | **Value: medium** | **Effort: high** | Source: internal

Delegate to Martin's daemon for extract/inline/change-signature operations. Unified UX, no compiler dependency bloat. Risk: external dependency on Martin maintenance. Effort higher than it looks (daemon lifecycle, error handling, version compat).

### TextEdit as universal edit primitive
**PARKED** | **Value: low** | **Effort: low** | Source: internal

Martin's `SourceRewriter` (43 lines). We already have PSI — marginal added value.

### Daemon mode with warm compiler environment
**PARKED** | **Value: low** | **Effort: medium** | Source: internal

Gradle daemon already provides JVM reuse. Marginal benefit.

---

## Architectural exploration (loose thoughts — not to be acted upon)

### Hexagonal architecture analysis of cnav itself
**PARKED** | Source: internal

Port/adapter boundaries:
- Input: command parsing (properties → config objects)
- Output: printing formats (TEXT/LLM/JSON)
- Bytecode: interfacing with compiled classes
- Core: analysis logic on data classes

Questions: right port abstractions? Bytecode inside/outside core? Relation to existing Config→Builder→Formatter separation?

### Scope decision: tool vs. platform
**PARKED** | Source: internal

40 tasks today, 20+ planned. Stay focused (TaskRegistry scales) or invest in extensibility (plugin system, composable pipelines)?

---

## Evaluated and rejected

- **CLI-first architecture** — REJECTED. Build-tool plugins provide frictionless installation, version management, transitive deps. CLI would require separate install + JVM coordination. Shared orchestration extraction achieves dedup without DX regression.
- **Classpath discovery via Gradle init script** — NOT NEEDED. We already run inside Gradle.

---

## Parked future ideas

- **Cross-reference hotspots with bytecode**: Combine `cnavHotspots` with `cnavCallers`/`cnavDeps`. (internal)
- **Entity ownership / main developer**: Who "owns" each file by contribution weight. Mode on `cnavAuthors`. (internal)
- **Architectural-level grouping**: Aggregate file-level results by logical component/layer. (internal)
- **Source-level structural analysis**: Analyze imports from source without compilation. (internal)
- **Fail fast on wrong bytecode**: Replace `ScanResult<T>` partial-fail with hard failure. (internal)
- **Remove cnav disk cache entirely**: Zero measurable difference on ~20k LOC. Needs testing on larger projects. (internal)
- **Abstractness per package** (`A` metric): Not needed for Balanced Coupling pipeline. Add as standalone if demand emerges. (internal)
- **Custom entry-point config file** (`.cnav-entry-points`): Framework presets cover most cases. (internal)
- **DI-aware `cnavInjectors`**: Solvable with `cnavUsages -Ptype=X` + interface dispatch. (internal)
- **Stable JSON schemas** (`cnavSchema`): JSON is self-describing. Agents infer from examples. (internal)

---

## ~~Completed~~ (see plan-completed.md)

- ~~cnavTypeAffinity~~ — DONE (v0.1.102)
- ~~@Option migration~~ — DONE (v0.1.99)
- ~~Embedded Kotlin Compiler Frontend~~ — DONE (v0.1.90)
- ~~`cnavFindCallers` class-match UX hint~~ — DONE (v0.1.80)
