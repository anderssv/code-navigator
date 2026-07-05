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

## Multi-module support

### Full multi-module analysis — aggregate class dirs from all project modules
**ACTIVE** | **Value: high** | **Effort: high** | Source: internal

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

### Replace regex JSON parsing with Jackson
**PARKED** | **Value: low** | **Effort: low** | Source: internal

`PlanMutator.parseJson` currently uses regex extraction. Replace with Jackson (or kotlinx.serialization) for robustness. Add Jackson dependency or reuse one already on classpath via Maven plugin. Low priority — no reported issues from regex approach.

### ~~cnavExecutePlan — execute a plan file~~ — DONE
**DONE** | **Value: high** | **Effort: medium** | Source: design-discussion

Dedicated task (`cnavExecutePlan --plan-file=plan.json`) that reads a plan JSON and applies each move step sequentially using `MoveClassRewriter`. Class names are resolved to FQCNs upfront via the compiled class index (no fuzzy matching in rewriting logic). Supports `--preview` to dry-run.

Plan format: `[{"action":"move","type":"com.example.api.Dto","to":"com.example.service"}]`

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

### `cnav-config.json` — consolidated project config (rings + modules + defaults)
**ACTIVE** | **Value: high** | **Effort: medium** | Source: field-test(greitt, v0.1.106)

Single config file with three top-level sections. Designed in one pass to avoid three parser iterations.

**Problem**: Three separate config needs — (1) ring hints to correct classifier blind spots, (2) multi-module module discovery, (3) per-task defaults to eliminate repeated CLI flags. Each was scoped as a separate feature; sharing one file means one parser, one schema, one UX.

**Schema**:
```json
{
  "version": 1,
  "defaults": {
    "format": "llm",
    "scope": "prod",
    "rootPackage": "no.mikill.greitt",
    "packageFilter": "no.mikill.greitt",
    "ports": [".*Repository", ".*Client", ".*Port"],
    "maxFanIn": 10,
    "excludeAnnotated": ["org.springframework.stereotype.Component"]
  },
  "modules": {
    "include": [":shared", ":service", ":web"],
    "exclude": [":integration-test"],
    "include-regex": ".*-module$",
    "auto-discover": true
  },
  "rings": {
    "ringNames": ["domain", "port", "application", "infrastructure", "web-output", "web-input", "composition-root"],
    "hints": {
      "domain": ["*Domain*", "*Event", "*Exception", "*Types"],
      "port": ["*Port", "*Repository", "*Client"],
      "application": ["*Service", "*UseCase", "*Orchestrator"],
      "infrastructure": ["*Impl", "*Config", "*Serializer", "*Generator", "*Renderer", "*Factory", "*Provider", "*Util*"],
      "web-output": ["*Page", "*Component*"],
      "web-input": ["*Route*", "*Routes", "*Controller", "*Endpoint"]
    },
    "overrides": {
      "no.mikill.greitt.web.RoutePaths": "web-input"
    }
  }
}
```

**Sections**:

- **`defaults`** — Per-task CLI defaults applied before explicit params. Targets: `format` (all), `scope` (all structural), `packageFilter` (Dsm/Cycles/Rings/Metrics), `ports` (TestCoupling), `excludeAnnotated` (Dead), `maxFanIn` (MoveSuggest). **Concern**: implicit defaults hide active config — all output must show active defaults when config is in use.

- **`modules`** — Multi-module include/exclude lists. `include` = explicit Gradle project paths or Maven artifact IDs. `exclude` = skip these. `include-regex` = pattern match. `auto-discover` = scan all subprojects that have the plugin applied.

- **`rings`** — Ring hints and overrides (see semantics below). `ringNames` sets display labels. `hints` sets minimum ring by naming pattern. `overrides` sets FQCN-to-ring overrides.

**Ring hints semantics**:

- **`hints`**: Glob patterns matching simple class names (after stripping `Kt`/`Test` suffixes). Set a **minimum ring** for matching classes. If the graph calculates ring 0 but the class matches `*Serializer` (ring 2), it gets promoted to ring 2. Hints never demote. Actual ring = `max(graphRing, hintMinimum)`.
- **`overrides`**: FQCN-to-ring mappings. Take precedence over hints. Only needed for 1-2 edge cases per project.
- **`ringNames`**: Display labels for rings. Order determines ring number (first = innermost).

**How the classifier changes**:

