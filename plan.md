# Plan

Items are grouped by theme. Within each group, items are ordered for sequential execution.
Value and effort are qualitative assessments to aid prioritization, not estimates.

---

---

## cnavLayerCheck: concentric ring / hexagonal architecture support

### Support hexagonal (concentric ring) layer definitions instead of only linear stacks

**Value: high** | **Effort: medium**

From field test: `cnavLayerCheck` currently models layers as a linear stack where layer N can only depend on layers below it. This doesn't map well to hexagonal architecture, which uses concentric rings:

- **Ring 0 (innermost)**: Domain — pure business logic, no outward dependencies
- **Ring 1**: Ports/services — interfaces the domain exposes and consumes
- **Ring 2**: Adapters — driving adapters (HTTP routes/pages) and driven adapters (DB, email clients)
- **Ring 3 (outermost)**: Composition root — wires everything together

Key differences from a linear stack:
1. Multiple packages at the same ring level should NOT depend on each other (adapters shouldn't cross-reference)
2. Dependencies must flow inward only (outer ring → inner ring), never outward
3. The composition root is special — it's allowed to reference all rings (it wires adapters to ports)
4. Peer dependencies between adapters at the same ring are violations (unlike a stack where "same layer" might be allowed with `peerLimit`)

**Proposed config format** (strawman):
```json
{
  "mode": "hexagonal",
  "rings": [
    { "name": "domain", "packages": ["*.polls", "*.polls.calendar", "*.util", "*.validation"] },
    { "name": "ports", "packages": ["*.context", "*.auth", "*.email"] },
    { "name": "adapters", "packages": ["*.admin", "*.participant", "*.creation", "*.mypolls", "*.static", "*.transfer", "*.web", "*.web.*", "*.css", "*.db"], "peerForbidden": true },
    { "name": "composition", "packages": ["*.Application*"], "canReferenceAll": true }
  ]
}
```

Rules:
- Ring N can depend on ring N-1, N-2, ... (inward only)
- Ring N cannot depend on ring N+1, N+2, ... (outward = violation)
- Peers within the same ring are forbidden by default in hexagonal mode (`peerForbidden: true`)
- Composition root exempted from all rules (or uses `canReferenceAll`)

### Sectors within rings

**Value: medium** | **Effort: medium**

Consider distinguishing **sectors** (role-based groupings) within the same ring. In hexagonal architecture, the adapter ring has two distinct sectors:
- **Driving adapters (incoming)**: web controllers, API routes, CLI handlers — they call inward
- **Driven adapters (outgoing)**: database clients, file writers, email senders — they are called from inward

These sectors have different coupling rules:
- Driving adapters should NOT depend on driven adapters (and vice versa)
- Both sectors depend inward on ports, but they don't interact with each other

Currently `cnavRings` treats all packages at the same ring level uniformly. Adding sector awareness would:
1. Catch violations like a web route directly calling a `DatabaseClient` (bypassing ports)
2. Make the ring output more readable — group by sector instead of flat list
3. Reduce false-positive peer violations (two driving adapters referencing the same port isn't a problem)

**Open questions:**
- Can sectors be auto-detected (e.g., driving adapters have no incoming project edges, driven adapters have no outgoing project edges)?
- Or does this require config: `{ "ring": "adapters", "sectors": [{ "name": "incoming", "packages": [...] }, { "name": "outgoing", "packages": [...] }] }`?
- Is this already partially handled by `peerForbidden`? Peer violations between adapters *are* flagged, but without distinguishing why they're wrong (cross-sector vs. same-sector coupling).

---

## cnavMoveSuggest improvements

Reduce false positives from structural patterns that look like misplacement but are intentional.

### Symbol-level move suggestions (intra-file analysis)

**Value: medium** | **Effort: high**

From field test: `DateSelection` is a class inside `web/RouteUtils.kt` — a grab-bag file with multiple unrelated declarations (`withAdminPoll`, `getUUID`, `HtmlRenderUtils`, etc.). An independent code review correctly identified `DateSelection` as domain logic misplaced in a web infrastructure file. But `cnavMoveSuggest` couldn't detect this because it analyzes at the file/facade level (`RouteUtilsKt`), not individual symbols within a file.

Currently cnav treats multi-declaration files as a single unit. To catch intra-file misplacement, it would need to:
1. Identify individual top-level declarations (classes, functions, properties) within a `*Kt` facade
2. Compute edge gravity per symbol, not per file
3. Suggest extracting specific symbols to a different package

This is significantly more complex than file-level analysis — requires mapping bytecode members back to individual source declarations. May not be worth the effort unless combined with a `cnavExtractSymbol` refactoring command.

---

## Dead code improvements

Related improvements to `cnavDead`. Ordered by dependency — ConfidenceScorer extraction enables the others.

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

### Cycle fix suggestions in DSM

**Value: high** | **Effort: medium**

The DSM tells you which cycles exist, but not how to fix them. When `-Pcycles=true`, also show which specific class-level edges would need to move to break the cycle, and suggest which direction the dependency should flow.

- **Prerequisite**: Benefits from `cnavWhyDepends` infrastructure — same edge-explanation logic.
- **Separate from DSM what-if**: What-if simulation is a distinct, higher-effort feature. Evaluate need after cycle fix suggestions ship.

### High violation count warning for `cnavRings`

**Value: low** | **Effort: low**

From self-review: `cnavRings` produced 116 peer violations. When violation count exceeds a threshold (e.g., >50), add a note: "High violation count may indicate the project doesn't follow concentric ring architecture, or that test edges are collapsing the ring structure. Consider `cnavRings -Pscope=prod` or `cnavLayerCheck` with explicit layer config."

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

---

## Find-usages output quality

From field test (v0.1.72): `cnavFindUsages` output is noisy at bytecode level. A single logical call site (e.g., constructing `RAClientImpl`) produces 3-4 lines (`.new` type ref + `.<init>` method call + `.checkcast` + field access). Lambda classes like `MonitorService$getCurrentStatus$2$raClientStatusDeferred$1` obscure the actual caller. Users pipe through `grep -v` to find meaningful results.

Ordered by dependency — collapsing enables the summary mode, and smart usages builds on the cleaner output.

---

## Output & UX improvements

Improvements to task output, discoverability, and agent experience.

### Refactoring result LLM hints for follow-up actions

**Value: high** | **Effort: low**

After a successful refactoring (rename, move, etc.), the LLM output should include contextual hints suggesting further analysis or refactoring steps using cnav. Examples:
- After `cnavRenameMethod`: suggest `cnavFindUsages` to verify no remaining references, or `cnavRenameParam` if related parameters should also be renamed.
- After `cnavMoveClass`: suggest `cnavPackageDeps` to verify the move improved structure, or `cnavCycles` to check for new cycles.
- After any refactoring: suggest running tests, and hint at related refactorings (e.g., "consider `cnavRenameProperty` for fields that reference the old name").

This helps agents chain operations and discover related tasks they might not know about.

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

### `cnavBalance` volatility values lack context

**Value: low** | **Effort: low**

From self-review (v0.1.83): `cnavBalance` output showed `volatility=246/0` for a DANGER entry. The raw number is meaningless without knowing the scale. Is 246 high?

- **Option A**: Show percentile rank — "volatility=246 (top 5%)" or "HIGH/MEDIUM/LOW" verdict
- **Option B**: Show relative to project mean — "volatility=246 (12x avg)"
- **Option C**: Include the interpretation in the `withInterpretation()` text — "volatility numbers represent total git churn across all files in the package"

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

### Review implementations for spread logic

**Value: high** | **Effort: medium**

Several tasks (especially refactoring and composite analysis) contain logic that spans multiple concerns in a single function. The principle should be: an orchestrator calls single-purpose tasks that are as specific (and reusable) as possible.

Review areas:
- `RenameMethodRewriter` / `RenameMethodEditor`: location finding + PSI editing should be clearly separated (partially done with `RenameLocationFinder`)
- `MoveClassRewriter`: import updating, content extraction, file writing — are these reusable?
- DSM orchestrator: does it compose focused builders, or does it inline resolution logic?
- Formatter classes: some may contain query logic that belongs in builders

Goal: each unit does one thing; composition happens at the orchestrator level. This makes individual steps testable, cacheable, and reusable across tasks.

### Evaluate other JVM languages to support

**Value: medium** | **Effort: low (research)**

With the `LanguageRenameRewriter` abstraction in place (v0.1.94), adding new languages is straightforward — implement the interface and register in `RenameMethodEditor`. Java support is done. Evaluate:

- **Groovy** — common in Gradle build scripts and older JVM projects. IntelliJ Groovy PSI may be available.
- **Scala** — has its own compiler/PSI ecosystem; likely high effort unless a suitable library exists.

Criteria: Is PSI available in `kotlin-compiler-embeddable` or a lightweight dep? Is the language common enough in projects that also use Kotlin?

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

### Break `formatting` ↔ `navigation.relations` cycle

**Value: medium** | **Effort: low**

The only remaining production cycle: `formatting.JsonFormatter`/`LlmFormatter` → 28 classes in `navigation.relations`, reverse edge caused by `UsageFormatterTest` importing `formatting.JsonFormatter` and `formatting.LlmFormatter`.

- **Action**: Move `UsageFormatterTest` to the `formatting` test package (it tests formatter output, not usage scanning logic).

### Move misplaced root-package test classes

**Value: low** | **Effort: low**

Several test classes in the root `no.f12.codenavigator` test package belong in sub-packages:
- `ClassFileStalenessTest` → `registry`
- `TaskDefTest`, `TaskRegistryTest`, `ParamDefTest`, `UsageExampleTest` → `registry`
- `CacheFreshnessTest` → `navigation.cache`
- `TableFormatterTest` → `formatting`
- `IntegrationTest` → `navigation.relations.callgraph`

Part of ongoing alignment with production package structure.

### Fix DANGER balance: root package → callgraph/implementors

**Value: medium** | **Effort: low**

Self-analysis (v0.1.83) found 2 DANGER entries: `no.f12.codenavigator` → `navigation.relations.callgraph` and → `navigation.relations.implementors` (FUNCTIONAL strength, distance=3, high volatility=246). The root package contains `AgentHelpText`, `HelpText`, and registry-related classes that reference concrete callgraph/implementor types.

- **Investigate**: Which classes in the root package cause these edges? Likely `AgentHelpText` or test classes. May resolve naturally when misplaced test classes are moved.

### Split JsonFormatter and LlmFormatter per-feature

**Value: high** | **Effort: medium**

Self-analysis (v0.1.83) confirms: `JsonFormatter` (364 outgoing, 77 referenced types, fanIn=261) and `LlmFormatter` (similar scale) are the highest-complexity production classes. Package cohesion for `formatting` is 0.03 (nearly zero internal collaboration). Both are top-5 hotspots (45+ revisions each). They change together 96% of the time.

- **Approach**: Split into per-feature formatters (e.g., `CallTreeJsonFormatter`, `DeadCodeJsonFormatter`). Top-level formatters become thin dispatchers.
- **Ordering**: `LlmFormatter` first (primary agent-facing format), then `JsonFormatter`. `TableFormatter` is smaller and can follow later.
- **Benefits**: Adding a new feature means adding a new formatter file, not editing a shared god class. Cohesion of `formatting` package improves dramatically.

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
- **~~`cnavFindCallers` class-match UX hint~~** — DONE (v0.1.80): `CallGraph.findMethods()` uses `Regex.containsMatchIn` on `qualifiedName` (className.methodName). Pattern "Parser" matches every method in `Parser` class, producing separate caller trees per method. User expected "who references Parser as a type" which is `cnavFindUsages -Ptype=Parser`. Fix: when pattern matches only class-name portions of multiple methods in the same class, add a hint suggesting `cnavFindUsages -Ptype=`. v0.1.47 field test.
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

## Task context guidance (generic mechanism)

### `TaskGuidance` — structured context output for all tasks

**Value: high** | **Effort: medium**

When an LLM (or user) runs a task without required context parameters, or with default parameters, the output should include guidance explaining what the task checks, what parameters make sense, and how to determine the right values for the specific project. This is different from existing mechanisms:

- `withInterpretation()` — explains results after the fact
- `noResultsHints` — explains why nothing was found
- `AgentHelpText` — general workflow guidance

The new mechanism is **task context guidance**: "here's what this task needs to know about your project, here's how to figure it out, and here's what the output means."

**Design**:

```kotlin
data class TaskGuidance(
    val purpose: String,          // What this task checks and why
    val parameterGuidance: String, // How to determine the right parameter values
    val interpretation: String,   // How to read the results (replaces current INTERPRETATION constants)
)
```

**Principle**: Avoid default values on `TaskGuidance` fields. Set explicitly at call sites if not resolved from a source. This makes it clear when guidance text is intentionally missing vs accidentally omitted.

Each task defines a `TaskGuidance` instance. The same text is reused in:
1. **LLM output header** — always shown when `format=llm`, before the results
2. **AgentHelpText** — referenced in the task's section
3. **No-params fallback** — when the task requires project-specific params and none are given, print the guidance and stop (or proceed with detected defaults and note what was assumed)

This replaces the current scattered `internal const val *_INTERPRETATION` strings in `LlmFormatter` with a single source of truth per task, accessible from formatters, help text, and orchestrators.

**Migration**: Existing `*_INTERPRETATION` constants move into `TaskGuidance.interpretation` on each task's config/orchestrator. Existing `noResultsHints` become part of the guidance or remain separate (they're conditional on empty results).

---

## TDD practice enforcement

Tasks to help teams enforce the TDD triad: Test Setup, Fakes, and Testing Through the Domain.

### `cnavFakeCoverage` — verify all ports have fakes

**Value: high** | **Effort: medium**

Scan interfaces matching a pattern (e.g., `*Repository`, `*Client`), check that each has at least one implementation in the test source set. Report missing fakes.

- **Parameters**: `-Ppattern=<regex>` (interface name filter, default `".*Repository|.*Client"`), `-Pformat=text|llm|json`
- **Builder**: Use `InterfaceRegistry` to find all interfaces matching pattern. For each, check if any implementor is in the test source set. Report interfaces with no test implementation.
- **Output**: List of interfaces missing a fake, plus summary (e.g., "12/15 ports have fakes, 3 missing").
- **Why**: Enforces "fake everything" principle. Teams adopting fakes can run this in CI to prevent regression.

### `cnavTestCoupling` — detect tests bypassing the domain

**Value: high** | **Effort: high**

Analyze test code's call graph: if test methods directly call repository/adapter mutation methods (`add*`, `save*`, `store*`, `insert*`) instead of going through service-layer methods, flag them as "data-oriented setup" that violates Testing Through the Domain.

- **Parameters**: `-Pservice-pattern=<regex>` (service layer, default `".*Service"`), `-Prepo-pattern=<regex>` (repos/adapters, default `".*Repository|.*Client"`), `-Pmutation-pattern=<regex>` (mutation methods, default `"add.*|save.*|store.*|insert.*|update.*|delete.*"`), `-Pformat=text|llm|json`
- **Builder**: From test source set call graph, find test methods that call repo/adapter mutation methods directly (not via a service). Compare against test methods that set up state through service calls.
- **Output**: Per-test-class report showing which tests use data-oriented vs domain-oriented setup. Confidence score based on ratio.
- **Heuristics**: Calls to repo methods inside a helper named `*setup*` or `*before*` are weighted differently. Calls to repo `get*`/`find*` for assertions are not flagged (reading state to verify is fine).
- **Why**: Directly enforces Testing Through the Domain. The key insight: `test → repo.save()` = smell; `test → service.register()` = good.

#### Field feedback (greitt run)

All results came back as MIXED with no differentiation. Key improvements needed:

1. ~~**Distinguish adapter tests from domain tests**~~ ✅ DONE (v0.1.85) — Behavioral detection: if primary callee (>50%) is a port implementor OR port interface with 3+ calls, classify as ADAPTER_TEST.

2. ~~**Separate fake setup calls from behavioral calls**~~ ✅ DONE (pre-existing) — Only methods declared on the port interface are flagged. Fake-only methods (`willReturn`, `failOnNext`) are never violations.

3. ~~**Richer verdict taxonomy**~~ ✅ DONE (v0.1.85) — ADAPTER_TEST / DOMAIN_ORIENTED / MIXED / DATA_ORIENTED + confidence score (port-calls / total-calls ratio).

4. ~~**Exclusion parameter**~~ ✅ DONE (v0.1.85) — `-Pexclude=".*ImplTest|.*Fake|.*TestExtensions"` filters test classes by regex.

5. ~~**Show actual calls in detail mode**~~ ✅ DONE (v0.1.85) — `-Pdetail=true` shows per-call breakdown:
   ```
   SearchServiceTest  verdict=MIXED  confidence=0.25
     testSearch → RARepository.search [PORT]
   ```

#### Additional improvements (v0.1.85)

6. **Constructor exclusion** — `<init>` and `<clinit>` calls are never port violations (constructing result objects is test data setup).

7. **Inner-class aggregation** — Coroutine lambdas (`$1` classes) are rolled up to their outer test class for verdict computation.

8. **@Test annotation detection** — Uses bytecode annotations (JUnit4, JUnit5, kotlin.test, TestNG) instead of class name suffixes. Auto-excludes fakes, utilities, test extensions.

9. **ADAPTER_TEST suppression** — Adapter test classes are filtered from all formatter output (they're expected, not violations).

#### Field validation results (v0.1.85)

| Project | Before | After |
|---|---|---|
| ra-backend | 60+ MIXED (all noise) | 0 violations |
| greitt | 39 MIXED (all noise) | 0 violations |
| terms-and-conditions | not tested | 3 findings (2 DAO tests + 1 integration test) |

#### Remaining improvements

- **DAO test threshold** — `TermsAndConditionsDaoTest` and `ParentalConsentDaoTest` are adapter tests but port calls are <50% due to assertion/setup noise. Consider: count only non-framework calls in the denominator, or add a port-method-diversity heuristic (multiple distinct port methods = likely adapter test).
- **Sectors within rings** — see above section on ring sectors.

### `cnavContextUsage` — verify consistent test context usage

**Value: medium** | **Effort: medium**

Check that test classes use a shared test context (matching a configurable pattern like `*TestContext`) rather than constructing dependencies ad-hoc.

- **Parameters**: `-Pcontext-pattern=<regex>` (test context class, default `".*TestContext"`), `-Pformat=text|llm|json`
- **Builder**: Find all test classes. For each, check if it references a class matching the context pattern. Flag tests that instantiate services/repos directly instead of getting them from the context.
- **Output**: List of test classes not using the shared context, grouped by package.
- **Why**: Enforces the Test Setup pattern — centralized, reusable system setup.

### `cnavInterfacePurity` — check interfaces use domain types

**Value: medium** | **Effort: medium**

For interfaces matching a pattern, check that method signatures reference only domain-package types (not DTO/infrastructure types). Enforces that adapters convert at the boundary, keeping fakes simple.

- **Parameters**: `-Ppattern=<regex>` (interface filter), `-Pdomain-packages=<csv>` (packages considered domain), `-Pinfra-packages=<csv>` (packages considered infrastructure/DTO), `-Pformat=text|llm|json`
- **Builder**: For each interface method, inspect parameter types and return types. Flag methods whose signatures reference types from infra packages.
- **Output**: Per-interface report of methods with non-domain types in their signatures.
- **Why**: Ensures fakes remain simple (HashMap of domain objects) and the domain is protected from external format changes.

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

---

## Agent workflow improvements

### CI fail-on-violation mode

**Value: high** | **Effort: low**

Allow `cnavLayerCheck`, `cnavCycles`, and `cnavCohesion` to fail the build (non-zero exit code) when violations exceed a threshold. Transforms cnav from an exploration tool into an enforcement tool that blocks architectural decay in CI.

- `-Pfail-on-violation=true` for `cnavLayerCheck` (any OUTWARD violation fails)
- `-Pmax-cycles=0` for `cnavCycles` (fail if cycle count exceeds N)
- Possible: `-Pmax-danger=0` for `cnavBalance` (fail if any DANGER verdict)

---

## Behavioral + structural fusion

### Port volatility lockstep detector

**Value: medium** | **Effort: medium**

Cross-reference `cnavInterfaces` with `cnavVolatility`. If a port interface changes as frequently as its adapter implementation, the abstraction isn't stable — it's leaking implementation concerns upward.

- **Input**: Port pattern (e.g. `.*Repository|.*Client`), git history window
- **Builder**: For each interface, find implementors. Compare file-level change frequency (revisions, churn) between interface and implementor files. Flag pairs where the interface changes at >50% the rate of the implementor.
- **Output**: Per-port report: interface revisions vs implementor revisions, lockstep percentage, verdict (STABLE / LEAKY)
- **Why**: A stable port should rarely change. If it changes every time the adapter changes, the boundary isn't providing value. Suggests the interface needs to be more abstract or the adapter is doing work that belongs in the domain.

### `cnavChangedSince` → layered impact predictor

**Value: high** | **Effort: medium-large**

Expand `cnavChangedSince` from single-hop blast radius into a multi-signal impact predictor with confidence tiers.

#### Confidence layers

1. **Direct callers** (high confidence) — already implemented
2. **Transitive callers** up to configurable depth (medium confidence) — use `CallTreeBuilder`
3. **Interface implementor callers** (medium-high) — wire `InterfaceRegistry` to detect polymorphic impact
4. **Historically co-changed classes** (empirical signal) — join with `ChangeCouplingBuilder` data
5. **Same-package structural peers** (low confidence) — cheap heuristic from DSM data

#### Output shape

Each impacted class gets a confidence tier + reason. Agent/CI chooses threshold.

#### Infrastructure already available

- `CallTreeBuilder` — transitive caller expansion
- `InterfaceRegistry` — interface → implementor mapping
- `ChangeCouplingBuilder` — temporal coupling from git history
- `DsmDependencyExtractor` / `PackageDependencyBuilder` — structural coupling

#### Known limits

- Reflection / dynamic dispatch invisible to bytecode analysis
- Semantic changes (behaviour change without signature change) produce false negatives
- Test↔production mapping not available (future: combine with test-coupling task)

---

## Future: Compiler-based refactoring operations (inspired by Martin)

Potential refactoring operations using our PSI infrastructure. Now that `cnavRenameMethod` has been migrated to PSI (v0.1.90), the compiler integration barrier is gone — we have a working two-phase architecture (ASM location finding → PSI editing in isolated classloader) that new operations can reuse.

Inspired by [Martin](https://github.com/audunstrand/martin) by Audun Fauchald Strand — a CLI tool for semantically-correct Kotlin refactorings using the embedded Kotlin compiler frontend. Martin's clean design (especially the TextEdit primitive and warm daemon pattern) was instrumental in showing that `kotlin-compiler-embeddable` is viable outside an IDE. Thanks Audun!

**Effort key (post-PSI migration):**
- **Low** = PSI tree walking + text replacement, no type resolution needed. Reuses existing `KotlinCoreEnvironment` setup.
- **Medium** = Needs bytecode-guided location finding (like rename) or cross-file coordination.
- **High** = Needs `BindingContext` / full type resolution (not yet implemented).

### Extract operations

- **`cnavExtractFunction`** — Extract a range of lines into a new function, automatically determining parameters and return values. **Effort: high** (needs data flow analysis via BindingContext to determine params/return).
- **`cnavExtractVariable`** — Extract an expression at cursor into a named `val`. **Effort: low** (single-file PSI transform, no type resolution needed).
- **`cnavExtractConstant`** — Extract a literal into a companion object constant. **Effort: low** (single-file, find literal → add to companion).
- **`cnavExtractParameter`** — Extract a hardcoded value into a function parameter. **Effort: medium** (single-file extraction is easy, but updating call sites needs ASM location finding).
- **`cnavExtractInterface`** — Extract an interface from a class (public methods become interface contract). **Effort: medium** (PSI can enumerate public methods; updating implementors needs bytecode scan).
- **`cnavExtractSuperclass`** — Extract a superclass from a class. **Effort: medium** (similar to ExtractInterface).

### Inline / simplify operations

- **`cnavInline`** — Replace all usages of a variable or function with its definition, remove the declaration. **Effort: medium** (local inline is low; cross-file inline needs ASM + multi-file PSI edits).
- **`cnavConvertToExpressionBody`** — Convert block body `{ return x }` to `= x`. **Effort: low** (pure PSI pattern match on single function).
- **`cnavConvertToBlockBody`** — Convert expression body `= x` to `{ return x }`. **Effort: low** (inverse of above).

### Signature & structure changes

- **`cnavChangeSignature`** — Add, remove, or reorder function parameters (updating all call sites). **Effort: medium** (declaration change is trivial; call-site updates reuse RenameLocationFinder pattern — find via ASM, edit via PSI).
- **`cnavAddNamedArguments`** — Add explicit parameter names to call arguments. **Effort: medium** (needs to resolve parameter names from declaration, then find call sites via ASM).
- **`cnavIntroduceParameterObject`** — Group function parameters into a data class. **Effort: medium** (compose: create data class + ChangeSignature).
- **`cnavPullUpMethod`** — Move a method from a subclass to its superclass. **Effort: medium** (hierarchy already known from bytecode; PSI does the move).
- **`cnavReplaceConstructorWithFactory`** — Replace a constructor with a factory function. **Effort: medium** (declaration change + ASM finds constructor call sites).

### Type conversion operations

- **`cnavConvertToDataClass`** — Convert a class to a data class. **Effort: low** (single-file: add `data` modifier, verify requirements via PSI).
- **`cnavConvertToExtensionFunction`** — Convert a method to an extension function. **Effort: medium** (move + update call sites from `obj.method()` to `obj.method()` extension — call sites don't change syntactically but imports do).
- **`cnavConvertToSealedClass`** — Convert a class hierarchy to a sealed class. **Effort: medium** (needs hierarchy from bytecode, then multi-file PSI edits to add `sealed` + move subclasses).
- **`cnavTypeMigration`** — Change a type and update all related code. **Effort: high** (needs BindingContext for full type inference across assignments/returns).
- **`cnavConvertPropertyToFunction`** — Convert a property to a function. **Effort: medium** (declaration + call-site syntax change from `x.prop` to `x.prop()`).

### Safety operations

- **`cnavSafeDelete`** — Delete a declaration only if it has no usages (like dead code removal but interactive/targeted). **Effort: low** (combine existing `cnavDeadCode` logic with targeted PSI deletion).
- **`cnavEncapsulateField`** — Make a public property private and generate accessors. **Effort: medium** (single-file property change + ASM finds direct field access sites).

### Recommended implementation order (quick wins first)

1. **`cnavConvertToExpressionBody`** / **`cnavConvertToBlockBody`** — trivial, good for validating the pattern
2. **`cnavExtractVariable`** / **`cnavExtractConstant`** — single-file, builds confidence
3. **`cnavSafeDelete`** — leverages existing dead code detection
4. **`cnavConvertToDataClass`** — single-file with PSI validation
5. **`cnavChangeSignature`** — reuses RenameLocationFinder, high value for agents
6. **`cnavExtractParameter`** — combines single-file + cross-file (medium complexity)
7. **`cnavExtractInterface`** — medium, high value for architecture improvements

### Notes

- All "medium" operations follow the same two-phase pattern proven by `cnavRenameMethod`: ASM finds locations → PSI edits at those locations.
- "Low" operations can skip Phase 1 entirely — they're single-file PSI transforms.
- "High" operations (ExtractFunction, TypeMigration) are blocked on BindingContext integration. Consider Martin delegation for these until we add BindingContext support.
- `cnavSafeDelete` overlaps with existing `cnavDeadCode` but is targeted (single symbol) rather than whole-project scan.
- `cnavExtractInterface` and `cnavExtractSuperclass` overlap with existing hierarchy analysis — could use `cnavTypeHierarchy` to inform decisions.

---

## Future: Architectural patterns from Martin worth evaluating

Techniques observed in [Martin](https://github.com/audunstrand/martin) source code that could improve code-navigator.

### ~~Embedded Kotlin Compiler Frontend (`kotlin-compiler-embeddable`)~~ — DONE (v0.1.90)

**Value: high** | **Effort: high**

Martin uses `kotlin-compiler-embeddable` as a library dependency to get full PSI trees + `BindingContext` for type resolution — no IDE plugin required. This unlocks source-level transformations that bytecode analysis can't provide.

**K2 PSI trial results (Greitt, 140 .kt files, rename `toggleAdminDate`):**

| Approach | Cold | Warm | Notes |
|----------|------|------|-------|
| OpenRewrite | 16s | 7s | Full type resolution, heavy deps (~40MB) |
| PSI all files | 2s | 678ms | env=229ms, parse=370ms, find=72ms |
| PSI targeted (projected) | — | ~220ms | Parse only affected files |

**Decision (ADR-0001):** Two-phase architecture implemented in v0.1.90:
- Phase 1 (ASM, main classpath): `RenameLocationFinder` scans bytecode for call-site files + implementors
- Phase 2 (PSI, isolated classpath): `PsiRenameMethodRewriter` edits only identified files

**Current status:** `cnavRenameMethod` fully migrated from OpenRewrite to PSI. Uses `kotlin-compiler-embeddable:2.0.21` as `compileOnly` dep with classloader isolation. Bytecode guidance catches cross-package call sites (e.g., injected dependencies without explicit imports).

**Remaining:** BindingContext not yet used — current approach relies on bytecode for type resolution instead. Full BindingContext would enable extract/inline/change-signature operations listed above.

Evaluation questions:
- Could be added as an optional dependency for refactoring tasks that need source-level analysis.
- How does it interact with code-navigator's current OpenRewrite-based rewriting?
- Startup cost is ~3s for `KotlinCoreEnvironment` creation — acceptable inside Gradle daemon?
- K2 compiler (new frontend) may offer a lighter alternative in future Kotlin versions.

### TextEdit as universal edit primitive

**Value: medium** | **Effort: low**

Martin's `SourceRewriter` is 43 lines: all refactorings produce `List<TextEdit(file, offset, length, replacement)>`, applied in reverse offset order per file. Simpler than OpenRewrite's recipe/change-set model for targeted operations.

Evaluation questions:
- Could complement OpenRewrite for operations where recipe composition isn't needed.
- Offset-based approach requires source positions — available from PSI but not from bytecode.
- Mixing TextEdit and OpenRewrite in the same codebase: worth the dual model?

### Classpath discovery via Gradle init script

**Value: low (we run inside Gradle)** | **Effort: low**

Martin injects a temporary `--init-script` that registers a `martinPrintClasspath` task and prints resolved classpath entries. Clever for standalone CLI tools that need the project's dependency graph without the Tooling API.

Relevance: Only useful if code-navigator ever supports a standalone CLI mode outside Gradle/Maven. Low priority.

### Daemon mode with warm compiler environment

**Value: medium** | **Effort: medium**

Martin keeps `KotlinCoreEnvironment` alive across TCP socket invocations (`warmUp()` / `disposeEnvironment()`), reducing per-operation time from ~3s to <2s. Port file at `.martin/daemon.port` with auto-delegation from CLI.

Relevance: code-navigator already benefits from Gradle/Maven daemon JVM reuse. But if we add `kotlin-compiler-embeddable` operations, caching the analysis environment across task invocations (e.g., via a Gradle shared service) would be analogous.

### Martin as external tool integration

**Value: high** | **Effort: medium**

Rather than reimplementing compiler-based refactorings, code-navigator could delegate to Martin's daemon for extract/inline/change-signature operations. This gives:
- Unified UX (agent sees cnav tasks, doesn't need to know Martin exists)
- No compiler dependency bloat in code-navigator itself
- Martin handles the hard parts (data flow, type inference, scope analysis)

Integration shape:
- Optional Martin jar path in cnav config
- cnav tasks shell out to Martin daemon (start if not running)
- cnav wraps Martin output in standard LLM/JSON/TEXT format
- Falls back to "not available" guidance if Martin isn't installed
