# Plan

Items grouped by functional area. Each item has:
- **Status**: ACTIVE (next up) / FUTURE (someday) / LOW (deprioritized but still on the backlog, ahead of PARKED) / PARKED (low priority, revisit if demand) / REJECTED
- **Source**: internal / field-test(project) / user-feedback(version)

---

## Bugs

---

## Multi-module support

### Full multi-module analysis — aggregate class dirs from all project modules
**PARTIALLY DONE** | **Value: high** | **Effort: high** | Source: internal

**Progress so far**: Implementation-order steps 1-2 done, plus `cnavDsm` wired (Gradle only) as the first task per the plan's own prioritization ("start with cnavDsm"). `ModuleSourceSet(moduleName, sourceSet)` added to `no.f12.codenavigator.navigation.types` alongside `SourceSet`. `MultiModuleResolver` (Gradle-only, `no.f12.codenavigator.gradle`) resolves `project.rootProject.allprojects.filter { it.plugins.hasPlugin(CodeNavigatorPlugin::class.java) }` and tags each module's main/test class dirs with its Gradle project path (e.g. `:shared`) as the module name — simpler auto-detection than the plan's three-way OR (plugin-applied / cnav-config.json present / explicit `modules` list); the config-file-based detection modes are not implemented yet.

Deliberately **not** done in this pass (all still open, see below): Maven's `MavenReactorResolver`, wiring into the other ~9 structural tasks (Cycles, Rings, Balance, MoveSuggest, Dead, Cohesion, Strength, Distance, Volatility), the `--modules` CLI shortcut, `cnav-config.json` `modules` section, cross-module plan-file support, and the `test-project-multi/` fixture (verified instead via an ephemeral 2-subproject composite build, not committed).

