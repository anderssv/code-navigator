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

### Replace regex JSON parsing with Jackson
**FUTURE** | **Value: low** | **Effort: low** | Source: internal

`PlanMutator.parseJson` currently uses regex extraction. Replace with Jackson (or kotlinx.serialization) for robustness. Add Jackson dependency or reuse one already on classpath via Maven plugin.

### ~~cnavExecutePlan — execute a plan file~~ — DONE
**DONE** | **Value: high** | **Effort: medium** | Source: design-discussion

Dedicated task (`cnavExecutePlan --plan-file=plan.json`) that reads a plan JSON and applies each move step sequentially using `MoveClassRewriter`. Class names are resolved to FQCNs upfront via the compiled class index (no fuzzy matching in rewriting logic). Supports `--preview` to dry-run.

Plan format: `[{"action":"move","type":"com.example.api.Dto","to":"com.example.service"}]`

### Consider: operation sequences as the primary interface
**FUTURE** | **Value: high** | **Effort: high** | Source: design-discussion

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
**FUTURE** | **Value: high** | **Effort: high** | Source: design-discussion

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

### `.cnav-config.json` — Ring hints to correct heuristic misclassifications
**ACTIVE** | **Value: high** | **Effort: medium** | Source: field-test(greitt, v0.1.106)

**Problem**: Emergent mode classifies rings by dependency shape (framework imports → higher ring). Infrastructure classes with no framework imports (serializers, generators, config classes, renderers) get placed in ring 0 alongside domain logic. Users can't correct this without a config file.

**Why not `cnavLayerCheck`'s format**: The old `.cnav-layers.json` defined layers as a linear stack with `peerLimit`, `testInfrastructure`, and allowed-dependency rules. That was an enforcement tool. This is a **hint** file — it nudges the classifier, it doesn't define architecture.

**Schema**:

```json
{
  "version": 1,
  "ringNames": [
    "domain", "port", "application", "infrastructure", "web-output", "web-input", "composition-root"
  ],
  "hints": {
    "domain": ["*Domain*", "*Event", "*Exception", "*Types"],
    "port": ["*Port", "*Repository", "*Client"],
    "application": ["*Service", "*UseCase", "*Orchestrator"],
    "infrastructure": ["*Impl", "*Config", "*Serializer", "*Generator", "*Renderer", "*Factory", "*Provider", "*Util*"],
    "web-output": ["*Page", "*Component*"],
    "web-input": ["*Route*", "*Routes", "*Controller", "*Endpoint"]
  },
  "overrides": {
    "no.mikill.greitt.web.RoutePaths": "web-input",
    "no.mikill.greitt.web.plugins.FileWatcher": "infrastructure"
  }
}
```

**Semantics**:

- **`ringNames`**: Labels for display. Instead of "Ring 0", "Ring 1", etc., show "domain", "port", etc. Order determines ring number (first = innermost). Defaults to a reasonable set if omitted.

- **`hints`**: Glob patterns matching simple class names (after stripping `Kt`/`Test` suffixes — same logic as the removed `cnavLayerCheck`). These set a **minimum ring** for matching classes. If the dependency graph calculates ring 0 but the class matches `*Serializer` (infrastructure ring 2), it gets promoted to ring 2. If the graph already places it at ring 3, it stays at ring 3 — hints never demote.

  A class matches a hint pattern → its minimum ring is the order of the hinted ring. Its actual ring = `max(graphRing, hintMinimum)`.

  The pattern list for a ring is checked in order and the first match wins. The `*` glob matches everything, so `"domain": ["*"]` would be the catch-all (match anything not caught by earlier patterns).

- **`overrides`**: FQCN-to-ring mappings for classes that don't follow naming patterns. Only needed for 1-2 edge cases per project. Takes precedence over hints.

**How the classifier changes**:

1. Run standard emergent detection (framework-import heuristic + SCC collapse + longest-path) → `rawRings`
2. For each class, check `overrides` first (FQCN match) → if found, use that ring
3. For each class, check `hints` (glob match) → if found, compute `max(rawRing, hintMinimum)`
4. If neither match, keep `rawRing`
5. Recompute ring labels from `ringNames` for display

