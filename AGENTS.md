# Code Navigator - Agent Instructions

## Using code-navigator on a target project

When working on a project that has code-navigator installed, run `./gradlew cnavAgentHelp -Pllm=true` (or `mvn cnav:agent-help -Dllm=true` for Maven) to get detailed, up-to-date instructions on available tasks, parameters, recommended workflows, result interpretation heuristics, and JSON schemas. That output is the primary reference for using code-navigator as an agent.

Refactoring operations try to be as deterministic as possible, but sometimes resort to heuristics when full type resolution is unavailable. Your LLM should be able to solve the remaining issues if the result is incorrect.

## Developing code-navigator itself

### Quick Reference

- **Run tests**: `mise exec -- ./gradlew test`
- **Run the Maven goals end to end**: `mise exec -- ./gradlew mavenTest` — installs the plugin into `~/.m2`, then runs real `mvn` goals against `test-project-maven` (needs network the first time, ~1 min warm). Not part of `test` (Gradle excludes the JUnit tag `maven`) or of the Maven build (surefire `excludedGroups` in `pom.xml` — `mvnw deploy` runs the whole test tree, so a new tagged test needs that exclusion too); run it after touching `src/maven`, the pom, or anything the mojos depend on, and before a release. Unit tests can't see how a mojo derives source roots from a `MavenProject` or whether the plugin's own classpath is complete — both broke every refactoring goal in Kotlin projects while the unit suite was green.
- **Publish locally**: `mise exec -- ./gradlew publishToMavenLocal`
- **Version**: `build.gradle.kts` + `pom.xml` (keep in sync, `-SNAPSHOT` for dev)
- **Plan**: `plan.md` (roadmap), `plan-completed.md` (done)

### Workflow policy

Never leave a pre-existing bug you notice while working, even if it's unrelated to the task at hand. Either fix it (preferred), or — only if fixing it would create too much unrelated change right now — note it explicitly (e.g. in `plan.md` or a code comment) so it isn't silently lost. Don't just skip past it.

### Testing in local projects

When testing changes in a local project (e.g., Greitt):
1. Publish locally: `mise exec -- ./gradlew publishToMavenLocal`
2. In the target project, update `settings.gradle.kts` to include `mavenLocal()` in `pluginManagement { repositories { ... } }`
3. Update the plugin version to the current SNAPSHOT (e.g., `0.1.90-SNAPSHOT`)
4. Revert the target project changes after testing (`git checkout -- .`)

## Source Layout

```
src/
├── core/    — Shared logic (both Gradle + Maven use this)
├── gradle/  — Gradle plugin tasks
├── maven/   — Maven Mojo wrappers
├── test/    — Tests for core + shared
└── gradleTest/ — Gradle-specific integration tests
```

### Core packages (`src/core/kotlin/no/f12/codenavigator/`)

**Root package** — help text only:
- `AgentHelpText.kt` — generates `cnavAgentHelp` output
- `HelpText.kt`, `ConfigHelpText.kt` — detailed help + config help

**`registry/`** — task registration and build tool support:
- `TaskRegistry.kt` — `ParamDef`/`TaskDef` DSL, all task+param definitions
- `BuildTool.kt` — goal-to-task-name mapping (Gradle/Maven)
- `CacheFreshness.kt` — cache staleness detection

**`formatting/`** — output formatters:
- `JsonFormatter.kt`, `LlmFormatter.kt`, `TableFormatter.kt` — output formatters
- `OutputWrapper.kt` — wraps output with LLM markers
- `DsmOutputFormatter.kt` — formats DSM orchestrator results (distance/strength)

**`config/`** — dependency-free leaf package:
- `OutputFormat.kt` — `OutputFormat` enum (TEXT/JSON/LLM), imported by all `*Config` classes

**`navigation/`** — bytecode-based analysis (requires compiled `classes`). Organized into sub-packages by feature:

