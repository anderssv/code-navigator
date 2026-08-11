# Plan

Items grouped by functional area. Each item has:
- **Status**: ACTIVE (next up) / FUTURE (someday) / LOW (deprioritized but still on the backlog, ahead of PARKED) / PARKED (low priority, revisit if demand) / REJECTED
- **Source**: internal / field-test(project) / user-feedback(version)

---

## Bugs

### `cnavMovePackage`/`cnavExecutePlan`: per-class errors silently swallowed when total changes is zero
~~**ACTIVE**~~ **DONE (v0.1.114-SNAPSHOT)** | **Value: high** | **Effort: low** | Source: field-test(bass-self-service, [PR #1461](https://github.com/techcloud0/bass-self-service/pull/1461))

**ROOT CAUSE**: two separate bugs stacked. (1) `ExecutePlanFormatter`'s TEXT/LLM/JSON output never rendered `MoveClassResult.error` at all — only `warnings` had a rendering path. A step that failed with a real, actionable `error` (e.g. `moveClass`'s multi-class-file guard: *"also declares: Valid, Invalid. Use cnavMoveFile or cnavMoveClass --from-file"*) produced zero changes and its error vanished, indistinguishable from a step that legitimately needed no changes. (2) `MovePackageMojo`/`MovePackageTask`/`ExecutePlanMojo`/`ExecutePlanTask` all bailed early with a generic `"No changes needed"` message whenever `planResult.totalChanges == 0`, without checking whether any step actually carried an `error` — for a package containing a single sealed class with nested objects (`ValidationResult` + nested `Valid`/`Invalid`), `extractDeclaredClassNames` correctly treats the nested types as sibling declarations (by design, tested at `MoveClassRewriterTest.kt:437`), routes to the multi-class guard, produces a real diagnostic error — and the mojo discarded it entirely, printing `{"results":[],"message":"No changes needed"}` with zero indication a real problem was found.

**Fix**: `ExecutePlanResult.allErrors` (new, mirrors `allWarnings`) collects non-null `error` fields across all steps. All three formatters (TEXT/LLM/JSON) now render a step's `error` inline (`"  ERROR: ..."` / `" — ERROR: ..."` / `"error"` JSON field) instead of silently treating it as an empty-changes no-op. The four task/mojo call sites' bail condition changed from `totalChanges == 0` to `totalChanges == 0 && allErrors.isEmpty()` — a batch with any real error now always falls through to the full formatted output (with the error visible) rather than the generic "no changes" message.

**Related field observations from the same PR, assessed but not independently actionable**:
- *"Sequential `cnavMovePackage` calls left bytecode stale between steps, causing 2 of 6 classes to report 0 files moved without being physically relocated"* — the "0 files, not physically moved" half of this is the existing `withZeroChangeWarning` mechanism (already surfaced via `warnings`, which — unlike `error` — were already rendered before this fix); the underlying "stale bytecode across sequential mojo invocations" is a real, inherent limitation of `cnavMovePackage`'s two-stage design (package membership resolved from compiled bytecode at each invocation's start, then rewriting works from source) — not a regression, and not something a formatting fix addresses. No automatic incremental-recompile-between-steps mechanism exists. Workaround: recompile between sequential `cnavMovePackage` invocations that depend on each other's output, or use a single batched request where possible.
- *"Moved file kept a self-import pointing at a sibling still in its old package"* — investigated against current code and not reproduced as an independent bug: `addMissingImportsForSiblings` already exists specifically to fix up the *moved* file's own imports for former-same-package siblings it references (tested at `MoveClassRewriterTest.kt:523`). The reported dangling import is best explained as a **downstream symptom of the sequential-move staleness issue above**, not a separate gap — the rewriter generated an import assuming a sibling's co-move would succeed, and that sibling's move silently failed (see above), leaving the generated import pointing at a package the sibling was never actually relocated to.

### `cnavMoveFile` crashes with OpenRewrite `JavaTemplate` parse error, plus OOM-prone
~~**ACTIVE**~~ **FIXED (already in source)** | **Value: high** | **Effort: medium** | Source: field-test(ra-backend, v0.1.113-SNAPSHOT)

Fixed by `8322d71` — MoveClassRewriter was fully migrated off OpenRewrite to a PSI + `KotlinTypeReferenceRewriter` implementation, removing OpenRewrite entirely. The `JavaTemplate`/`ChangeType` crash path no longer exists. The Metaspace pressure was also a symptom of OpenRewrite's classloader footprint, which is now gone.

### `cnavRenameMethod` misses interface declaration when targeting an `Impl` class
~~**ACTIVE**~~ **NOT REPRODUCED** | **Value: high** | **Effort: medium** | Source: field-test(ra-backend, v0.1.113-SNAPSHOT)

Tested via `RenameMethodRewriterTest` — `renaming a method on an Impl also renames the interface and sibling implementors` (line 346) passes: renaming `RaClientImpl.getInfo` correctly renames the `RaClient` interface and `RaClientFake` in the same file. `RenameLocationFinder.findOverrideFamily` correctly walks up to the interface and down to all declarers, and those FQNs are passed as `implementorFqns` to `KotlinRenameMethodRewriter`. The ra-backend instance may have been caused by the compiled classes not being on the classpath (the `classesRoots` param defaulting to empty), which disables the override-family lookup entirely. Closing; if it recurs on a real project, check that `--classpath` / compiled classes are provided.

### `cnavMovePackage` leaves source file in original package when it can't be physically moved
~~**ACTIVE**~~ **FIXED (already in source)** | **Value: high** | **Effort: medium** | Source: field-test(ra-backend, v0.1.113-SNAPSHOT)