**Effect on greitt**:

Without config: NanoidGenerator at ring 0, MarkdownRenderer at ring 0, QrCodeGenerator at ring 0, ApplicationConfigKt at ring 0.
With `hints: { "infrastructure": ["*Serializer", "*Generator", "*Renderer", "*Config", "*Impl"] }`:

| Class | Raw ring | Hint match | Final ring |
|-------|----------|------------|------------|
| NanoidGenerator | 0 | `*Generator` → infrastructure (2) | 2 |
| InstantDeserializer | 0 | `*Serializer` → infrastructure (2) | 2 |
| MarkdownRenderer | 0 | `*Renderer` → infrastructure (2) | 2 |
| QrCodeGenerator | 0 | `*Generator` → infrastructure (2) | 2 |
| ApplicationConfigKt | 0 | `*Config` → infrastructure (2) | 2 |
| PollsRepositoryImpl | 4 | `*Impl` → infrastructure (2) | 4 (already above minimum) |

**Implementation**:

1. Add `RingsHintsConfig` class — JSON parser for `cnav-config.json` (reuse `SimpleJson` from old `cnavLayerCheck` or use Jackson)
2. Add `--hints-file` param to `cnavRings` task (defaults to `cnav-config.json` at project root, or `.cnav/rings.json`)
3. In `ClassRingClassifier.classify()`, add a post-processing step that applies hints + overrides
4. Update `EmergentRingFormatter` to use `ringNames` for display labels
5. Add `--generate-hints` mode that analyzes current misclassifications and emits a suggested `cnav-config.json`:
   - Finds all classes in ring 0 with external deps matching framework-like patterns → suggests `infrastructure` hints
   - Finds all classes that are interfaces → suggests `port` hints
   - Emits a `cnav-config.json` with comments explaining each suggestion

**LLM workflow** (the primary user of this feature):

```
1. Run:  ./gradlew cnavRings --mode=emergent --scope=prod --format=llm
2. Review: scan "Ring 0" for classes that aren't domain (serializers, configs, generators, renderers)
3. Write:  create cnav-config.json with hints catching those patterns
4. Verify: re-run cnavRings to confirm misclassified classes moved to correct rings
```

The `--generate-hints` flag automates step 2-3. The LLM should:
  - Run `--generate-hints` first to get a starting config
  - Review the suggestions (the tool may flag things that are intentionally in ring 0)
  - Save to `cnav-config.json`, adjust any overrides, re-run

**Self-documenting output**: When `cnavRings` detects classes in ring 0 that match infrastructure-like naming patterns (e.g., `*Serializer`, `*Generator`, `*Config`), the LLM formatter should append a tip at the end of the output:

```
Tip: Some classes in ring 0 look like infrastructure (serializers, generators, configs).
To correct this, create cnav-config.json:
  { "hints": { "infrastructure": ["*Serializer", "*Generator", "*Config"] } }
See --generate-hints for a suggested starting config.
```

This ensures the LLM knows it can configure exceptions without reading docs or the plan. The tip only appears when heuristic misclassifications are detected (classes in ring 0 that match common infrastructure patterns like `*Config`, `*Serializer`, `*Generator`, `*Impl`).

**Example `--generate-hints` output**:

```
// Suggested cnav-config.json — review before using
// Based on 50 classes in ring 0 with infrastructure-like patterns:
{
  "version": 1,
  "hints": {
    "infrastructure": [
      "*Serializer",    // InstantDeserializer, InstantSerializer, LocalDateSerializer — 5 classes
      "*Generator",     // NanoidGenerator, QrCodeGenerator — 2 classes
      "*Renderer",      // MarkdownRenderer — 1 class
      "*Config",        // ApplicationConfig, DatabaseConfig, AuthenticationConfig — 3 classes
      "*Watcher"        // FileWatcher — 1 class
    ],
    "port": [
      "*Repository",    // PollsRepository, DevicesRepository — 2 classes
      "*Client",        // EmailClient — 1 class
      "*Port"           // PollMigrationPort — 1 class
    ]
  },
  "overrides": {
    "no.mikill.greitt.web.RoutePaths": "web-input",
    "no.mikill.greitt.web.util.DateUtilsKt": "infrastructure"
  }
}
```