1. Run emergent detection → `rawRings`
2. Check `overrides` → if found, use that ring
3. Check `hints` → if found, `max(rawRing, hintMinimum)`
4. Otherwise keep `rawRing`

**Implementation**:

1. Add `CnavConfig` class — single JSON parser (Jackson or kotlinx.serialization). Parse all three sections.
2. Add `--config-file` param to tasks (defaults to `cnav-config.json` at project root).
3. Wire `defaults` into `*Config.parse()` methods — apply defaults BEFORE CLI params, CLI takes precedence.
4. Wire `modules` into `MultiModuleResolver` include/exclude logic.
5. Wire `rings` into `ClassRingClassifier.classify()` post-processing.
6. Add `--generate-hints` mode to `cnavRings` — analyzes misclassifications and emits a suggested config.
7. Always show active defaults in output header when config is loaded.

**Ring hints — what they DON'T do**: No `peerLimit`, no `fail-on=upward`, no layer rules. They only fix heuristic blind spots. All ordering still comes from actual code structure.
### High violation count warning for `cnavRings`
**PARKED** | **Value: low** | **Effort: low** | Source: internal

When violation count exceeds a threshold (>50), add a note: "High violation count may indicate the project doesn't follow concentric ring architecture. Consider `--scope=prod` or `cnavLayerCheck` with explicit layer config."

---

## Refactoring operations

### ~~cnavMovePackage — batch move all classes in a package~~ — DONE (v0.1.105-SNAPSHOT)
**DONE** | **Value: high** | **Effort: low** | Source: field-test(bass-ra)

Implemented `cnavMovePackage --from-package=<pkg> --to-package=<pkg>` (Gradle + Maven). Scans project classes, filters by source package, then iterates `MoveClassWorkAction` for each class. Supports `--preview`. Reuses `ExecutePlanFormatter` for consistent batch output.

**Known limitation**: Shares the same OpenRewrite worker metaspace issue as `cnavExecutePlan` — packages with 5+ classes may hit `OutOfMemoryError: Metaspace` with default JVM settings. Workaround: increase `org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=1g` in `gradle.properties`.

### cnavMoveFunction — move top-level Kotlin functions
**PARKED** | **Value: low** | **Effort: high** | Source: field-test(bass-ra)

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
**FUTURE** | **Value: medium** | **Effort: high** | Source: internal

`@RestController` is meta-annotated with `@Controller` which is meta-annotated with `@Component`. Currently, excluding `Component` does NOT exclude `@RestController`.

- In `AnnotationExtractor`, scan annotation `.class` files from classpath JARs and resolve meta-annotations transitively.
- Covers custom stereotype annotations automatically.
- **Prerequisite**: `cnavJar` (classpath resolution infrastructure) — non-trivial to resolve annotation classes from JARs. Pending classpath JAR infrastructure.

### Transitive dead code detection
**FUTURE** | **Value: medium** | **Effort: high** | Source: user-feedback(v0.38)

A method is "transitively dead" if all its callers are themselves dead. Iterate until fixed point.
- Confidence levels: `DEAD` (zero callers), `TRANSITIVELY_DEAD` (all callers dead), `TEST_ONLY` (only test callers), `SHRINKING` (declining caller trend).

### `cnavDead` baseline diff — confirm cleanup was complete
**FUTURE** | **Value: low** | **Effort: low** | Source: internal

`--baseline=<path>` parameter pointing to saved JSON output. On re-run, show diff. Alternative: just use `jq` to diff JSON externally.

### Dead code: flag methods called only from test scope
**ACTIVE** | **Value: medium** | **Effort: low** | Source: internal

Use source set tagging (already available) to identify production methods/classes whose only callers are in the test source set. Quick win — source set tagging already exists, just needs a new confidence label and output filter.

### Per-package health dashboard
**FUTURE** | **Value: low** | **Effort: medium** | Source: internal

Aggregate all per-package metrics into a single view: volatility, coupling strength breakdown, distance profile, cycle involvement, balance assessment. Could be a mode of `cnavMetrics` (`--by-package=true`) or separate `cnavPackageHealth` task.

### `cnavClassMetrics` — per-class cohesion + CK metrics
**ACTIVE** | **Value: high** | **Effort: medium** | Source: repowise comparison

Single ASM visitor pass, per-class output: TCC/LCC cohesion (field-access graph) + WMC/CBO/DIT (Chidamber & Kemerer).