- **`types/`**: `DomainTypes.kt` (`ClassName`, `PackageName`, `AnnotationName`, `SourceSet`), `TypeMatcher.kt`, `FrameworkPresets.kt`, `PatternEnhancer.kt`
- **`bytecode/`**: `BytecodeReader.kt` (`ScanResult<T>`), `KotlinMethodFilter.kt`, `LambdaCollapser.kt`, `SkippedFileReporter.kt`, `ProjectClassScanner.kt`, `RootPackageDetector.kt`, `SourceSetResolver.kt`, `AnnotationParameterCollector.kt`
- **`cache/`**: `FileCache.kt`, `CacheFreshness.kt`
- **`annotation/`**: `AnnotationExtractor`, `AnnotationQueryBuilder`, `AnnotationQueryConfig`, `AnnotationQueryFormatter`
- **`relations/callgraph/`**: `CallGraphBuilder` (ASM → `CallGraph`), `CallGraphCache`, `CallGraphConfig`, `CallTreeBuilder` (→ `CallTreeNode`), `CallTreeFormatter`, `FindUsagesConfig`, `UsageFormatter`, `UsageScanner`
- **`relations/hierarchy/`**: `TypeHierarchyBuilder`, `TypeHierarchyConfig`, `TypeHierarchyFormatter`
- **`relations/implementors/`**: `FindInterfaceImplsConfig`, `InterfaceFormatter`, `InterfaceRegistry`, `InterfaceRegistryCache`
- **`classinfo/`**: `ClassDetailExtractor`, `ClassDetailFormatter`, `ClassDetailScanner`, `ClassFilter`, `ClassIndexCache`, `ClassInfoExtractor`, `ClassScanner`, `FindClassConfig`, `FindClassDetailConfig`, `ListClassesConfig`
- **`complexity/`**: `ClassComplexityAnalyzer`, `ComplexityConfig`, `ComplexityFormatter`
- **`deadcode/`**: `DeadCodeConfig`, `DeadCodeFinder`, `DeadCodeFormatter`, `FieldExtractor`, `InlineMethodDetector`
- **`dsm/`**: `CycleDetector`, `CyclesConfig`, `CyclesFormatter`, `DsmConfig`, `DsmDependencyExtractor`, `DsmFormatter`, `DsmHtmlRenderer`, `DsmMatrixBuilder`, `PackageDependencyBuilder`, `PackageDependencyFormatter`, `PackageDepsConfig`
- **`metrics/`**: `MetricsBuilder`, `MetricsConfig`, `MetricsFormatter`
- **`rank/`**: `RankConfig`, `RankFormatter`, `TypeRanker`
- **`stringconstant/`**: `StringConstantConfig`, `StringConstantExtractor`, `StringConstantFormatter`, `StringConstantScanner`
- **`symbol/`**: `FindSymbolConfig`, `SymbolExtractor`, `SymbolFilter`, `SymbolIndexCache`, `SymbolScanner`, `SymbolTableFormatter`

**`analysis/`** — git-history-based analysis (no compilation needed):
- `GitLogRunner` (runs git), `GitLogParser` (parses output)
- Per-analysis triple: `*Builder.kt` + `*Config.kt` + `*Formatter.kt`
- Analyses: `Hotspot`, `ChangeCoupling`, `CodeAge`, `AuthorAnalysis`, `Churn`

### Gradle tasks (`src/gradle/kotlin/.../gradle/`)

- `CodeNavigatorPlugin.kt` — registers all tasks
- One `*Task.kt` per task (e.g. `FindCallersTask`, `FindUsagesTask`, `DeadCodeTask`)
- Each task declares `@Option`-annotated properties matching its `TaskDef.params` from TaskRegistry
- `CodeNavigatorTask` — abstract base class providing shared options (`format`, `llm`) and `buildOptionsMap()` helper

### Maven mojos (`src/maven/kotlin/.../maven/`)

