# Code Navigator - Agent Instructions

## Using code-navigator on a target project

When working on a project that has code-navigator installed, run `./gradlew cnavAgentHelp -Pllm=true` (or `mvn cnav:agent-help -Dllm=true` for Maven) to get detailed, up-to-date instructions on available tasks, parameters, recommended workflows, result interpretation heuristics, and JSON schemas. That output is the primary reference for using code-navigator as an agent.

## Developing code-navigator itself

### Quick Reference

- **Run tests**: `mise exec -- ./gradlew test`
- **Publish locally**: `mise exec -- ./gradlew publishToMavenLocal`
- **Version**: `build.gradle.kts` + `pom.xml` (keep in sync, `-SNAPSHOT` for dev)
- **Plan**: `plan.md` (roadmap), `plan-completed.md` (done)

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

**Root package** — shared infrastructure:
- `TaskRegistry.kt` — `ParamDef`/`TaskDef` DSL, all task+param definitions
- `BuildTool.kt` — goal-to-task-name mapping (Gradle/Maven)
- `JsonFormatter.kt`, `LlmFormatter.kt`, `TableFormatter.kt` — output formatters
- `OutputWrapper.kt` — wraps output with LLM markers
- `AgentHelpText.kt` — generates `cnavAgentHelp` output
- `HelpText.kt`, `ConfigHelpText.kt` — detailed help + config help
- `CacheFreshness.kt` — cache staleness detection

**`config/`** — dependency-free leaf package:
- `OutputFormat.kt` — `OutputFormat` enum (TEXT/JSON/LLM), imported by all `*Config` classes

**`navigation/`** — bytecode-based analysis (requires compiled `classes`). Organized into sub-packages by feature:

- **Shared root** (`navigation/`): `BytecodeReader.kt` (`ScanResult<T>`), `DomainTypes.kt` (`ClassName`, `MethodRef`), `FileCache.kt`, `KotlinMethodFilter.kt`, `LambdaCollapser.kt`, `PatternEnhancer.kt`, `SkippedFileReporter.kt`
- **`annotation/`**: `AnnotationExtractor`, `AnnotationQueryBuilder`, `AnnotationQueryConfig`, `AnnotationQueryFormatter`, `FrameworkPresets`
- **`callgraph/`**: `CallGraphBuilder` (ASM → `CallGraph`), `CallGraphCache`, `CallGraphConfig`, `CallTreeBuilder` (→ `CallTreeNode`), `CallTreeFormatter`, `FindUsagesConfig`, `UsageFormatter`, `UsageScanner`
- **`classinfo/`**: `ClassDetailExtractor`, `ClassDetailFormatter`, `ClassDetailScanner`, `ClassFilter`, `ClassIndexCache`, `ClassInfoExtractor`, `ClassScanner`, `FindClassConfig`, `FindClassDetailConfig`, `ListClassesConfig`
- **`complexity/`**: `ClassComplexityAnalyzer`, `ComplexityConfig`, `ComplexityFormatter`
- **`deadcode/`**: `DeadCodeConfig`, `DeadCodeFinder`, `DeadCodeFormatter`, `FieldExtractor`, `InlineMethodDetector`
- **`dsm/`**: `CycleDetector`, `CyclesConfig`, `CyclesFormatter`, `DsmConfig`, `DsmDependencyExtractor`, `DsmFormatter`, `DsmHtmlRenderer`, `DsmMatrixBuilder`, `PackageDependencyBuilder`, `PackageDependencyFormatter`, `PackageDepsConfig`
- **`hierarchy/`**: `TypeHierarchyBuilder`, `TypeHierarchyConfig`, `TypeHierarchyFormatter`
- **`interfaces/`**: `FindInterfaceImplsConfig`, `InterfaceFormatter`, `InterfaceRegistry`, `InterfaceRegistryCache`
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
- `GradleSupport.kt` — `buildPropertyMap()` helper for reading `-P` properties

### Maven mojos (`src/maven/kotlin/.../maven/`)

- One `*Mojo.kt` per goal, mirrors the Gradle task structure

### Tests (`src/test/kotlin/no/f12/codenavigator/`)

Mirror the core structure. Each core class has a matching `*Test.kt`. Tests use ASM `ClassWriter` to generate synthetic `.class` files — no real compilation needed.

## Adding a New Feature

Typical checklist for a new task or parameter:

1. **Config**: add/update `*Config.kt` + `*ConfigTest.kt`
2. **TaskRegistry**: add `ParamDef` / update `TaskDef` params
3. **Scanner/Builder**: implement logic + tests (synthetic bytecode)
4. **Formatter**: update TEXT/LLM/JSON formatters + tests
5. **Gradle task**: update `*Task.kt` to pass new param, update `propertyNames` list
6. **Maven mojo**: update `*Mojo.kt` with new `@Parameter`
7. **AgentHelpText**: update common questions / workflow / JSON schemas
8. **noResultsGuidance**: update hints if applicable

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
5. `mise exec -- ./gradlew publishPlugins`
6. `mise exec -- ./mvnw clean deploy -Prelease` (signs + publishes to Central)
7. Bump to `X.Y.(Z+1)-SNAPSHOT` in `build.gradle.kts` and `pom.xml`
8. `git commit -am "Bump to X.Y.Z-SNAPSHOT"` && `git push && git push --tags`

Requires GPG key + Sonatype credentials in `~/.m2/settings.xml` (server id `central`).

## Plan Management

`plan.md` → active roadmap. When a feature is done, move its section to `plan-completed.md`.