**Problem**: cnavCohesion measures package-level cohesion (internal/total edges per package). This misses within-class structure — a class with 15 methods where only 3 share field access is cohesively weak but invisible at the package level. repowise demonstrated per-class metrics as a working approach.

**Metrics**:
- **TCC (Tight Class Cohesion)**: fraction of directly connected method pairs (methods accessing ≥1 common field). Low = class does too many unrelated things.
- **LCC (Loose Class Cohesion)**: fraction of directly or transitively connected method pairs.
- **WMC (Weighted Method Count)**: sum of McCabe cyclomatic complexity across all methods. Higher = harder to test.
- **CBO (Coupling Between Objects)**: count of distinct types referenced (ex-JDK/stdlib). Higher = more context needed.
- **DIT (Depth of Inheritance Tree)**: superclass chain length from `Object`/`Any`. Deeper = more inherited behavior.

**Implementation** — two collectors in one ASM pass:

1. **`FieldAccessAnalyzer`** — per method, track accessed instance fields. Exclude constructors, static initializers, synthetic accessors.
2. **`CohesionGraphBuilder`** — adjacency matrix: edge if `accessedFields(A) ∩ accessedFields(B) ≠ ∅`. TCC = direct / total pairs, LCC = transitive closure.
3. **WMC** — count branches (`if`, `when` arms, `for`, `while`, `catch`, `&&`, `||`, `?:`) per method. Base = 1, +1 per branch.
4. **CBO** — distinct types in field/method/return/local signatures, ex-JDK (`java.lang`, `java.util`, Kotlin stdlib) and primitives.
5. **DIT** — walk superclass chain via classpath. Interfaces excluded.

**Result**:
```kotlin
data class ClassMetricsResult(
    val className: ClassName,
    val packageName: PackageName,
    val totalMethods: Int,
    val tcc: Double, val lcc: Double, val verdict: CohesionVerdict,
    val wmc: Int, val cbo: Int, val dit: Int
)
```

**Verdict** (cohesion): HIGH (TCC ≥ 0.7) / MEDIUM (0.4–0.7) / LOW (TCC < 0.4, LCC ≥ 0.7) / MONOLITH (TCC < 0.4, LCC < 0.7).

**Filtering**: `--min-methods=5`, `--min-tcc=0.0`, `--max-wmc=20`, `--max-cbo=10`.

**TEXT**:
```
Class                        TCC    LCC   Vldct  WMC  CBO  DIT
com.example.OrderService     0.12  0.30  MNLTH  34   12   3
com.example.OrderValidator   0.70  0.90  HIGH    8    5   2
```

**JSON**: per-entry fields `tcc`, `lcc`, `verdict`, `wmc`, `cbo`, `dit`.

**Integration**: New task `cnavClassMetrics`. Self-contained ASM visitor, no dependency on `DsmDependencyExtractor` or `CallGraphBuilder`. ~200 lines core + formatters.

**Improvement over repowise**: filters constructors/synthetic accessors (repowise inflated TCC), adds transitive LCC detection, excludes JDK from CBO, counts only `superclass` for DIT.

---

## Standalone new tasks

### `cnavConverge` — composite architectural signal (intersect + risk scoring)
**ACTIVE** | **Value: high** | **Effort: high** | Source: field-test(bass-ra)

Two analysis modes producing ranked problem lists:

**Mode 1 — Intersection** (primary): Run cnavCycles (structure), cnavRings (layering), cnavCoupling (git). Report only edges where ≥2 independent views agree. Classify: ACT NOW (cycle ∩ high-coupling), LATENT (cycle ∩ low-coupling), MISSING ABSTRACTION (clean ∩ high-coupling), IGNORE.

**Mode 2 — Risk scoring** (secondary): `risk = change_frequency × complexity × coupling_degree`. Combines Hotspot, Churn, and Complexity builders. Low effort — all inputs exist. Available via `cnavConverge --mode=risk` or standalone when only risk ordering is needed.

The intersection mode is more robust than weighted averaging on small/nested/package-by-feature codebases. Reference: bass-ra `architecture-signal-recipe.md`. Needs scope alignment (coupling is path-based, not source-set-based — see cnavCoupling --scope item).

### `cnavTestHealth` — verify all test methods actually ran
**ACTIVE** | **Value: medium** | **Effort: medium** | Source: user-feedback

