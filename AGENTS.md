# Code Navigator - Agent Instructions

## Using code-navigator on a target project

When working on a project that has code-navigator installed, run `./gradlew cnavAgentHelp -Pllm=true` (or `mvn cnav:agent-help -Dllm=true` for Maven) to get detailed, up-to-date instructions on available tasks, parameters, recommended workflows, result interpretation heuristics, and JSON schemas. That output is the primary reference for using code-navigator as an agent.

Refactoring operations try to be as deterministic as possible, but sometimes resort to heuristics when full type resolution is unavailable. Your LLM should be able to solve the remaining issues if the result is incorrect.

## Developing code-navigator itself

### Quick Reference

- **Run tests**: `mise exec -- ./gradlew test`
- **Publish locally**: `mise exec -- ./gradlew publishToMavenLocal`
- **Version**: `build.gradle.kts` + `pom.xml` (keep in sync, `-SNAPSHOT` for dev)
- **Plan**: `plan.md` (roadmap), `plan-completed.md` (done)

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
5. **Help text, agent help, and usage examples** are all generated from TaskRegistry metadata — never hand-curated.

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

## Release Process

1. Update `CHANGELOG.md` with changes since last tag (`git log` / `git diff`)
2. Remove `-SNAPSHOT` from `build.gradle.kts` and `pom.xml`
3. Update version in `README.md` installation examples
4. `git commit -am "Release X.Y.Z"` && `git tag vX.Y.Z`
5. `mise exec -- ./mvnw clean deploy -Prelease` (signs + publishes to Central — do this FIRST, it's more likely to fail)
6. `mise exec -- ./gradlew publishPlugins`
7. Bump to `X.Y.(Z+1)-SNAPSHOT` in `build.gradle.kts` and `pom.xml`
8. `git commit -am "Bump to X.Y.Z-SNAPSHOT"` && `git push && git push --tags`

Requires GPG key + Sonatype credentials in `~/.m2/settings.xml` (server id `central`).

**Important:** If one publish target succeeds but the other fails (e.g., Gradle Plugin Portal succeeds but Maven Central fails), you must bump to the next version and release again. Published versions cannot be overwritten.

## Plan Management

`plan.md` → active roadmap. `plan-completed.md` → archive of finished work.

When a feature is done, mark it with `~~` strikethrough and `— DONE (vX.Y.Z)` in the heading, then move the section to `plan-completed.md`. Periodically batch-move all completed sections to keep `plan.md` focused on remaining work.