- One `*Mojo.kt` per goal, mirrors the Gradle task structure
- Each mojo declares `@Parameter`-annotated fields matching its `TaskDef.params` from TaskRegistry
- **Source roots: use `project.mainSourceRoots()` / `testSourceRoots()` / `allSourceRoots()` (`MavenSupport.kt`), never `project.compileSourceRoots` directly.** In a Kotlin Maven project `src/main/kotlin` only lands in `compileSourceRoots` while kotlin-maven-plugin runs in the *same* Maven invocation. Every cnav goal is its own invocation, so the raw list holds just the default `src/main/java`; source-based goals then scan nothing. The helpers add the conventional Kotlin dirs and any `<sourceDirs>` configured on kotlin-maven-plugin.
- **Anything the plugin needs at runtime must be a compile-scope dependency in `pom.xml`.** `provided` means "absent from the plugin's classpath" for users, even though it is present when building and testing (that is how `kotlin-compiler-embeddable` shipped broken). Gradle gets the compiler through a separate worker classloader; Maven has no equivalent.
- **Unit tests can't see either of those.** After touching `src/maven`, the pom, or anything mojos depend on, run `mise exec -- ./gradlew mavenTest`. It runs real `mvn` goals as separate invocations against `test-project-maven`. Keep that fixture realistic: no `<sourceDirectory>` and no plugin declaration in its pom, because either hides the source-root bug. Running by hand: `mvn no.f12:code-navigator-maven-plugin:<version>:<goal> -D...` works without declaring the plugin; use a JDK the project's Kotlin/Lombok versions support (not just the newest installed one).

## TaskRegistry as Single Source of Truth

`TaskRegistry` (in `src/core/`) is the **master** definition for all tasks, parameters, types, defaults, descriptions, and examples. Both Gradle tasks and Maven mojos are **consumers** that sync from TaskRegistry.

```
                    ┌──────────────────┐
                    │  TaskRegistry    │  ← Master: params, types, defaults, descriptions
                    │  (core)          │
                    └────────┬─────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼                             ▼
   ┌──────────────────┐          ┌──────────────────┐
   │  Gradle Tasks    │          │  Maven Mojos     │
   │  @Option props   │          │  @Parameter      │
   │  (consumers)     │          │  (consumers)     │
   └──────────────────┘          └──────────────────┘
```

### Sync rules

1. **Add a param?** Add it to `TaskDef.params` in TaskRegistry first. Then add matching `@Option` (Gradle) and `@Parameter` (Maven) declarations.
2. **Rename a param?** Rename in TaskRegistry first. Then update Gradle and Maven to match.
3. **Tests enforce sync**: `TaskRegistryTest` verifies that every `ParamDef` in a `TaskDef` has corresponding option/parameter declarations in the Gradle task and Maven mojo. If you add a param to TaskRegistry but forget to add it to the task class, the test fails.
4. **Never add a parameter only in Gradle or Maven** — if TaskRegistry doesn't know about it, it doesn't exist.
5. **Help text and agent help are projections of TaskRegistry + TaskDocs.** `TaskDocs.kt` holds a `TaskDoc` per goal (details, whenToUse, interpretation, schema, caveats, related). `HelpRender.kt` turns a `TaskDef` + its `TaskDoc` into text once; `HelpText.kt` (`cnavHelp`), `AgentHelpText.kt` (`cnavAgentHelp` guide, sections, `--task=<goal>`, `--section=skill`) are thin arrangements of it. Adding a param to `TaskDef.params` makes it appear everywhere automatically. Adding a task requires a `TaskDocs` entry with at least `details`, `whenToUse` and `schema` (`TaskDocsTest` enforces this). Doc strings use `{t:goal}`, `{u:goal}`, `{p:name=value}`, `{pf:name}`, `{cmd}` placeholders (`DocTemplate`) so one string renders for Gradle and Maven; never write `--flag` or `cnavFoo` literally in a description, `AgentHelpTextTest`'s parity test will fail.

### Why this matters

- Single place to update descriptions, defaults, and types
- Drift between Gradle and Maven is caught by tests
- Agent help and human help always match actual behavior


### Tests (`src/test/kotlin/no/f12/codenavigator/`)

Mirror the core structure. Each core class has a matching `*Test.kt`.

**Prefer test-project** for tests that verify behavior against real Kotlin compiler output. This makes tests inspectable — you can read the source file to understand what's being tested.

**Use synthetic bytecode (TestClassWriter)** only when:
- Testing specific bytecode flags/patterns the compiler won't produce (e.g., missing source file attribute, specific ACC_SYNTHETIC combinations)
- Testing annotation visitor edge cases (nested annotations, enum arrays, repeatable containers, class literals) that would require adding heavy framework dependencies to test-project
- Testing the ByteArray API overload (no file on disk)
- Testing LDC instruction filtering (non-string constants like int/double/Type)