Fixed via two subsequent commits: `3f53015` (detect visibility-modified classes that weren't being located) and `59dfa26` (hard-stop on destination collision instead of silent overwrite). `withZeroChangeWarning` also already surfaces the warning when a class produces zero changes. All MoveClass tests pass.

### `cnavMoveClass` creates destination file but does not delete source
~~**ACTIVE**~~ **NOT REPRODUCED** | **Value: high** | **Effort: low** | Source: field-test(greitt, v0.1.113-SNAPSHOT)

Marked as not reproduced: `applyChanges` uses `Files.move(src, dst, REPLACE_EXISTING)` which atomically moves (deleting the source), and a test at `MoveClassRewriterTest.kt:174` explicitly asserts `!oldFile.exists()`. The "leftover" untracked file observed in the session was likely the pre-existing `polls/model/UserPollsService.kt` overwritten by `REPLACE_EXISTING` — not the source file. The destination-collision guard (`destinationCollisionError`, returns error + empty changes when destination exists) is also in place. Closing; reopen with a reproducible test case if it recurs.

### `-q`/`--quiet` Gradle flag silently suppresses ALL cnav output
~~**ACTIVE**~~ **DONE (v0.1.113-SNAPSHOT)** | **Value: high** | **Effort: low** | Source: field-test(ra-backend, v0.1.112)

All ~111 `logger.lifecycle(...)` result-emitting calls across Gradle tasks changed to `logger.quiet(...)`. Maven mojos were unaffected (already used `println`). Verified empirically on ra-backend: `cnavListClasses --format=llm -q` now returns all 392 classes.

### `--filter-synthetic` (default `true`) hid real call sites when the caller is a DSL lambda body
~~**ACTIVE**~~ **DONE (v0.1.113-SNAPSHOT)** | **Value: high** | **Effort: medium** | Source: field-test(ra-backend, v0.1.112)

**ROOT CAUSE**: not a `*Kt`-facade indexing bug as first suspected — `CallGraphBuilder` correctly recorded every edge, including calls to top-level functions. The actual bug: `KotlinMethodFilter.isGenerated()`'s lambda regex (`Regex("""\$lambda\$""")`) matched on substring alone, with no distinction between truly synthetic lambda-adapter/bridge methods and real lambda **body** methods that ARE the call site (e.g. the body of `route("/api/v1") { requireCorrelationId { ... } }`, compiler-named `registerV1Routes$lambda$0$0`). `--filter-synthetic` defaults to `true` and fed this check into both `cnavFindCallers` and `cnavFindUsages`, silently dropping the only caller and producing `(no callers)`/empty results.

**Diagnosis chain**: ruled out basic static-to-static `*Kt` indexing via synthetic `CallGraphBuilderTest` cases (kept as regression coverage) → pointed `CallGraphBuilder` directly at ra-backend's real compiled classes, found the caller immediately via `callersOf()` → confirmed `--filter-synthetic=false` revealed the full real chain, isolating the filter as the cause.

**Fix**: `KotlinMethodFilter.isGenerated(methodName, treatLambdaBodyAsGenerated: Boolean = true)` — new parameter, default preserves all 12 other existing call sites' behavior unchanged (dead code, complexity, symbol/annotation extraction, etc. still treat lambda methods as noise by default). Only the two call-site-position filters were changed to pass `false`:
- `CallGraphConfig.buildFilter(graph, direction: CallDirection = CallDirection.CALLERS)` — now direction-aware: resolving CALLERS never treats `$lambda$` as generated (real call sites), resolving CALLEES keeps the old behavior (lambda-named callees are usually genuine adapter noise). `CallTreeOrchestrator` and `ContextOrchestrator` (which builds separate CALLERS/CALLEES trees) updated to pass the real direction through — previously both called `buildFilter(graph)` with no direction, silently defaulting to the callee-side behavior for both directions.
- `FindUsagesConfig.filterSyntheticCallers` — `callerMethod` is always in the caller role for a usage site, so it now always passes `treatLambdaBodyAsGenerated = false`.

**Verified end-to-end on ra-backend with default settings** (no `--filter-synthetic=false` needed anymore):
- `cnavFindCallers --pattern=requireCorrelationId` now shows `SetupKt.registerV1Routes$lambda$0$0 → requireCorrelationId` in the tree, plus one level further up (`setupRoutes$lambda$0$0 → registerV1Routes`).
- `cnavFindUsages --type=CorrelationIdInterceptorKt` now shows `SetupKt.registerV1Routes -> CorrelationIdInterceptorKt method-call`.

**Impact was broader than the original "*Kt facade" framing** — it affected any call made from inside any lambda (DSL blocks, route builders, test setup blocks), not just top-level function calls.

### `cnavDead`: false positive on Kotlin `const val` holder objects (compiler-inlined, no bytecode references survive)
~~**ACTIVE**~~ **DONE (v0.1.113-SNAPSHOT)** | **Value: medium** | **Effort: medium** | Source: user-feedback([GitHub #1](https://github.com/anderssv/code-navigator/issues/1))

**ROOT CAUSE**: Kotlin inlines `const val` references as literal values at every call site (`TemplateKeys.LANG` compiles to the plain string literal `"lang"` in the caller's bytecode), so no `GETSTATIC`-style edge ever points back to the declaring class/object — a purely bytecode-based analyzer has nothing to find even when the holder is referenced dozens of times in source. `DeadCodeFinder` flagged such classes `HIGH` confidence `NO_REFERENCES`, which is misleading since bytecode fundamentally cannot verify liveness here (unlike a genuinely dead class, where absence of references is real evidence).

**Fix**: `ConstValHolderDetector` (new, mirrors the existing `InlineMethodDetector` pattern for the same class of problem — inline functions leave no call edges either) parses the `@kotlin.Metadata` annotation via `kotlin-metadata-jvm` and flags a class as a "const val holder" when it declares one or more `const val` properties and no functions. `ConfidenceScorer.score()` downgrades such classes from `HIGH` to `LOW` confidence instead of excluding them outright — the class is still surfaced (so a genuinely dead const-val holder can still be found and removed), but no longer misrepresented as a confident deletion candidate. `DeadCodeFormatter`'s user-facing note now lists "const val inlining" alongside "reflection, serialization, generated code" as a known limitation.

**Wiring**: `DeadCodeOrchestrator` scans `constValHolders` via `ConstValHolderDetector.scanAll(classDirectories)` and threads it through `DeadCodeQuery`/`DeadCodeFinder.find()` to `ConfidenceScorer.score()`, same shape as `inlineMethods`/`delegationMethods`/`bridgeMethods`.

**Tests**: `ConstValHolderDetectorTest` (new, mirrors `InlineMethodDetectorTest`, real compiled fixtures in `ConstValFixtures.kt` — pure holder, nested holder, mixed holder+function, no-const-vals-at-all), `ConfidenceScorerTest` (two new cases: const-val-holder class downgrades to LOW, downgrade does not apply to methods), `DeadCodeFinderTest` (two new end-to-end cases), `LlmFormatterTest` updated for the new NOTE text.

---

## Multi-module support

### ~~Full workspace analysis — automatically include real project dependencies~~ — DONE (v0.1.113-SNAPSHOT)
**DONE** | **Value: high** | **Effort: high** | Source: internal

Module discovery is automatic input resolution, not a task parameter — no `--multi-module` flag, no per-task `ParamDef`. Invoking a task on a leaf analyzes that project plus real transitive project dependencies; invoking on an aggregator/root analyzes its whole source subtree. Unrelated sibling/hierarchy modules are excluded.

Core types in `navigation.types.AnalysisWorkspace.kt` retain hierarchy and dependency relationships independently:
- `AnalysisWorkspace(modules, classpath, moduleAware)` — one module for ordinary analysis, the included module tree/DAG otherwise.
- `ModuleNode(id, role, parentId, dependencies, classDirectories, sourceDirectories)` — role is SOURCE or DEPENDENCY relative to the invocation; `parentId` models hierarchy, `dependencies` models the build DAG.
- `TaggedClassDirectory`/`TaggedSourceDirectory` — provenance survives flattening. `WorkspaceClassIndex.modulesOfClass()` indexes class→module(s) as `Map<ClassName, Set<String>>` (not a single module) so a duplicate FQCN across modules is preserved rather than silently overwritten.

`AnalysisWorkspaceResolver` (Gradle) always resolves the workspace via `MultiModuleResolver.classify()`, which classifies every project in the build relative to the invoked project into three roles: **SOURCE** (the invoked project's own transitive subtree — a leaf invocation is just that project, a root/aggregator invocation is the whole subtree collapsed into one scope), **DEPENDENCY** (reachable via a real `project(":x")` edge from something in SOURCE, walked transitively), or **HIERARCHY** (structurally related — parent, sibling — but not an actual dependency; excluded by default). Inclusion never requires the code-navigator plugin to be applied on the dependency module itself — a real `project(...)` dependency with source sets is enough. `classify(project): Map<Project, ModuleRelationship>` is exposed separately from resolution for testability. (Submodule-depth question — does `:services:billing`→`:services:shared` deserve different default treatment than a top-level boundary crossing — is deliberately left open; all DEPENDENCY edges are treated identically regardless of nesting depth for now.)

`Project.taggedClassDirectories()` and `CodeNavigatorTask.resolveAnalysisWorkspace()` both delegate to it, so every existing Gradle bytecode analysis inherits automatic resolution with zero task-specific wiring. The generic staleness pre-check consumes the same workspace.

**Consumers**: DSM, Cycles, and Rings have explicit workspace orchestrator overloads and module-aware TEXT/LLM/JSON output (`ModulePackageLabels` shared across all three; emergent rings retain class→module provenance through simulated moves). Balance, MoveSuggest, Cohesion, Strength, Distance, and Dead consume the workspace for class-directory input. All other existing Gradle bytecode analyses inherit automatic resolution through `Project.taggedClassDirectories()`.

**Committed live fixture**: `test-project-multi/` (`:service → :shared`, unrelated `:unrelated`) includes the plugin directly from source via `includeBuild("..")` — no publish step, no flags needed to verify. `ProjectBuilder` tests cover one-node workspace, dependency inclusion, hierarchy parent, and unrelated exclusion.

**Scoped out — see separate items below**: Maven reactor resolver, module-qualified dependency-graph identity, explicit module-level dependency reporting, and cross-module write/refactoring operations are each a distinct piece of follow-on work, not blockers on this item.

### Maven reactor workspace resolver
**FUTURE** | **Value: medium** | **Effort: high** | Source: internal

Gradle's `AnalysisWorkspaceResolver` has no Maven equivalent — Maven mojos still see only their own module. Real support needs `MavenSession`/reactor project list threaded into every mojo (no shared base Mojo class exists today, so this is per-mojo wiring, not a single change point). Lower priority than the Gradle-side work since Maven-based multi-module cnav usage hasn't been field-tested yet.

### Module-qualified class identity for duplicate FQCNs
**FUTURE** | **Value: medium** | **Effort: high** | Source: internal

`WorkspaceClassIndex.modulesOfClass()` correctly detects when the same FQCN exists in more than one module (`Map<ClassName, Set<String>>`), and that's surfaced in DSM/Cycles/Rings module labels. But the underlying dependency graphs (`PackageDependency`, `CallGraph`, etc.) are still keyed by `ClassName` alone — a genuine duplicate silently merges into one graph node, so cross-module edges touching a duplicated class can't be attributed to the correct module. This is a real correctness gap, not just a display one; fixing it means threading module identity into the dependency-extraction layer itself (`DsmDependencyExtractor`, `CallGraphBuilder`), which touches every consumer. High effort, parked until duplicate FQCNs across dependency modules are shown to matter in practice (uncommon with clean module boundaries).

### Module-level dependency aggregation/reporting
**FUTURE** | **Value: low** | **Effort: medium** | Source: internal

Class/package-level analysis already runs against the shared workspace and labels results by module. A dedicated module-level view (e.g. "module A depends on module B via N class references, here are the top edges") doesn't exist yet — today you infer it by reading module labels across many package-level results. Low priority; the underlying data already exists in `AnalysisWorkspace`'s `dependencies`/`dependenciesOf`, so this would be a formatter/aggregation exercise, not new analysis.

### Replace regex JSON parsing with Jackson
**PARKED** | **Value: low** | **Effort: low** | Source: internal

`PlanMutator.parseJson` currently uses regex extraction. Replace with Jackson (or kotlinx.serialization) for robustness. Add Jackson dependency or reuse one already on classpath via Maven plugin. Low priority — no reported issues from regex approach.

### Consider: operation sequences as the primary interface
**FUTURE** | **Value: high** | **Effort: very high** | Source: design-discussion

Could the sequence-of-operations pattern (`--plan-file`) become the default mode rather than an add-on? Instead of many dedicated targets (cnavCycles, cnavDsm, cnavRings, cnavBalance, cnavSimulateMove), a single target accepts a JSON describing what to analyze and what virtual mutations to apply. This would:
- Eliminate many separate targets in favour of one composable interface
- Unify `cnavSimulateMove` into the plan format (just another step + "then show cycles")
- Allow agents to express complex queries in one invocation

Trade-offs:
- Requires robust JSON validation with clear error messages when fields are missing or malformed
- Discoverability is worse (no `--help` per task, everything is JSON schema)
- Simple queries become more verbose
- Existing per-task interface is already well-established

Could be a new single target (`cnavAnalyze --plan-file=...`) alongside the existing tasks, not replacing them immediately.

### Auto-suggest refactoring sequences
**FUTURE** | **Value: high** | **Effort: very high** | Source: design-discussion

Now that `--plan-file` supports simulating N sequential moves, can we automatically suggest a sequence? Heuristics to explore:
- Start with weakest-link edges from `CycleBreakAnalyzer` — for each, identify which class to move and where
- Greedy: try moving each class on a weak edge to the "other side", pick the move that reduces cycle count the most, repeat
- Type affinity: if a class has higher affinity to another package (from `cnavTypeAffinity`), suggest moving it there
- Ring violations: classes in the wrong ring (from emergent detection) are candidates for moves
- Combine: score = cycle_reduction × affinity_alignment × ring_improvement

Output: a suggested `plan-file` JSON that the agent (or human) can review, adjust, and then execute.
Prerequisite: `--plan-file` wiring complete + Jackson JSON parsing for robust plan validation.

### Post-execution simulation drift detection
**FUTURE** | **Value: medium** | **Effort: medium** | Source: field-testing (greitt)

The `--plan-file` simulation mutates the dependency graph structurally but doesn't model secondary effects of import rewrites. When `MoveClassRewriter` updates imports during execution, the new import graph can shift ring assignments for *other* classes — causing the actual result to diverge from the simulation.

Improvement: after `execute-plan` runs, automatically re-run ring/cycle analysis and compare against the pre-execution simulation. If they diverge significantly, warn the user. This would catch cases where a move drags framework dependencies into the wrong layer (e.g., Ktor types ending up in infrastructure).

### `cnavFileDeps` — file-level dependency tree
**FUTURE** | **Value: medium** | **Effort: medium** | Source: internal

Current dependency analysis works at package granularity (`cnavPackageDeps`, `cnavDsm`) or method/symbol granularity (`cnavFindCallers`, `cnavFindUsages`). There is no file-level view, which is the granularity humans and agents actually navigate at.

- **Builder**: Aggregate class-level bytecode references up to `UsageSite.sourceFile`.
- **Single-file mode**: `--file=OrderService.kt` — direct deps. `--reverse=true` — files that depend on this one. `--depth=N` — transitive tree.
- **All-files mode**: `--all=true` — full file-dep graph (fan-in/fan-out ranking, orphan detection, file-level cycle detection).
- **Alternative framing**: Extend `cnavDsm` with `--granularity=file|package|class` instead of a separate task.

### Structural ring mode improvement
**FUTURE** | **Value: medium** | **Effort: medium** | Source: field-test(greitt, v0.1.102)

Improve the default `--mode=package` to detect ring subpackages by their dependency shape (not naming). A subpackage is Ring 0 if it has no framework deps, Ring 2 if it imports I/O — regardless of what it's called. Validates that detected ring boundaries are respected.

### Intra-package ring violations for emergent mode
**FUTURE** | **Value: medium** | **Effort: medium** | Source: field-test(greitt, v0.1.102)

In emergent mode, report violations where a domain class (ring 0) within a package depends on an adapter class (ring 2) — upward dependencies within a package. Currently SCC collapse makes this hard to detect (cycles hide direction). May need pre-SCC edge analysis.

### `cnav-config.json` — consolidated project config (rings + modules + defaults)
**PARTIALLY DONE** | **Value: high** | **Effort: medium** | Source: field-test(greitt, v0.1.106)

One file, three top-level sections, sharing one location (`cnav-config.json` at project root) — but each section still has its own parser rather than a single unified one (see note at the end).

**Actual current schema** (flat, not nested under a `"config"` wrapper):
```json
{
  "defaults": {
    "format": "llm",
    "scope": "prod",
    "packageFilter": "no.mikill.greitt",
    "excludeAnnotated": ["org.springframework.stereotype.Component"]
  },
  "ringNames": ["domain", "port", "application", "infrastructure", "web-output", "web-input", "composition-root"],
  "hints": {
    "domain": ["*Domain*", "*Event", "*Exception", "*Types"],
    "port": ["*Port", "*Repository", "*Client"]
  },
  "overrides": {
    "no.mikill.greitt.web.RoutePaths": "web-input"
  }
}
```

**`rings` (hints/overrides/ringNames)** — **DONE**, predates this item. Fully implemented in `RingsHintsConfig` (loaded via `RingsHintsConfig.loadFromDirectory`), wired into `EmergentRingDetector`/`ClassRingClassifier`. `hints` sets a **minimum ring** by glob pattern on the simple class name (`Kt`/`Test` suffixes stripped) — never demotes, `actualRing = max(rawRing, hintMinimum)`. `overrides` (FQCN → ring name) take precedence over hints. `ringNames` sets display labels; order determines ring number (first = innermost). `cnavRings --bootstrap-config` generates a starting file from emergent detection. Covered by `RingsHintsConfigTest`.

**`defaults`** — **DONE** (v0.1.112). New `CnavConfig.loadDefaults`/`applyDefaults` in core (`no.f12.codenavigator.config`) reads the `defaults` object as string values and merges it under a task's properties map — **any param name for any task works generically**, since it's just a key/value merge before `ParamDef.parseFrom(properties)` runs; there's no per-task allowlist, unmatched keys are silently ignored exactly like an unrecognized CLI flag. Precedence: explicit CLI options > `cnav-config.json` defaults > a task's own hardcoded `ParamDef` default.
- **Gradle**: wired once, centrally, in `CodeNavigatorTask.buildOptionsMap()` — covers **every** Gradle task automatically, no per-task changes needed.
- **Maven**: no shared base task exists (see orchestrator-extraction notes above), so each mojo needs an explicit `project.applyConfigDefaults(buildPropertyMap())` wrap (added to `MavenSupport.kt`). **Now wired for all ~48 Maven mojos** (v0.1.112) — the 37 sharing the plain `enhanceProperties(buildPropertyMap())` pattern via bulk mechanical edit, `find-callees`/`find-callers` via the one shared `CallTreeMojoSupport.execute()` call site, `why-depends` directly (it didn't even call `enhanceProperties`, a separate pre-existing minor gap left as-is), and `rename-class` for free via its `MoveClassMojo` superclass. Left out on purpose: `cnavHelp`/`cnavAgentHelp`/`cnavConfigHelp` — pure help output, no task-level params for a default to meaningfully apply to. Verified end-to-end with a real local Maven install (`./mvnw install`) against a scratch project: config default applies with no `-D` flag given, explicit `-Dformat=text` still overrides it, on both a plain mojo (`why-depends`) and the shared-support path (`find-callers`).

  Found one more small Maven-only divergence while verifying: `CallTreeMojoSupport`'s "no methods found" path used a raw `println(...)` instead of `OutputWrapper.emptyResult(config.format, ...)`, ignoring `--format` — Gradle's `CallTreeTaskSupport` didn't have this gap. Fixed alongside since it was a one-line change in the same file.
- Verified end-to-end against the real plugin (Gradle composite build) that a config default applies when no CLI flag is given, and that an explicit CLI flag still overrides it.
- Not done: "always show active defaults in output header when config is in use" (original concern about implicit defaults hiding active config) — no output currently surfaces which defaults came from the file vs CLI vs hardcoded.
- Also candidate but not implemented: `ci.maxCycles`/`ci.maxViolations` for the `--fail-on-violation` CI gate (raised in v0.1.111 field feedback) — works today via the generic mechanism (`{"defaults": {"max-cycles": "0"}}`), just not given dedicated `ci.*` schema treatment.

**`modules`** — **NOT CURRENTLY NEEDED**: Gradle workspace discovery is automatic from the real project dependency graph; unrelated siblings are excluded without configuration. Add include/exclude overrides only if field use finds legitimate exceptions.

**Deferred cleanup**: literally merging `RingsHintsConfig` and `CnavConfig` into one parser (instead of two independent readers of the same file) was skipped to avoid risk to the already-tested rings-hints code path. Low priority — revisit only if the two ever need to share more logic than "read the same file."

---

## Refactoring operations

### cnavMoveFunction — move top-level Kotlin functions
**PARKED** | **Value: low** | **Effort: high** | Source: field-test(bass-ra)

Move top-level functions (e.g. `objectMapper()` from `di/Serialization.kt` to `config/Serialization.kt`). Top-level functions in Kotlin compile to `*Kt` facade classes, so bytecode-level tracking works, but the refactoring operation needs to handle the source differently (no class declaration to move, just function bodies).

### PSI-based refactoring operations
**FUTURE** | **Value: high** | **Effort: varies** | Source: internal

Now that `cnavRenameMethod` has been migrated to PSI (v0.1.90), the compiler integration barrier is gone — we have a working two-phase architecture (ASM location finding → PSI editing in isolated classloader) that new operations can reuse.

Inspired by [Martin](https://github.com/audunstrand/martin) by Audun Fauchald Strand.

**Catalog updated**: `cnavSafeDelete` and `cnavChangeSignature` (steps 3 and 5 below) are already implemented (`SafeDeleteTask`/`SafeDeleteMojo`, `ChangeSignatureTask`/`ChangeSignatureMojo`, both with `*WorkAction` PSI editors) — struck from the remaining work, kept in the order list for context. Nothing else in the full catalog exists yet (confirmed: no `extract-*`, `convert-to-*`, `inline`, `add-named-arguments`, `introduce-parameter-object`, `pull-up-method`, `replace-constructor-with-factory`, or `encapsulate-field` goals registered in `TaskRegistry`).

**Effort key (post-PSI migration):**
- **Low** = PSI tree walking + text replacement, no type resolution needed.
- **Medium** = Needs bytecode-guided location finding or cross-file coordination.
- **High** = Needs `BindingContext` / full type resolution (not yet implemented).

**Recommended implementation order (quick wins first):**
1. `cnavConvertToExpressionBody` / `cnavConvertToBlockBody` — trivial, validates pattern (low)
2. `cnavExtractVariable` / `cnavExtractConstant` — single-file (low)
3. ~~`cnavSafeDelete`~~ — **DONE**, leverages existing dead code detection (low)
4. `cnavConvertToDataClass` — single-file with PSI validation (low)
5. ~~`cnavChangeSignature`~~ — **DONE**, reuses RenameLocationFinder, high value for agents (medium)
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
- ~~`cnavChangeSignature`~~ — **DONE** (medium)
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
- ~~`cnavSafeDelete`~~ — **DONE** (low)
- `cnavEncapsulateField` (medium)

**Notes:**
- All "medium" operations follow the two-phase pattern proven by `cnavRenameMethod`: ASM finds locations → PSI edits.
- "High" operations blocked on BindingContext. Consider Martin delegation until we add support.

---

## Dead code & metrics

### Meta-annotation traversal for dead code filtering
**FUTURE** | **Value: medium** | **Effort: high** | Source: internal

`@RestController` is meta-annotated with `@Controller` which is meta-annotated with `@Component`. Currently, excluding `Component` does NOT exclude `@RestController`.

- In `AnnotationExtractor`, scan annotation `.class` files from classpath JARs and resolve meta-annotations transitively.
- Covers custom stereotype annotations automatically.
- **Prerequisite**: `cnavJar` (classpath resolution infrastructure) — non-trivial to resolve annotation classes from JARs. Pending classpath JAR infrastructure.

### Transitive dead code detection
**FUTURE** | **Value: medium** | **Effort: high** | Source: user-feedback(v0.38)

A method is "transitively dead" if all its callers are themselves dead. Iterate until fixed point.
- Confidence levels: `DEAD` (zero callers), `TRANSITIVELY_DEAD` (all callers dead), `TEST_ONLY` (only test callers), `SHRINKING` (declining caller trend).

### Per-package health dashboard
**FUTURE** | **Value: low** | **Effort: medium** | Source: internal

Aggregate all per-package metrics into a single view: volatility, coupling strength breakdown, distance profile, cycle involvement, balance assessment. Could be a mode of `cnavMetrics` (`--by-package=true`) or separate `cnavPackageHealth` task.

---

## Standalone new tasks

### `cnavTestHealth` — verify all test methods actually ran
**PARKED** | **Value: medium** | **Effort: medium** | Source: user-feedback

Count `@Test`-annotated methods from bytecode, compare against JUnit XML results, flag the delta. Catches silently skipped tests (e.g., non-`Unit` return types).

### `cnavDiff` — structural diff between builds
**PARKED** | **Value: low** | **Effort: medium** | Source: internal

Compare two compiled states: added/removed/changed classes, methods, dependency edges. No demand from field use. Most diff needs handled by git-based tasks.

### `cnavUnused` — unused build dependencies
**PARKED** | **Value: low** | **Effort: medium** | Source: internal

For each declared dependency JAR, extract package list. Scan project bytecode for references. Dependencies with zero references are candidates for removal. Caveat: runtime-only deps (JDBC drivers, logging backends) need exclusion mechanism. Niche — existing tools (gradle-lint, dependency-analysis) already cover this.

---

## TDD practice enforcement

### `cnavFakeCoverage` — verify all ports have fakes
**PARKED** | **Value: low** | **Effort: low** | Source: internal

Scan interfaces matching a pattern (`*Repository`, `*Client`), check each has at least one implementation in test source set. InterfaceRegistry + test source filter — infrastructure exists.

### `cnavContextUsage` — verify consistent test context usage
**PARKED** | **Value: low** | **Effort: medium** | Source: internal

Check that test classes use a shared test context rather than constructing dependencies ad-hoc.

### `cnavInterfacePurity` — check interfaces use domain types
**PARKED** | **Value: low** | **Effort: medium** | Source: internal

For interfaces matching a pattern, check method signatures reference only domain-package types (not DTO/infrastructure types).

---

## Output & UX

### Interpretation/hint fields in JSON output
**PARTIALLY DONE** | **Value: medium** | **Effort: medium** | Source: field-test(bass-ra)

First concrete instance shipped (v0.1.112): `JsonFormatter.formatCycles` now emits `"testInvolvement":{"testInvolved":N,"total":M}` as a real structured field, landed as part of the formatting-layer-boundary fix. Establishes the shape (per-result field, not a wrapper object) — reuse this pattern for the rest.

Still only TEXT/LLM: `BALANCE_INTERPRETATION`/`COUPLING_INTERPRETATION` strings, `cnavRings`' test-involvement line and package-mode notice (`cnavRings` JSON isn't structured at all yet — see the new item above), and any other task's interpretation footer. Applies to: `cnavBalance`, `cnavCoupling`, `cnavRings` (any task that appends an interpretation/notice today).

### `test-involvement` line for cnavBalance
**PARKED** | **Value: low** | **Effort: medium** | Source: field-test(bass-ra)

The `test-involvement: N of M … involve test sources` line (printed when scope=all) was added to cnavCycles and cnavRings (emergent), which retain class-level edges end-to-end. cnavBalance was deferred: its public result (`BalanceEntry`/`BalanceOutput`) is package-level and `BalanceOrchestrator` consumes the class-level `extractResult.data` internally without surfacing it. To add the line for Balance, thread a test-involvement count out of the orchestrator (it already has the class-level deps + can build a `SourceSetResolver` from the unfiltered tagged dirs) into `BalanceOutput`, then render via `TestInvolvement.notice(...)`.

The formatting-boundary cleanup this was waiting on is now done (see above) — `TestInvolvement.Counts` flowing through as a field on the orchestrator's output, rendered by the formatter, is the established pattern (`CyclesOutput`/`EmergentRingsOutput`). Apply the same pattern here whenever this gets picked up.

### `TaskGuidance` — structured context output for all tasks
**PARTIALLY DONE** | **Value: medium** | **Effort: high** | Source: internal

The `TaskGuidance` data class already exists (`no.f12.codenavigator.formatting.TaskGuidance`, 13 lines: `purpose`/`parameterGuidance`/`interpretation`) and has exactly one real instance — `TestCouplingGuidance.GUIDANCE`, used by `TestCouplingTask`/`TestCouplingMojo` via `OutputWrapper.wrapWithGuidance(...)`. That's a working precedent, not the full rollout — the plan's actual ask ("for all tasks", "replaces scattered `*_INTERPRETATION` constants") hasn't happened. The scattered `*_INTERPRETATION` constants (balance, coupling, rings, etc.) are still scattered. Remaining work: define a `TaskGuidance` instance per task and wire it into the no-params fallback / LLM header uniformly, following the `TestCouplingGuidance` pattern.

```kotlin
data class TaskGuidance(
    val purpose: String,
    val parameterGuidance: String,
    val interpretation: String,
)
```

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
**LOW** | **Value: medium** | **Effort: low** | Source: internal(v0.1.83) + field-test(greitt, v0.1.113)

Raw volatility numbers meaningless without scale. Show percentile rank or relative to project mean.

Field-test reinforcement: greitt showed a `DANGER` verdict on `web → polls.model` — a legitimate cross-ring edge that's technically high-distance + volatile, but in a small clean hexagonal project *everything* is volatile, so `DANGER` on a web→domain edge is alarming when it shouldn't be. This is the concrete cost of the missing context — verdicts need to be relative to project scale (percentile / project-mean), not absolute thresholds. Bumped to LOW/medium given a second field report.

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

### Refactoring "compile to verify" warning is unconditional
**LOW** | **Value: low** | **Effort: low** | Source: field-test(v0.1.113)

Every refactor op — even a clean rename with full call-site coverage — ends its output with "Automated refactoring is not always fully accurate … compile to verify." Good advice, but unconditional noise. Make it conditional: warn only when the rewriter actually hit an ambiguous case — a heuristic fallback / unresolved reference, dynamic dispatch, reflection, or the new non-Kotlin-reference warning. Now tractable because the K1 resolution work makes "did we resolve everything?" a known quantity: a fully-resolved rename could instead report "all N call sites updated" (see [[Make the move/rename rewriter type-safe (semantic resolution)]]).

### `cnavRings` warns about ringNames coverage even when none are configured
**LOW** | **Value: low** | **Effort: low** | Source: field-test(ra-backend, v0.1.113)

With 10 rings detected and no `cnav-config.json` present, output printed `Warning: ringNames covers 4 rings but rings up to 8 were detected — rings 4–8 will use default names`. When the user has supplied no `ringNames` at all, default names (`Ring 4`, …) are the expected behavior — nothing to warn about. Fire the warning only when a *user-supplied* `ringNames` list is shorter than the detected ring count, not for the built-in defaults.

### `cnavConverge --mode=risk` includes complexity=0 classes
**LOW** | **Value: low** | **Effort: low** | Source: field-test(ra-backend, v0.1.113)

Risk entries with `complexity=0` (no fan-in/out) feed the formula as `max(0,1)=1` (the neutral multiplier) and look odd in output. A class with zero structural complexity probably shouldn't rank at all — filter `complexity < 1` out of the risk list to tighten it.

### `cnavDuplicates` is dominated by generated files
**FUTURE** | **Value: medium** | **Effort: low** | Source: field-test(ra-backend, v0.1.113)

On ra-backend the top duplicate blocks were all JAXB-generated `.java` under `no/bankid/ra/*` — intentional/structural duplication in generated artifacts, drowning hand-written duplication. Add an exclusion: a `--exclude=<regex>` (matching the shared `EXCLUDE` param) and/or auto-skip files that are `@Generated`-annotated or under a `generated/` path.

### `cnavSize` rejects `--scope`
**LOW** | **Value: low** | **Effort: low** | Source: field-test(v0.1.113)

`cnavSize` is grouped with structural tasks that all carry `--scope`, so agents routinely pass it, but it errors "Unknown command-line option '--scope'". Either add scope support (filter by source set) or accept it as a documented no-op. Concrete instance of [[Consistent `--project-only` support across all tasks]].

### `cnavMoveSuggest --plan-file` missing from agent help
**LOW** | **Value: low** | **Effort: low** | Source: field-test(v0.1.113)

`cnavMoveSuggest` supports `--plan-file` but the `cnavAgentHelp` task-reference line for it and the global `--plan-file` param description don't list it among the plan-file-aware tasks. Add it to both.

### `--fail-on-violation` invisible in agent help
**LOW** | **Value: medium** | **Effort: low** | Source: field-test(v0.1.111)

`--fail-on-violation` and `--max-cycles`/`--max-violations` are defined in `TaskRegistry` and work correctly, but are absent from the `cnavCycles` and `cnavRings` task reference lines in `cnavAgentHelp`. The global param list also omits them. An agent or CI author reading the task table has no way to discover the CI gate feature exists — a significant discoverability gap. Fix: ensure `TaskDef.params` for `CYCLES` and `RINGS` include `FAIL_ON_VIOLATION`, `MAX_CYCLES`, `MAX_VIOLATIONS`, so the help generator picks them up automatically.

---

## Find-usages output quality
**FUTURE** | **Value: medium** | **Effort: medium** | Source: field-test(v0.1.72)

A single logical call site produces 3-4 lines (`.new` + `.<init>` + `.checkcast` + field access). Lambda classes obscure actual callers. Need collapsing/deduplication and smart summary mode.

---

## Classpath / JAR scanning

### Full classpath scanning option
**FUTURE** | **Value: medium** | **Effort: medium** | Source: internal

`--classpath=true` to scan full runtime classpath. Applies to list/find/class/interfaces/usages. AI agents frequently need library API signatures.

---

## Behavioral + structural fusion

### Port volatility lockstep detector
**FUTURE** | **Value: medium** | **Effort: medium** | Source: internal

Cross-reference interfaces with volatility. If port interface changes as frequently as its adapter, the abstraction isn't stable.

### `cnavChangedSince` → layered impact predictor
**FUTURE** | **Value: medium** | **Effort: high** | Source: internal

Expand from single-hop blast radius into multi-signal impact predictor: direct callers → transitive callers → interface implementor callers → historically co-changed → same-package peers. Each with confidence tier.

### `cnavConverge`: unify intersect and risk mode output
**FUTURE** | **Value: medium** | **Effort: medium** | Source: field-test(kotlin-htmx)

Field-tested on kotlin-htmx: both modes work and produce correct, actionable results independently (intersect found real LATENT/MISSING ABSTRACTION package pairs; risk correctly surfaced a high-churn, high-complexity, high-coupling class as the top hit). But the two modes are completely separate pipelines with no shared output — no combined view like "this class is high-risk AND it's in a cyclically-coupled package pair." An agent wanting the full picture currently has to run both and correlate manually.

Would need `ConvergeOrchestrator`'s risk-mode entries (per-class) to cross-reference intersect-mode's package-pair edges (`ConvergedEdge.source`/`.target` via `ClassComplexity.className.packageName()`) — e.g. a risk entry whose class's package is on both sides of an ACT_NOW edge could be flagged/boosted. Not attempted yet; the two modes' underlying `SourcePathIndex`-based path resolution already gives both a shared way to key off the same file, so the plumbing exists — just the correlation logic and a combined output shape don't yet.

---

## Internal code quality

### ~~Separate finding from doing in PSI refactoring operations~~ — DONE (v0.1.113-SNAPSHOT)
**DONE** | **Value: high** | **Effort: medium** | Source: internal

Extracted `DeclarationFinder` (`refactor/DeclarationFinder.kt`) — a shared pure-finding layer that walks all `.kt` source files and returns `DeclarationLocation(declarationFile, callSiteFiles, overrideFamilyFqns)`. Wired into all four PSI rewriters:
- `PsiRenamePropertyRewriter` — now iterates `location.callSiteFiles` instead of all source files; declaration edits only run against `location.declarationFile`.
- `PsiRenameParamRewriter` — same; `isValVar` pre-check uses `location.callSiteFiles`.
- `ChangeSignatureRewriter` — first pass targets `declarationFile` only; second pass targets `callSiteFiles`.
- `SafeDeleteRewriter` — `deleteDeclaration` simplified from a list-walking function to single-file, accepting `declarationFile` directly.

Also removed three copies of `buildClassFqn`/`matchesClassOrCompanion`/`fileReferencesClass` duplicated in `PsiRenameParamRewriter` vs `KotlinFqnSupport`/`RewriterSupport`, and replaced raw string comparisons (`filePackage != targetPackage` + `clazz.name != simple`) with `buildClassFqn/matchesFqn` in `ChangeSignatureRewriter` and `SafeDeleteRewriter` — those now correctly handle nested classes and Companion objects.

### Split JsonFormatter and LlmFormatter per-feature
**ACTIVE** | **Value: medium** | **Effort: high** | Source: internal(v0.1.83)

`JsonFormatter` (364 outgoing, 77 types, fanIn=261) and `LlmFormatter` are highest-complexity classes. Change together 96% of the time. Split into per-feature formatters; top-level becomes thin dispatcher. Wide change touching many files.

### Reduce Gradle/Maven duplication via orchestrator extraction
**PARKED** | **Value: medium** | **Effort: high** | Source: internal

Every Gradle/Maven pair duplicates orchestration. Three pairs already have orchestrators. Extend to rest (~590 lines duplicated across 14 pairs). Tedious but mechanical. More orchestrators extracted in v0.1.111-112 (volatility/coupling/type-affinity/cycles/dsm/rings/metrics); the bulk of the remaining duplication is in the refactoring tasks which have a different pattern (PSI-based, not orchestrator-based).

### Potentially dead code in cnav's own codebase
**PARKED** | **Value: medium** | **Effort: low** | Source: internal

Self-analysis found: `CallGraphCache.build()`, `ClassIndexCache.build()`, `InterfaceRegistryCache.build()`, `SymbolIndexCache.build()`, `UsageScanner.scan()`, various `FileCache` methods. Investigate if truly dead or called via dispatch.

### Test suite health
**LOW** | **Value: medium** | **Effort: medium** | Source: internal

- **Cache KotlinParser in rewriter tests** — 71% of test time in 5 classes. Sharing parsed AST could cut from 10.4s to ~3-4s.
- **Add `FieldExtractor` tests** — 0% coverage, only non-Gradle core class at zero.
- **Cover `LlmFormatter`/`JsonFormatter` uncovered branches** — primary agent-facing formatters at ~80%.


**PARKED** | **Value: low** | **Effort: low** | Source: internal

Confirmed via a live `cnavBalance` self-check (see [[Fix DANGER balance: root package → callgraph/implementors]] in `plan-completed.md`) that a *different* DANGER edge exists today: `no.f12.codenavigator → no.f12.codenavigator.navigation.types` (FUNCTIONAL, distance=2, volatility=74/0). Cause: `AgentHelpText.kt` imports `navigation.types.FrameworkPresets` to list framework presets in help output. Lower severity than the original finding (`navigation.types` is a shared low-level types package, not a concrete feature type like callgraph/implementors), so parked rather than acted on immediately — but worth a look if the root package picks up more such imports.

### Document that read-only analysis already supports any JVM language
**PARKED** | **Value: medium** | **Effort: low** | Source: internal

Re-scoped from "Evaluate other JVM languages to support" after checking the actual code: the read-only analysis layer (`navigation/bytecode/`, 9 files — DSM, cycles, rings, hotspots, dead code, complexity, usages, call trees, etc.) is entirely ASM bytecode-based with zero source-language assumptions. It already works on Groovy/Scala/Java projects today, same as Kotlin, with no new code — the README already claims this (line 3: "Works with any JVM language... since it analyzes compiled bytecode"). What's missing is making the *write* side's limitation equally explicit (see next item) rather than letting the blanket "any JVM language" claim imply refactor operations too. Low effort: verify the claim still holds (re-run a couple of read-only tasks against a non-Kotlin/Java fixture if one's easy to construct) and tighten the docs.

### Refactor/write operations are Kotlin-PSI-only, no real path to other languages
**PARKED** | **Value: low** | **Effort: high** | Source: internal

Checked the actual refactor-package code (prompted by a user question doubting the old item's "low effort" label): of the 5 refactor operations, only `RenameMethod` has a real per-language dispatch (`LanguageRenameRewriter` interface, `KotlinRenameMethodRewriter`/`JavaRenameMethodRewriter` — ~170/193 lines each, i.e. building the Java parallel cost a full second implementation). The other four — `ChangeSignature`, `SafeDelete`, `RenameParam`, `RenameProperty` — call straight into `KtPsiFactory`/`withKotlinPsiFactory` (kotlin-compiler-embeddable) with **no file-extension guard**: given a `.java` file (or any other JVM language source), they'll attempt to parse it with the Kotlin parser rather than rejecting it or routing elsewhere. `MoveClassRewriter` sits on OpenRewrite's `ChangeType`, nominally language-neutral, but its own file-path matching is hardcoded to `.kt` suffixes and the plan already documents real Kotlin-visitor traversal limits (`KotlinIsoVisitor` doesn't traverse 3+ levels of nested lambdas).

Real Groovy/Scala write support means sourcing a usable embeddable frontend per language and then building a second PSI-editing pipeline per operation (comparable cost to the one existing `RenameMethod` Java rewriter, × 4 more operations) — high effort, not the "low (research)" this used to be labeled. Parked rather than promoted; the immediate actionable follow-up is documenting the current failure mode (see item above), not building the support.

---

## Infrastructure

### Structured cache format
**PARKED** | **Value: medium** | **Effort: medium** | Source: internal

Replace tab-separated positional fields with self-describing format. Consider removing cache entirely — zero measurable difference on ~20k LOC projects.

### Gradle incremental task support
**FUTURE** | **Value: medium** | **Effort: high** | Source: internal

`@InputFiles`/`@OutputFile`/`InputChanges`. Most beneficial for leaf tasks (`cnavListClasses`, `cnavFindSymbol`).

---

## Martin integration & compiler infrastructure

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