Count `@Test`-annotated methods from bytecode, compare against JUnit XML results, flag the delta. Catches silently skipped tests (e.g., non-`Unit` return types).

### `cnavTestCoverage` — per-test-class coverage proximity analysis
**REJECTED** | **Value: low** | **Effort: high** | Source: internal

Identify production classes only tested "at distance". Requires JaCoCo integration (TestExecutionListener + per-test exec files). High effort for specialized use case. JaCoCo integration scope is too large for the value. Reject — not worth the complexity.

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

### `cnavTestCoupling` — remaining improvements
**PARKED** | **Value: medium** | **Effort: low** | Source: field-test(greitt+terms-and-conditions)

- **DAO test threshold**: adapter tests where port calls are <50% due to assertion noise. Consider counting only non-framework calls in denominator.
- **Concise "all clear" output**: When no violations found, one-liner confirmation instead of ~15 lines of guidance.

### `cnavContextUsage` — verify consistent test context usage
**PARKED** | **Value: low** | **Effort: medium** | Source: internal

Check that test classes use a shared test context rather than constructing dependencies ad-hoc.

### `cnavInterfacePurity` — check interfaces use domain types
**PARKED** | **Value: low** | **Effort: medium** | Source: internal

For interfaces matching a pattern, check method signatures reference only domain-package types (not DTO/infrastructure types).

---

## Output & UX

### Interpretation/hint fields in JSON output
**ACTIVE** | **Value: medium** | **Effort: medium** | Source: field-test(bass-ra)

Interpretation footers and notices (e.g. the `BALANCE_INTERPRETATION`/`COUPLING_INTERPRETATION` strings, the `test-involvement: N of M …` line, the package-mode notice) are only appended to TEXT and LLM output. JSON consumers get none of this guidance. Add structured equivalents to the JSON output so non-TEXT/LLM clients can react to the same signals — e.g. a top-level `interpretation` string and/or a `notices: []` array, or per-result fields like `testInvolvement: { testInvolved, total }`. This keeps the JSON contract machine-readable while carrying the same steer the LLM/TEXT formats already provide.

Applies to: `cnavBalance`, `cnavCoupling`, `cnavRings`, `cnavCycles` (any task that appends an interpretation/notice today). Decide on a consistent shape (wrapper object vs. extra fields) before implementing.

### `test-involvement` line for cnavBalance
**PARKED** | **Value: low** | **Effort: medium** | Source: field-test(bass-ra)

The `test-involvement: N of M … involve test sources` line (printed when scope=all) was added to cnavCycles and cnavRings (emergent), which retain class-level edges end-to-end. cnavBalance was deferred: its public result (`BalanceEntry`/`BalanceOutput`) is package-level and `BalanceOrchestrator` consumes the class-level `extractResult.data` internally without surfacing it. To add the line for Balance, thread a test-involvement count out of the orchestrator (it already has the class-level deps + can build a `SourceSetResolver` from the unfiltered tagged dirs) into `BalanceOutput`, then render via `TestInvolvement.notice(...)`. Do this together with the JSON-hints refactor and the formatting-boundary cleanup so the count is rendered by the formatter, not concatenated in the task.

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

### ~~Refactoring result LLM hints for follow-up actions~~ — DONE (v0.1.105-SNAPSHOT)
**DONE** | **Value: medium** | **Effort: low** | Source: internal

Implemented `RefactoringHints` helper in core. Each formatter's LLM output now includes task-specific follow-up suggestions after applied operations (non-preview). Move/execute-plan suggest structural verification (`cnavPackageDeps`, `cnavRings`, `cnavCycles`). Rename/delete/change-signature suggest `cnavFindUsages` for verification.

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

### `cnavMoveSuggest` + `--plan-file` support
**DONE** | **Value: medium** | **Effort: low** | Source: field-test(greitt)

Implemented for both Gradle and Maven. `MoveSuggestTask`/`MoveSuggestMojo` now extract via `PackageHealthExtractor`, mutate the dependency list and project class set through `PlanMutator`, then feed the mutated `PackageHealthExtraction` into `MoveSuggestOrchestrator.fromExtraction`.

Important nuance found during implementation: `PlanMutator.apply()` used to unconditionally drop edges that land in the same package after a simulated move — correct for cycle/DSM/ring analysis, but wrong for move-suggest (extracted with `includeSamePackage=true`), since those intra-package edges are exactly what scores gravity at the destination. Added a `dropSamePackageEdges` parameter (default `true`, preserving existing callers) and pass `false` from move-suggest.