Tests that still use synthetic bytecode document why inline (see comments in the test files).

### test-project (`test-project/`)

A small Kotlin project compiled by Gradle, providing real `.class` files under `test-project/build/classes/kotlin/main/`. **This is the preferred approach for new tests.** Add Kotlin source files here when you need bytecode to test against. This ensures tests validate against what the Kotlin compiler actually produces rather than hand-crafted bytecode that may not match real-world output.

Key fixture packages:
- `variants/hierarchy/` — class hierarchies, interfaces, abstract classes
- `variants/annotated/` — custom annotations on classes, methods, fields
- `variants/constants/` — string constants in method bodies
- `domain/` — interfaces, data classes, sealed classes
- `infra/` — interface implementations, companion objects

## Adding a New Feature

Typical checklist for a new task or parameter:

1. **TaskRegistry** (master): add `ParamDef` / update `TaskDef` params — this is always first
2. **Config**: add/update `*Config.kt` + `*ConfigTest.kt`
3. **Scanner/Builder**: implement logic + tests (synthetic bytecode)
4. **Formatter**: update TEXT/LLM/JSON formatters + tests
5. **Gradle task**: add `@Option` property matching the new param, update `buildOptionsMap()`
6. **Maven mojo**: add `@Parameter` field matching the new param
7. **Sync test**: run `TaskRegistryTest` — it verifies Gradle/Maven declarations match TaskRegistry
8. **AgentHelpText**: auto-generated from TaskRegistry; verify output looks correct
9. **noResultsGuidance**: update hints if applicable

## Code Structure Principles

### Separate parsing, resolution, and formatting

Three layers, each independently testable:

1. **Parsing** — reads raw input (bytecode, git log) → data structure. No formatting, no output.
2. **Resolution** — takes parsed data + query → result structure (e.g. tree of nodes). No formatting, no I/O.
3. **Formatting** — takes result structure → text/JSON/LLM. No graph walking, no query logic.

Formatters never reach back into parsed data. When two formatters need the same data, they consume the same result structure.

### Why this matters

- Bugs are isolated to one layer.
- New output format = new formatter only, no duplicated resolution logic.
- Tests per layer are fast and focused.

### Structured signals, not pre-rendered text, flow into formatters

When a task computes something like "N of M edges touch test sources," pass the raw counts (e.g. `TestInvolvement.Counts`) as a field on the result object — don't render it to a notice string and string-concat it onto formatter output in the task. Formatters render every attribute, including notices/interpretations, per output format; that's the only way JSON can expose a structured field (`"testInvolvement":{"testInvolved":N,"total":M}`) instead of silently only supporting TEXT/LLM. Tasks/mojos must not receive formatted text from a deeper layer and append to it, and must not branch on `OutputFormat` themselves outside a formatter call.

Same idea elsewhere: `DeadCodeReason` (`NO_REFERENCES`/`TEST_ONLY`) and `DeadCodeConfidence` (`HIGH`/`MEDIUM`/`LOW`) are typed signals carrying the *why*, not a boolean carrying only the *whether*.

### Orchestrator pattern for Gradle/Maven parity

Analysis pipelines (extraction → plan-mutation → detection) live once in a core `*Orchestrator` object (e.g. `CyclesOrchestrator`, `RingsOrchestrator`, `BalanceOrchestrator`, `MoveSuggestOrchestrator`, `CohesionOrchestrator`). The Gradle task and the Maven mojo both call the exact same orchestrator function — nothing pipeline-shaped is reimplemented per build tool. This isn't just DRY: a real bug shipped because it wasn't followed — Maven mojos accepted `--plan-file` on the CLI and silently did nothing with it, because each mojo had its own hand-rolled copy of the pipeline that Gradle's version had evolved past. Extracting a shared orchestrator makes that class of drift a compile error instead of a silent gap.