**`cnavDsm --multi-module` implementation**: `DsmOrchestrator.run` gained an optional `moduleOfClass: Map<ClassName, String> = emptyMap()` param (default empty — zero behavior change for existing callers, including Maven's `DsmMojo` which doesn't support multi-module yet and just warns if the flag is set). When non-empty, `DsmAnalysisOutput.moduleLabels: Map<PackageName, Set<String>>` is computed by grouping `projectClasses` by `packageName().truncate(displayPrefix, depth)` — the *same* truncation the matrix itself uses, so labels key correctly against `matrix.packages` even with `--dsm-depth`/prefix-stripping active. A package mapping to more than one module (genuinely ambiguous — normally shouldn't happen with clean module boundaries) surfaces as multiple sorted names in the label rather than being silently collapsed. `DsmFormatter.labelFor` renders `[:mod1,:mod2] pkg` (plain `pkg` when no module info), applied to TEXT (legend + row labels) and LLM (`packages:` line); JSON adds an additive `packageModules` field (existing `packages` array untouched, so old JSON consumers unaffected). `DsmTask` (Gradle) scans each tagged module directory separately (`scanProjectClasses(listOf(dir))`) to build `moduleOfClass`, since bytecode scanning loses per-class module provenance once directories are flattened into one list.

**Verified live**: ephemeral 2-subproject composite build (`:shared` ← `:service`, cross-module `import`) — single-module `:service:cnavDsm` correctly shows nothing (the dependency is invisible from one module), `--multi-module=true` correctly shows the `:service`→`:shared` edge with `[:service]`/`:[shared]` labels in TEXT and a `packageModules` field in JSON. New tests: `MultiModuleResolverTest` (Gradle `ProjectBuilder`-based), `DsmOrchestratorTest` (moduleLabels grouping incl. the ambiguous-package case), `DsmFormatterTest`/`JsonFormatterTest`/`LlmFormatterTest` additions for `labelFor`/`packageModules`.

**DONE — classification implemented**: `MultiModuleResolver` no longer includes "any subproject with the plugin applied." It now classifies every project in the build relative to the invoked project via `ModuleRelationship` (`no.f12.codenavigator.gradle`), three-way:
- **SOURCE** — not a single project, but the transitive subtree rooted at whatever project the task was invoked on (`project.allprojects`). Invoked on a leaf (`:service`): SOURCE = just `:service`. Invoked on an aggregator/root: SOURCE = the root + everything beneath it, collapsed into one scope.
- **DEPENDENCY** — a module reachable via an actual `project(":x")` edge (any configuration, not just `implementation`) from something in SOURCE, walked transitively via BFS. On-disk, part of this build, represents real compiled coupling.
- **HIERARCHY** — everything else in `rootProject.allprojects` — structurally in the same build but neither SOURCE nor a real dependency. Excluded by default; `resolve()` filters these out before aggregating.

`classify(project): Map<Project, ModuleRelationship>` is exposed separately from `resolve()` for testability. The plugin-applied filter is gone entirely — inclusion in SOURCE/DEPENDENCY no longer requires the code-navigator plugin to be applied on that module (a dependency module is aggregated purely because it's a real `project(...)` dependency and has a `SourceSetContainer`, whether or not it happens to apply the plugin itself); HIERARCHY modules are simply whatever's left over, also without a plugin check.

Submodule-depth question (from the earlier design discussion — does a `:services:billing`→`:services:shared` dependency deserve different default treatment than one crossing a top-level boundary) is **deliberately left open**: for now, all DEPENDENCY edges are treated identically regardless of nesting depth. Revisit if real multi-level projects show this needs different defaults.

**Verified live**: 3-module composite build (`:shared`, `:service`→`:shared`, `:unrelated` with no dependency edge) — `:service:cnavDsm --multi-module=true` correctly shows only `:service`+`:shared`, `:unrelated` is excluded (HIERARCHY). New tests: `MultiModuleResolverTest` rewritten for `classify()` (leaf vs. root invocation, direct and transitive DEPENDENCY, HIERARCHY exclusion) and `resolve()` (tags with source module path, excludes HIERARCHY, includes real dependencies, root aggregates the whole subtree).

**DONE — staleness check fixed**: the generic pre-check in `CodeNavigatorPlugin`'s task registration (`doFirst` block) now checks `(this as? MultiModuleCapable)?.multiModuleFlag == "true"` and, when true, uses `MultiModuleResolver.resolve(project)`/`.sourceDirectories(project)` (aggregated across included modules) instead of the invoking project's own `SourceSetContainer`. `MultiModuleCapable` (`no.f12.codenavigator.gradle`, alongside `CodeNavigatorTask`) is a small marker interface exposing `multiModuleFlag: String?`, implemented by `DsmTask` — future tasks that gain `--multi-module` support just implement it too, no changes needed to the plugin registration code. Also hardened the single-module branch while in there: `getByType(SourceSetContainer::class.java)` (throws if the project has no Java/Kotlin plugin at all, e.g. a bare aggregator root) became `findByType` (treated as empty dirs → the existing "no class files" error, not a crash) — a real pre-existing gap, fixed rather than left, per the "never leave pre-existing bugs" note in AGENTS.md. Verified live: a bare aggregator root (no `kotlin("jvm")`/`java` plugin at all) running `:cnavDsm --multi-module=true` now correctly aggregates the whole subtree instead of failing.

Original plan text preserved below for the remaining work.

**Problem**: Every cnav task currently analyzes only the module it runs in (`taggedClassDirectories()` returns one module's `classesDirs`). For multi-module Gradle/Maven projects, this means:
- `cnavDsm` shows only intra-module dependencies — misses cross-module edges entirely
- `cnavCycles` can't detect cycles spanning module boundaries (e.g., `:shared` ↔ `:service`)
- `cnavRings` has an incomplete dependency graph — a class may appear ring 0 when its actual framework dependency lives in another module
- `cnavMoveSuggest` can't suggest moves to packages in sibling modules
- `cnavDead` flags a class as dead when its only consumer is in another module's test scope
- User must run `:module-a:cnavX` then `:module-b:cnavX` and manually combine outputs

**Solution**: A new `--multi-module` mode that aggregates class directories + source roots from all (or selected) subprojects before running analysis. The bytecode layer (`DsmDependencyExtractor`, `CallGraphBuilder`, `SourceSetResolver`) already accepts `List<File>` and handles multiple directories — the gap is in collecting them and presenting results with module provenance.

**Architecture**:

```
┌────────────────────────────────────────────────────────────────────┐
│                       MultiModuleResolver                           │
│  Discovers modules, collects (classDir → moduleName) mappings       │
└────────────────┬───────────────────────────────────────────────────┘
                 │
                 ▼
┌────────────────────────────────────────────────────────────────────┐
│                  AggregatedClassDirectoryProvider                    │
│  Collects classes + source dirs + classpath from every module       │
│  Tags each file/dir with its module of origin                       │
└────────────────┬───────────────────────────────────────────────────┘
                 │
                 ▼
┌────────────────────────────────────────────────────────────────────┐
│              Existing analysis (no change needed)                    │
│  DsmDependencyExtractor.extract(classDirectories, classpath)        │
│  CallGraphBuilder.build(taggedDirs, classpath)                       │
│  SourceSetResolver.from(taggedDirs)                                  │
│  Any task that takes List<File> for class dirs                       │
└────────────────┬───────────────────────────────────────────────────┘
                 │
                 ▼
┌────────────────────────────────────────────────────────────────────┐
│                  ModuleAwareFormatter                                │
│  Wraps existing formatters, adds module labels to output            │
│  Renders module-column in tables, module-group in JSON              │
└────────────────────────────────────────────────────────────────────┘
```

**Gradle implementation** — `MultiModuleResolver`:
```kotlin
class MultiModuleResolver(project: Project) {
    val rootProject = project.rootProject
    val modules: List<ModuleInfo> = rootProject.subprojects
        .filter { hasCodeNavigatorPlugin(it) || isExplicitlyIncluded(it) }
        .map { sub ->
            val sourceSets = sub.extensions.getByType(SourceSetContainer::class.java)
            ModuleInfo(
                name = sub.name,
                path = sub.path,                    // ":module-a"
                classDirectories = sourceSets.getByName("main").output.classesDirs.files.toList(),
                testClassDirectories = sourceSets.getByName("test").output.classesDirs.files.toList(),
                sourceDirectories = sourceSets.getByName("main").allSource.srcDirs.toList(),
            )
        }

    // Aggregated views
    val allClassDirectories: List<File> = modules.flatMap { it.classDirectories }
    val allTaggedDirectories: List<Pair<File, ModuleSourceSet>> = modules.flatMap { m ->
        m.classDirectories.map { it to ModuleSourceSet(m.name, SourceSet.MAIN) } +
        m.testClassDirectories.map { it to ModuleSourceSet(m.name, SourceSet.TEST) }
    }
    val allSourceDirectories: List<Pair<File, String>> = modules.flatMap { m ->
        m.sourceDirectories.map { it to m.name }
    }
}
```

**Maven implementation** — `MavenReactorResolver`:
```kotlin
class MavenReactorResolver(project: MavenProject, session: MavenSession) {
    val modules: List<ModuleInfo> = session.getAllProjects()
        .filter { hasCodeNavigatorPlugin(it) || isExplicitlyIncluded(it) }
        .map { sub ->
            ModuleInfo(
                name = sub.artifactId,
                groupId = sub.groupId,
                classDirectories = listOf(File(sub.build.outputDirectory)),
                testClassDirectories = listOf(File(sub.build.testOutputDirectory)),
                sourceDirectories = sub.compileSourceRoots.map { File(it as String) },
            )
        }
}
```

**Detection strategy** — two modes:

1. **Auto-detect**: When `--multi-module` is set, scan `rootProject.subprojects` (Gradle) or `session.getAllProjects()` (Maven). Include every subproject that:
   - Has the code-navigator plugin applied (`project.plugins.hasPlugin("no.f12.code-navigator")`)
   - OR has a `cnav-config.json` in its project root
   - OR is explicitly listed in `cnav-config.json`'s `modules` section

2. **Explicit config**: In `cnav-config.json` `modules` section (see consolidated config item below).

**CLI**: Tasks get a `--multi-module` flag (boolean). When set, the task's orchestrator uses `MultiModuleResolver` instead of the single-project `taggedClassDirectories()`. Optionally `--modules=":shared,:service"` for explicit selection without config.

**Output changes — module labels**:

| Format | Current | Multi-module |
|--------|---------|--------------|
| TEXT | `com.example.foo.Service` | `[:shared] com.example.foo.Service` |
| JSON | `"className": "com.example.foo.Service"` | `"className": "com.example.foo.Service", "module": ":shared"` |
| LLM | Same as TEXT | Same as TEXT |
| DSM | Packages as row/col labels | `module/package` compound labels, or module-color bands |

**Module prefix strategy**: Show module prefix **only when ambiguous** (class exists in multiple modules with same FQCN) or when `--multi-module` is active. Single-module output unchanged.

**Task-by-task impact**:

| Task | What changes | New capability |
|------|-------------|----------------|
| `cnavDsm` | Row/col labels include module prefix | Cross-module dependency matrix. Upper-right quadrant shows inter-module deps. |
| `cnavCycles` | No cycle detection change (graph is already unified) | Detects cycles spanning module boundaries. Labels each node with module. |
| `cnavRings` | `ClassRingClassifier` gets module info | Sees all dependencies including those in other modules — more accurate ring assignment. Reports which module each violator belongs to. |
| `cnavBalance` | `BalanceBuilder` gets module-tagged entries | Cross-module edges visible in strength/distance/volatility analysis. |
| `cnavMoveSuggest` | `MoveSuggester` includes cross-module deps | Can suggest moves to sibling module packages. |
| `cnavDead` | Cross-module callers included | A class called only from another module's tests is marked `TEST_ONLY`. A class called from no module is truly dead. |
| `cnavCoupling` | Already path-based, no change needed | Benefits from unified view (co-change pairs across module boundaries already detected by git). |
| `cnavChangedSince` | Already path-based | Already works across modules. |
| `cnavSize` | Summed across modules | Total project size regardless of module split. |
| `cnavMetrics` | Module-level rollup | Per-module metrics summary alongside project totals. |

**The formatter barrier — why this strengthens it**:

Multi-module support forces the long-needed cleanup: today's `taggedClassDirectories()` returns `List<Pair<File, SourceSet>>` — the source set tells us MAIN vs TEST but NOT which module. To support multi-module, this must become `List<Pair<File, ModuleSourceSet>>` where `ModuleSourceSet(name, sourceSet)`. That change ripples through every resolver, builder, and formatter.

The **formatter boundary** strengthens because:
- Formatters must now render module labels — this is a pure display concern that must NOT leak into analysis
- Analysis (resolved class graph, cycle detection, ring assignment) must be identical whether modules come from one or many projects
- The module-collection logic lives in a new `MultiModuleResolver` wrapper — analysis code never imports it

The **domain boundary** strengthens because:
- `ClassName` and `PackageName` remain the domain primitives — they don't carry module info
- Module info is an infrastructure concern added at the presentation layer
- If a class exists in two modules (same FQCN, different compilation), the resolver picks one (first wins + warning) or tags both — but the analysis layer never sees the ambiguity

**Implementation order**:

1. **Add `ModuleSourceSet` type** — replaces bare `SourceSet` in tagged-directory tuples. Add `name: String` field carrying the Gradle project path / Maven artifact ID.
2. **Extract `ModuleClassDirectoryProvider` interface** — single-module and multi-module implementations. Single-module delegates to `taggedClassDirectories()`; multi-module delegates to `MultiModuleResolver`.
3. **Build `MultiModuleResolver`** (Gradle + Maven variants) — collects from `rootProject.subprojects` / `session.getAllProjects()`, respects config includes/excludes.
4. **Wire `--multi-module` flag into all structural tasks** (Dsm, Cycles, Rings, Balance, MoveSuggest, Dead, Cohesion, IntegrationStrength, Distance, Volatility).
5. **Add module labels to formatters** — TEXT/JSON/LLM formatters render `module` prefix or field when multi-module is active. Start with `cnavDsm` (most visible impact), then `cnavCycles`, then `cnavRings`.
6. **Add `--modules` CLI shortcut** — `--modules=":shared,:service"` to select specific modules without config.
8. **Cross-module plan-file support** — move suggestions and execute-plan resolve target module from class location.

**Test strategy**:
- New `test-project-multi/` with 2-3 Gradle subprojects (`shared`, `service`, `web`) containing cross-module dependencies
- Verify cross-module cycles are detected (service → shared → service)
- Verify DSM shows inter-module cells
- Verify rings use cross-module deps for classification
- Verify dead code flagged only when no module references it
- Verify move-suggest across module boundaries

The test project itself becomes a forcing function for the formatter boundary — if formatters or analysis accidentally depend on single-module assumptions, the multi-module fixtures will catch it at compile time.

**Strengthens formatter and domain barrier** | **Value: high** | **Effort: high**

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

**`modules`** — **NOT DONE**, blocked on multi-module support (separate ACTIVE item above) not existing yet; no consumer to wire it into.

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

`cnavMoveSuggest` supports `--plan-file` but the `cnavAgentHelp` task-reference line for it and the global `--plan-file` param description don't list it among the plan-file-aware tasks. Add it to both. (Repeat finding — bears fixing.)

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

### New DANGER balance finding: root package → navigation.types
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