**What hints DON'T do**:

- They don't define allowed dependencies. No `peerLimit`, no `fail-on=upward`, no layer rules. That's a separate concern for a future "enforcement" mode.
- They don't define architecture. They just fix the 5-10 heuristic blind spots per project.
- They don't replace the dependency graph. All ordering still comes from actual code structure.

### `cnav-config.json` — Extend to per-project task defaults
**ACTIVE** | **Value: high** | **Effort: medium** | Source: field-test(greitt, v0.1.106)

The `cnav-config.json` currently only serves ring hints. Many tasks require project-specific flags that LLM agents must repeat on every invocation. A shared `defaults` section would eliminate this friction:

```json
{
  "version": 1,
  "defaults": {
    "format": "llm",
    "scope": "prod",
    "rootPackage": "no.mikill.greitt",
    "packageFilter": "no.mikill.greitt",
    "ports": [".*Repository", ".*Client", ".*Port"],
    "maxFanIn": 10
  },
  "rings": {
    "ringNames": ["domain", "port", "application", "infrastructure", "web-output", "web-input", "composition-root"],
    "hints": {
      "infrastructure": ["*Serializer", "*Generator", "*Renderer", "*Config", "*Impl"],
      "port": ["*Repository", "*Client", "*Port"]
    },
    "overrides": {
      "no.mikill.greitt.web.RoutePaths": "web-input"
    }
  }
}
```

**Targets that benefit**:

| Target | Config key | What it replaces |
|--------|-----------|-----------------|
| All tasks | `defaults.format` | `--format=llm` on every call |
| All tasks | `defaults.scope` | `--scope=prod` on structural tasks |
| `cnavDsm`, `cnavCycles`, `cnavRings`, `cnavMetrics` | `defaults.packageFilter` | `--package-filter=...` |
| `cnavTestCoupling` | `defaults.ports` | `--ports=".*Repository\|.*Client"` |
| `cnavDead` | `defaults.excludeAnnotated` | `--exclude-annotated=...` |
| `cnavMoveSuggest` | `defaults.maxFanIn` | `--max-fan-in=...` |
| `cnavRings` | `rings.*` | Ring hints, names, overrides |

**Implementation**:

1. Extend `RingsHintsConfig` (or create `CnavConfig`) to parse a top-level `defaults` section
2. For each task config, apply defaults BEFORE CLI params — CLI params take precedence
3. Wire into `*Config.parse()` methods or a shared `ConfigDefaults` helper
4. `cnavTestCoupling` reads `ports` from config if `--ports` not provided
5. `cnavDead` reads `excludeAnnotated`/`treatAsDead` from config

**Benefit for LLM agents**: A single `cnav-config.json` at project root replaces the preamble pattern where agents ask "what's your package structure?" and users type the same 3-4 flags every session.

**Estimate**: 2-3 hours

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
**ACTIVE** | **Value: medium** | **Effort: low** | Source: field-test(greitt)

Now that `cnavMoveSuggest` detects structural supertype gravity (implements/extends), users want to simulate moves before executing. Currently `cnavDsm`, `cnavCycles`, `cnavRings`, `cnavSimulateMove`, `cnavMetrics`, and `cnavBalance` accept `--plan-file` for what-if simulation — `cnavMoveSuggest` should too.

Workflow:
1. Run `cnavMoveSuggest` → see "move `FakeRepo` to `polls`"
2. Create `plan.json`: `[{"action":"move","type":"fakes.FakeRepo","to":"polls"}]`
3. Run `cnavMoveSuggest --plan-file=plan.json` → see updated suggestion list
4. Iterate until satisfied
5. Execute with `cnavExecutePlan --plan-file=plan.json`

Also applies to `cnavSuggestStructure` and `cnavCohesion` — both consume the same dependency list and could benefit from plan simulation.

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