Also found: `cnavCycles`/`cnavRings`/`cnavDsm`/`cnavMetrics` Maven mojos accept `--plan-file` as a CLI property but never actually apply the mutation to the dependency graph — silent no-op on the Maven side only (Gradle tasks apply it correctly via `CodeNavigatorTask.applyPlan`). See new item below.

Still open: `cnavSuggestStructure` and `cnavCohesion` — both consume the same dependency list and could benefit from the same plan-simulation wiring, using the same `dropSamePackageEdges=false` approach.

### Fix Maven `--plan-file` no-op on cnavCycles/cnavRings/cnavDsm/cnavMetrics
**DONE** | **Value: medium** | **Effort: low** | Source: internal (found while implementing cnavMoveSuggest plan-file support)

Fixed. Added shared `loadPlanSteps()`/`applyPlanFile()` helpers to `MavenSupport.kt` (mirroring Gradle's `CodeNavigatorTask.applyPlan`, since Maven mojos have no shared base task) and wired them into `CyclesMojo`, `DsmMojo`, `MetricsMojo` (simple `applyPlanFile(dependencies, planFile, log)` before matrix/cycle building) and `RingsMojo` (both `--mode=package` via `applyPlanFile`, and `--mode=emergent` via explicit `PlanMutator.apply`/`applyToClassSet` on both the project and external dependency extractions, since emergent mode needs the mutated class set for its project/external split). `MoveSuggestMojo` refactored to reuse `loadPlanSteps()` instead of parsing inline.

### `cnavMoveSuggest`: structural supertype gravity
~~**ACTIVE**~~ **DONE (v0.1.106-SNAPSHOT)** | **Value: high** | **Effort: low** | Source: field-test(greitt)

Detects `implements`/`extends` relationships as additional dependency gravity in `MoveSuggester`. These edges bypass the ubiquitous-type filter, so fakes (which primarily depend on interfaces that many classes use) are now correctly suggested for co-location with their interface's package.

Implementation: `DsmDependencyExtractor` extracts structural supertypes via a dedicated ASM pass. `MoveSuggester.suggest()` accepts them as a separate parameter and applies weight 3 per structural edge, immune to `maxFanIn` filtering.

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
**DONE** | **Value: high** | **Effort: low** | Source: internal

Implemented for `cnavCycles` (`--fail-on-violation=true --max-cycles=0`) and `cnavRings` (`--fail-on-violation=true --max-violations=0`, both `--mode=emergent` and `--mode=package`). `cnavLayerCheck` was removed in v0.1.97 (superseded by `cnavRings`), so it's not part of this. `cnavCohesion` excluded — it produces a ranked score list, not a violation count, so "fail on violation" doesn't map cleanly onto it.

Gradle throws `GradleException`, Maven throws `MojoFailureException`, after printing the normal output.

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

### Restore formatting-layer boundary — outer layers pass result objects, formatters emit output
**ACTIVE** | **Value: high** | **Effort: medium** | Source: internal

The layering rule (parsing → resolution → formatting) has eroded at the task/mojo boundary: outer layers (Gradle tasks, Maven mojos) increasingly assemble output *strings* by concatenating onto formatter output instead of passing result structures to formatters and letting the formatters render every attribute.

Concrete offenders introduced/observed:
- `RingsTask`/`RingsMojo` and `CyclesTask`/`CyclesMojo` string-concat the `test-involvement` notice and `PACKAGE_MODE_NOTICE` onto formatter output (`"$body\n\n$testNotice"`), and branch per `OutputFormat` in the task.
- Rings emergent/package output is returned as a pre-rendered `String` from the task and echoed identically for TEXT/JSON/LLM — the task, not a formatter, owns the format.

Target design:
- Tasks/mojos build a result object (e.g. carrying violations + `testInvolvement` counts + applicable notices) and hand it to the formatter.
- Formatters own ALL rendering, including notices/interpretations, per output format (so JSON can emit structured fields — see "Interpretation/hint fields in JSON output").
- Outer layers must not receive formatted text from deeper layers and must not append to it.

This pairs with the JSON-hints item and the JsonFormatter/LlmFormatter split. Do this refactor before adding more notice/interpretation surfaces, to stop the bleed.

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