Not applied to the refactoring-operation tasks (Rename*, Move*, ChangeSignature, SafeDelete) — their core rewrite logic (`RenameMethodRewriter`, `RenameLocationFinder`, etc.) is already directly shared and called identically from both sides; only Gradle wraps it in `WorkerExecutor` classloader isolation (`kotlin-compiler-embeddable` can't share a classloader with Gradle's own Kotlin runtime — Maven has no such conflict, so it calls the rewriter directly). There's no duplicated pipeline there to extract.

### Two-phase PSI refactoring

ASM finds locations (call sites, implementors) in the main classloader — fast, no compiler needed. PSI then performs the actual text edit, isolated in a Gradle `WorkerExecutor` classloader for the reason above. Maven skips the isolation step and calls the PSI rewriter in-process.

### Plan-file "what-if" simulation

`PlanStep`/`PlanMutator` mutate the in-memory dependency graph — not source files — so `--plan-file` on analysis tasks (`cnavCycles`, `cnavRings`, `cnavDsm`, `cnavMetrics`, `cnavBalance`, `cnavSimulateMove`, `cnavMoveSuggest`) can preview a move's structural impact before `cnavExecutePlan` applies it for real.

`PlanMutator.apply(dependencies, plan, dropSamePackageEdges)` defaults `dropSamePackageEdges` to `true` — correct for cycle/DSM/ring analysis, where an edge that becomes intra-package after a simulated move is noise. Pass `false` for anything extracted with `includeSamePackage=true` (move-suggest, cohesion), since those same-package edges are exactly what scores gravity at the destination — dropping them there would silently corrupt the simulation.

### `cnav-config.json` layered defaults

`CnavConfig.applyDefaults` merges a project's `cnav-config.json` `defaults` section under CLI-provided properties. It's generic by construction — any param name for any task works, since it's just a map merge ahead of `ParamDef.parseFrom`, no per-task allowlist. Precedence: CLI > `cnav-config.json` > a task's hardcoded `ParamDef` default.

Gradle gets this for every task from one central hook (`CodeNavigatorTask.buildOptionsMap()`). Maven has no shared base Mojo class, so every mojo wraps its own `buildPropertyMap()` with `project.applyConfigDefaults(...)` individually — remember this when adding a new mojo, since there's no compile-time enforcement the way `TaskRegistryTest` enforces param sync.

## Release Process

1. Run `mise exec -- ./gradlew test mavenTest` — both must pass (`mavenTest` is not part of `test`)
2. Update `CHANGELOG.md` with changes since last tag (`git log` / `git diff`)
3. Remove `-SNAPSHOT` from `build.gradle.kts` and `pom.xml`
4. Update version in `README.md` installation examples
5. `git commit -am "Release X.Y.Z"` && `git tag vX.Y.Z`
6. `mise exec -- ./mvnw clean deploy -Prelease` (signs + publishes to Central — do this FIRST, it's more likely to fail)
7. `mise exec -- ./gradlew publishPlugins`
8. Bump to `X.Y.(Z+1)-SNAPSHOT` in `build.gradle.kts` and `pom.xml` — check `git diff --stat` shows BOTH files changed (a silently non-matching `sed` once left the pom on the released version)
9. `git commit -am "Bump to X.Y.Z-SNAPSHOT"` && `git push && git push --tags`
10. `gh release create vX.Y.Z --title "vX.Y.Z" --notes-from-tag` (or pass `--notes` with the CHANGELOG.md section for this version if `--notes-from-tag` doesn't produce a useful body — the tag itself carries no message)

Requires GPG key + Sonatype credentials in `~/.m2/settings.xml` (server id `central`).

**Important:** If one publish target succeeds but the other fails (e.g., Gradle Plugin Portal succeeds but Maven Central fails), you must bump to the next version and release again. Published versions cannot be overwritten.

## Plan Management

`plan.md` → active roadmap. `plan-completed.md` → archive of finished work.

When a feature is done, mark it with `~~` strikethrough and `— DONE (vX.Y.Z)` in the heading, then move the section to `plan-completed.md` **immediately, in the same pass** — don't leave it in `plan.md` for a later cleanup. `plan.md` should only ever contain ACTIVE/FUTURE/PARKED/REJECTED work; a `DONE` item sitting in `plan.md` is a sign the move was skipped, not a valid steady state.
