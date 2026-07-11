# Plan — Completed

## ~~cnavChangeSignature~~ — DONE (v0.1.96)

PSI-based method signature refactoring: add, remove, or reorder parameters. Rewrites declaration and all call sites. Positional args reordered; named args preserved. New params require defaults for existing call sites (inserted as arguments, not Kotlin default values). Preview mode supported. 8 tests.

## ~~cnavLayerCheck removed~~ — DONE (v0.1.97)

Removed `cnavLayerCheck` (config-driven linear layer conformance). Superseded by `cnavRings` which auto-detects hexagonal architecture rings from the dependency graph without requiring configuration.

## ~~MoveClass / file operations~~ — DONE (v0.1.89)

### `cnavMoveClass`: handle files with multiple class declarations

When a class lives in a file not named after it (multi-class file), the rewriter falls back to content-based search. When the file contains multiple class declarations, all classes are moved together as a unit with consumer imports updated for each class.

### `cnavMoveFile` — file-level move for Kotlin files with mixed declarations

New `cnavMoveFile` task moves a Kotlin source file to a new package by relative path (`-Pfrom-file=<path> -Pto-package=<pkg>`). Handles multi-class files, Kt facade files, and mixed declaration files. Gradle task, Maven mojo, formatter, and help text all wired.

### `cnavMoveClass`: merge detection (target file exists)

Detection approach: when the target file already exists, the result includes a warning suggesting manual merge. Full automated merge deferred as too complex for marginal benefit.

## ~~`cnavSuggestStructure` — cluster analysis~~ — DONE (v0.1.89)

Implemented as a simpler grouping approach: groups `cnavMoveSuggest` results by target package, filters by min-group-size, computes structural drift score. Full community detection deferred. StructureGrouper, StructureFormatter, SuggestStructureOrchestrator, Gradle task, Maven mojo all wired.

## ~~`cnavFindUsages` summary mode — group by file~~ DONE

Added `-Pgroup-by=file` parameter that collapses results to one line per source file with a reference count. Motivated by user feedback that `cnavFindUsages -Ptype=SignatureContext` returned 40+ lines of data class boilerplate (`copy`, `copy$default`, `componentN`, `getSignatureContext`, field refs) when the user really wanted to know which files reference the type.

**Shape:**
- `-Pgroup-by=none` (default): unchanged per-reference listing.
- `-Pgroup-by=file`: one line per file with reference count.
  - TEXT: `Caller.kt (3 references)` (alphabetically sorted, singular/plural respected)
  - JSON: `[{"sourceFile": "Caller.kt", "referenceCount": 3}, ...]`
  - LLM: `Caller.kt 3` (terse, alphabetically sorted)

**Implementation:**
1. `GroupBy` enum (`NONE`, `FILE`) in `DomainTypes.kt` with `parse(String?)` mirroring `Scope`.
2. `GROUP_BY` ParamDef in `TaskRegistry.kt`, added to `FIND_USAGES.params` + new `UsageExample`.
3. `FindUsagesConfig.groupBy: GroupBy` field, parsed via `GroupBy.parse(TaskRegistry.GROUP_BY.parseFrom(...))`.
4. Aggregation lives in the formatters (post-processing), keeping `UsageScanner` untouched — aligns with three-layer architecture.
5. `UsageFormatter.formatSummary()`, `JsonFormatter.formatUsagesSummary()`, `LlmFormatter.formatUsagesSummary()`.
6. `FindUsagesTask` (Gradle) and `FindUsagesMojo` (Maven) dispatch on `config.groupBy`. Maven added `@Parameter(property = "group-by")`.
7. `HelpText` and `AgentHelpText` updated (parameter listing, JSON schema variant, exploration hint).
8. Chose enum shape (`-Pgroup-by=file`) over boolean flag for extensibility (future `-Pgroup-by=class`).

**Tests:** 12 new tests (5 TEXT formatter, 2 JSON formatter, 2 LLM formatter, 3 config parse). Full suite 2,226 tests green.

## ~~Replace `-Pprod-only` / `-Ptest-only` with `-Pscope=all|prod|test`~~ DONE

Replaced the two mutually-exclusive boolean parameters (`-Pprod-only=true`, `-Ptest-only=true`) with a single `-Pscope=all|prod|test` parameter (default: `all`). **Breaking change** — the old parameters are removed entirely, not deprecated.

**Scope semantics:**
- **Most tasks**: `scope` controls which source set classes to include (directory pre-filter or result post-filter).
- **`dead` with `scope=prod`**: test references don't count as keeping things alive.
- **`dead` with `scope=test`**: find dead test infrastructure (unused test helpers).
- **`dead` with `scope=all`**: current default — show all dead code.

**Implementation:**
1. Added `Scope` enum (`ALL`, `PROD`, `TEST`) in `DomainTypes.kt` with `matchesSourceSet(SourceSet)` and `parse(String?)`.
2. Added `SCOPE` ParamDef in `TaskRegistry.kt`, removed `PROD_ONLY` and `TEST_ONLY` entirely.
3. Updated all 22 config classes: `prodOnly: Boolean` / `testOnly: Boolean` → `scope: Scope`.
4. Updated all 4 filtering patterns (directory pre-filter, SourceSetResolver post-filter, CallGraph.sourceSetOf post-filter, domain-specific in DeadCodeFinder).
5. Updated all ~19 Gradle task classes and ~23 Maven Mojo classes.
6. Updated `AgentHelpText.kt`, `HelpText.kt`, `INCLUDETEST` deprecation message.
7. Updated `warnUnsupportedProperties` to include usage hint in error message.
8. All 22 config test files and `DeadCodeFinderTest` updated.
9. 2188+ tests green.

**Tested on:** greitt and bass-ra-backend projects with all three scope values (`prod`, `test`, `all`).

## ~~`cnavJar` — inspect library class signatures~~ DONE

Implemented as `-Pjar=<path-or-artifact>` parameter on four bytecode inspection tasks: `list-classes`, `find-class`, `class-detail`, and `find-symbol`. When set, scans classes from a JAR file instead of project classes; `prod-only`/`test-only` are ignored.

**Two modes**:
- File path: `-Pjar=/path/to/lib.jar` — validates file exists.
- Artifact coordinate: `-Pjar=com.example:library` — resolves from `runtimeClasspath` via Gradle's resolved artifacts.

**Implementation**:
- `JarClassScanner.kt` — New core class. Reads `.class` entries from JAR files, returns `List<JarClassEntry>` with `entryName`, `bytes`, and `label`.
- `BytecodeReader.kt` — Added `createClassReader(ByteArray, label)` overload. `File` overload delegates to it.
- `ClassInfoExtractor.extract(ByteArray)`, `ClassDetailExtractor.extract(ByteArray)`, `SymbolExtractor.extract(ByteArray)` — New overloads, each refactored to `extract(ClassReader)` internally.
- `GradleSupport.kt` — Added `Project.resolveJar(jarValue)` with `isArtifactCoordinate()` heuristic and `resolveArtifactFromClasspath()`.
- `ListClassesConfig`, `FindClassConfig`, `FindClassDetailConfig`, `FindSymbolConfig` — Added `jar: String?` field.
- `ListClassesConfig` — Also added `pattern: String?` field (previously lacked pattern filter).
- `TaskRegistry.kt` — Added `JAR` ParamDef. Added to `LIST_CLASSES`, `FIND_CLASS`, `CLASS_DETAIL`, `FIND_SYMBOL` task defs. Added `PATTERN` to `LIST_CLASSES`.
- `ListClassesTask`, `FindClassTask`, `FindClassDetailTask`, `FindSymbolTask` — JAR scanning path using `JarClassScanner` + respective extractors.
- `HelpText.kt`, `AgentHelpText.kt` — JAR parameter documentation with usage examples.

**Tests**: `JarClassScannerTest` (5 tests), `BytecodeReadExceptionTest` (3 new), plus ByteArray overload tests in `ClassInfoExtractorTest`, `ClassDetailExtractorTest`, `SymbolExtractorTest`. Config tests updated for jar/pattern fields. `TaskRegistryTest` expectedTypes map updated. `HelpTextTest` updated.

**Not implemented for Maven** — tracked as separate plan item.

## ~~`cnavLayerCheck` — architecture conformance via pattern-based layers~~ DONE

Architecture conformance checking based on hexagonal architecture principles. Layers are defined by class naming patterns (globs) in `.cnav-layers.json`, not by listing packages. First matching pattern wins, enabling enforcement in feature-organized projects where controllers, services, and repositories share a package.

**Layer rule model**:
- Layer order: top to bottom in config = outermost to innermost.
- Each layer may only depend on layers **below** it.
- Peer dependencies (same layer) are **forbidden by default** (`peerLimit = 0`).
- `peerLimit` on a layer raises the allowed per-class peer dependency count (`-1` = unlimited).
- Two violation types: **OUTWARD** (class depends on a higher/outer layer) and **PEER** (class exceeds `peerLimit` for same-layer dependencies).

**Config format** (`.cnav-layers.json`):
```json
{
  "layers": [
    { "name": "wiring", "patterns": ["*Dependencies", "*TestContext", "App"], "testInfrastructure": true },
    { "name": "http", "patterns": ["*Route", "*Routes", "*Setup", "*Endpoint"] },
    { "name": "service", "patterns": ["*Service"], "peerLimit": 3 },
    { "name": "adapter", "patterns": ["*Repository", "*Client", "*Cache", "*Sender"] },
    { "name": "domain", "patterns": ["*"], "peerLimit": -1 }
  ]
}
```

**Pattern matching**: On simple class name (not FQN). `matchesGlob` supports `*` (all), `*Suffix`, `Prefix*`, `*Middle*`, `Exact`. `Kt` and `Test` suffixes are stripped recursively before matching — `OrderRouteKt` matches `*Route`, `OrderServiceTest` matches `*Service`.

**testInfrastructure**: `testInfrastructure: true` on a layer means test classes (names ending in `Test`/`TestKt`) may depend on it from any layer without OUTWARD violations. Production code depending on a testInfrastructure layer is still a violation.

**`-Pinit=true` mode**: Generates a starter `.cnav-layers.json` with all class name patterns in a single "unassigned" layer plus dependency summary and next-step instructions.

**Implementation**:
- `LayerConfig.kt` — JSON parser (`SimpleJson`) reading layers, patterns, peerLimit, testInfrastructure. Boolean parsing added. `Layer` data class. `layerIndexOf()` with candidate name stripping.
- `LayerChecker.kt` — `checkDependencies()` evaluates OUTWARD and PEER violations per `PackageDependency`. `exemptedByTestInfrastructure()` check.
- `LayerFormatter.kt` — TEXT, JSON, LLM output for violations and summaries.
- `LayerCheckConfig.kt` — config parsing from property map.
- `LayerInitGenerator.kt` — generates starter config from dependency edges.
- `LayerCheckTask.kt` (Gradle), `LayerCheckMojo.kt` (Maven) — wired with non-zero exit code on violations.
- `ClassName.candidateNames()`, `ClassName.isTest()`, `ClassName.STRIPPABLE_SUFFIXES` — moved from LayerConfig companion for reuse.
- `JsonFormatter.formatLayerCheck()`, `LlmFormatter.formatLayerCheck()` — structured output.

**Tests**: `LayerConfigTest` (28 tests), `LayerCheckerTest` (19 tests), `LayerFormatterTest` (6 tests), `LayerInitGeneratorTest` (6 tests), `LayerCheckConfigTest`, `DomainTypesTest` (9 new tests for isTest/candidateNames).

**E2E validated** on greitt (83→0 violations), bass-ra-backend (34→2 real violations), spring-petclinic.

## ~~`cnavSize` — source file size analysis~~ DONE

New `SOURCE` category task that scans source files (Kotlin, Java) by line count without requiring compilation. First source-level scanner in the project.

- **Core**: `FileSizeScanner.scan(sourceRoots, over, top)` walks source roots, counts lines, filters by `over` threshold, returns top N sorted by size descending. `FileSizeEntry(file, lines)` data class.
- **Config**: `FileSizeConfig.parse()` reads `over` (default 0), `top` (default 50), `format` from properties. New `OVER` ParamDef in TaskRegistry.
- **Formatters**: TEXT with column-aligned table + terse "Consider splitting" recommendation (fires when largest file >= 3x median, minimum 3 files). JSON as array of `{file, lines}` objects. LLM as compact `file lines=N` format.
- **Gradle**: `SizeTask` uses new `project.sourceDirectories()` extension (iterates all sourceSets, collects existing `allSource.srcDirs`). Does not depend on compilation.
- **Maven**: `SizeMojo` uses `project.compileSourceRoots + project.testCompileSourceRoots`.
- **Registry**: `SIZE` TaskDef (goal="size", requiresCompilation=false, category=SOURCE). New `SOURCE` TaskCategory. Task count: 33.
- **Help**: Added to both `AgentHelpText` (common questions, workflow, task reference with SOURCE category, JSON schema) and `HelpText` (Source Analysis Tasks section).
- **Tests**: 22 new tests across FileSizeScannerTest (9), FileSizeConfigTest (5), FileSizeFormatterTest (5), JsonFormatterTest (2), LlmFormatterTest (1).

## ~~Terse recommendations in analysis formatters~~ DONE

Added short, actionable one-liner recommendations to four analysis formatters:

- **CyclesFormatter**: Every cycle gets "Extract shared types into a new package or invert one dependency direction."
- **ComplexityFormatter**: Flags high fan-out (>10 distinct outgoing classes) and high fan-in (>20 distinct incoming classes) with splitting/ripple warnings.
- **ChangeCouplingFormatter**: Flags coupling degree >=70% with merge/extract suggestion. Suppresses recommendations for test+main pairs (one file in `src/main/`, other in `src/test/`) since these are expected to co-change.
- **HotspotFormatter**: Flags files with revisions >=2x median (minimum 5 files) as change hotspots.

Tested on three projects of different sizes (greitt ~small, spring-petclinic ~medium, bass-ra-backend ~large). Thresholds scale well — small projects don't get false positives, larger ones get actionable signals. Remaining noise from non-source files (build config, deployment config) tracked as future improvement.

## ~~Dead code: polymorphic dispatch via intra-class calls~~ DONE

Interface/abstract dispatch resolution now runs inside the same BFS as intra-class call propagation. Previously, dispatch resolution ran once before intra-class BFS, so methods discovered via intra-class edges (e.g. `LeafPattern.match` → `this.singleMatch`) were never dispatched to implementors. The unified BFS handles both: when a method becomes alive, it dispatches to all implementors AND follows intra-class call edges. Covers multi-level hierarchies (Pattern → BranchPattern → Either/Required). This was the #1 source of false positives in the v0.1.46 docopt-kotlin field test.

## ~~Dead code: inner class liveness propagation~~ DONE

After building `calledTypes`, walks `ClassName.outerClass()` for every alive class and adds ancestors. Fixes `TokenError` flagged dead even though `TokenError$ExitException` was actively used. ~10 lines in `DeadCodeFinder.find()`.

## ~~Dead code: Kotlin delegation-generated methods~~ DONE

`DelegationMethodDetector` compares bytecode methods against Kotlin metadata functions. Methods present in bytecode but absent from metadata (excluding bridge/synthetic/constructors) are delegation methods. Passed to `DeadCodeFinder` as `delegationMethods` parameter for filtering. `BridgeMethodDetector` separately scans for `ACC_BRIDGE` methods (JVM bridge methods for type erasure), passed as `bridgeMethods` parameter. Both wired into Gradle `DeadCodeTask` and Maven `DeadCodeMojo`.

## ~~1. `cnavContext` — smart context gathering for AI agents (High value)~~ DONE

Given a class pattern, gathers everything an AI agent needs in a single invocation: class detail (signature, fields, methods, annotations), callers tree (depth-configurable), callees tree (depth-configurable), interface implementations, and implemented interfaces. Pure composition of existing features — no new analysis code. Reduces agent round-trips from 4-5 to 1.

**Implementation**: `ContextConfig` with `pattern`, `maxDepth`, `format`, `projectOnly`, `prodOnly`, `testOnly` parameters. `ContextBuilder.build()` composes `ClassDetail`, caller/callee `CallTreeNode` trees, implementors, and implemented interfaces into a `ContextResult` data class. Orchestration in `ContextTask` (Gradle) and `ContextMojo` (Maven) — scans class detail, builds call graph and interface registry, then for each matched class builds caller/callee trees and looks up interface information. TEXT formatter in `ContextFormatter`, plus `LlmFormatter.formatContext()` and `JsonFormatter.formatContext()`. Supports all standard output formats (TEXT/JSON/LLM) and filtering parameters (`projectonly`, `prodonly`, `testonly`).

## ~~2. Separate prod/test in output (High value)~~ DONE

All bytecode tasks now tag each caller, callee, and usage reference with `[test]` or `[prod]` based on which source set the class came from. Adds `-Pprod-only=true` / `-Ptest-only=true` filtering parameters to `cnavCallers`, `cnavCallees`, `cnavUsages`, `cnavComplexity`, and `cnavRank`.

**Implementation**: `SourceSet` enum (`MAIN`/`TEST`) in `DomainTypes.kt`. `CallGraph` tracks `sourceSets: Map<ClassName, SourceSet>` populated via `CallGraphBuilder.buildTagged()` which accepts `List<Pair<File, SourceSet>>`. `CallGraphCache` persists source sets in a backward-compatible `[SOURCE_SETS]` section. `CallTreeNode` carries `sourceSet` field populated by `CallTreeBuilder`. `UsageSite` carries `sourceSet` field populated by `UsageScanner.scanTagged()`. All formatters (TEXT, LLM, JSON) render `[test]`/`[prod]` tags on child nodes and usage lines. `CallGraphConfig.buildFilter()` and `FindUsagesConfig.filterBySourceSet()` handle prod-only/test-only filtering. Gradle tasks use `Project.taggedClassDirectories()` and Maven mojos use `MavenProject.taggedClassDirectories()` to resolve tagged source set directories.

## ~~cnavChangedSince — impact analysis for a branch/commit (Very high value)~~ DONE

`cnavChangedSince -Pref=<git-ref>` shows the blast radius of changes since a git ref. Runs `git diff --name-only <ref>...HEAD` to find changed files, maps them to compiled class names via suffix matching against `ClassInfo.reconstructedSourcePath`, then finds all callers of each changed class via `CallGraph.callersOfClass()`. Outputs changed classes sorted by caller count descending, with unresolved files (non-class changes like build.gradle.kts) listed separately. Supports TEXT, JSON, and LLM output formats. Hybrid task: requires both git and compilation (`dependsOn("classes")`).

## ~~1. Include test source set in cnavInterfaces (High value)~~ DONE

`cnavInterfaces` now supports `-Pincludetest=true` to also scan test class directories. This reveals test fakes (e.g., `FakeRepo`, `StubClient`) alongside production implementations. Uses a separate cache file (`interface-registry-all.cache`) when test classes are included to avoid mixing results.

## ~~2. True tree indentation for cnavCallers/cnavCallees (High value)~~ DONE

Already implemented. `CallTreeFormatter.renderTree()` recursively walks callers/callees up to `maxDepth`, increasing indentation at each level. Cycle detection via `visited` set prevents infinite recursion. Tests cover transitive nesting, depth limits, and cycles.

## ~~3. "No packages found" message for cnavDeps with invalid filter (Low effort, high polish)~~ DONE

Already implemented in `PackageDepsTask.kt:26-29`.

## ~~4. Reverse dependency view for cnavDeps (High value)~~ DONE

`cnavDeps` now supports `-Preverse=true` to show reverse dependencies (who depends on each package). Uses a lazy inverted map in `PackageDependencies.dependentsOf()`. `allPackages()` and `findPackages()` include all packages (both sources and targets of dependencies) so packages with only incoming dependencies also appear. Output uses `←` arrows for reverse mode and shows "(no incoming dependencies)" when a package has no dependents.

## ~~5. Filter out stdlib/JDK noise in cnavCallees and cnavDeps (Medium value)~~ DONE

cnavCallees, cnavCallers, and cnavDeps now support `-Pprojectonly=true` to filter output to project classes only, hiding JDK/stdlib/library noise. Uses `CallGraph.projectClasses()` (derived from scanned source files) to determine what's "project" vs "external".

## ~~7. JSON/machine-readable output format (Medium value)~~ DONE

All tasks now support `-Pformat=json` for structured JSON output. Hand-rolled JSON formatter (`JsonFormatter.kt`) with no external dependencies — uses `jsonArray`, `jsonObject`, `jsonValue` helpers and a `JsonRaw` value class for pre-rendered content. Covers all 8 data tasks: cnavListClasses, cnavFindClass, cnavFindSymbol, cnavClass, cnavCallers, cnavCallees, cnavInterfaces, cnavDeps. Also added `cnavAgentHelp` task with workflow guidance, task reference, and performance tips for AI coding agents.

## ~~8. cnavClass show interfaces implemented (Low effort)~~ DONE

Already implemented. `ClassDetailExtractor` extracts interfaces from bytecode and `ClassDetailFormatter` outputs "Implements: ..." when interfaces are present.

## ~~27. `cnavDead` — dead code detection (High value, low effort)~~ DONE

Implemented as `cnavDead` task / `cnav:dead` goal. Finds dead classes (no incoming type-level edges from other project classes) and dead methods (class is alive but method has no cross-class callers). Supports `filter` and `exclude` regex parameters. TEXT output uses columnar table (Class | Member | Kind | Source), plus JSON and LLM formats. Wired in both Gradle (`DeadCodeTask.kt`) and Maven (`DeadCodeMojo.kt`).

## ~~37. `cnavUsages` — find project references to external types/methods (High value, medium effort)~~ DONE

A classpath-wide search for usages of specific types and methods. Helps checking what is on the classpath as well as checking the signatures of classes and methods. The most common AI-assisted refactoring task is "migrate from deprecated API X to new API Y" — this requires finding every place in project code that references an external library type, method, or property. Currently cnav only indexes project-defined symbols (`cnavFindSymbol`) and traces calls between project methods (`cnavCallers`). External API usages fall through the cracks, forcing fallback to text-based grep — which misses FQN vs import distinctions, can't distinguish same-named methods on different types, and doesn't understand bytecode-level method names like `getMonthNumber` for Kotlin property `.monthNumber`.

ASM's `MethodVisitor` already sees every `INVOKE*` and field access instruction with full owner class + method name + descriptor. The data is there during cnav's class scanning pass.

- **Question**: "Where in my project code do I use this external type or method?"
- **Needs**: Bytecode only (extends existing ASM scanning)
- **Parameters**:
  - `-Powner=<class>` — FQN of the type to search for (e.g., `kotlinx.datetime.LocalDate`)
  - `-Pmethod=<name>` — (optional) specific method name on the owner (e.g., `getMonthNumber`)
  - `-Ptype=<class>` — (alternative to owner) find all references to a type in signatures, fields, locals, casts
  - `-Pprojectonly=true` — filter to project classes only
- **Builder**: `UsageScanner.scan(classDirectories, owner, method, type) -> List<UsageSite(callerClass, callerMethod, sourceFile, targetOwner, targetName, targetDescriptor, kind)>`
- **Bytecode instructions scanned**:
  - `visitMethodInsn` — method calls (owner + method + descriptor)
  - `visitFieldInsn` — field reads/writes (GETFIELD, PUTFIELD, GETSTATIC, PUTSTATIC)
  - `visitTypeInsn` — NEW, CHECKCAST, INSTANCEOF
  - Method/field descriptors — type references in parameters, return types, field types
- **Why this beats grep**:
  - Distinguishes `someLocalDate.monthNumber` from `someOtherType.monthNumber` (owner-aware)
  - Finds Kotlin property accessors by their bytecode name (`getMonthNumber`) even when source says `.monthNumber`
  - Catches FQN references and imported references identically
  - Type reference search catches field declarations, method parameters, return types, and casts — not just call sites

## ~~41. `cnavUsages` — smarter "no results" guidance and `-Ptype` should also find method call owners (Medium value, low effort)~~ DONE

From real-world migration feedback: `cnavUsages -Ptype=ContextKt` returned "No usages found" because `-Ptype` only searched for type references (NEW, CHECKCAST, INSTANCEOF, descriptor types). Now `-Ptype` is comprehensive: it also matches method call and field instruction owners, so `-Ptype=ContextKt` finds calls to `ContextKt.locateResourceFile()`. Additionally, empty results now show guidance suggesting FQN checks and alternative parameters.

## ~~42. `-Pcycles=true` on `cnavDsm` — dedicated cycle detail view~~ DONE

Implemented as a `-Pcycles=true` parameter on the existing `cnavDsm` task (rather than a separate task). When `cycles=true`, skips the full DSM matrix and outputs only cycle details with class-level edges in both directions. Supports all three output formats (TEXT, JSON, LLM). Note: source file locations are not tracked in the DSM data model, so edges show class names only (not file:line).

## ~~48. Targeted cycle filter for DSM (Medium value, low effort)~~ DONE

Implemented as `-Pcycle=pkgA,pkgB` parameter on `cnavDsm`. When set, implies cycles-only mode and filters to show only the cycle between the two named packages. Parsed via `DsmConfig.parseCycleFilter()` which splits on comma. Supports all three output formats (TEXT, JSON, LLM). Wired in both Gradle (`DsmTask.kt`) and Maven (`DsmMojo.kt`).

## ~~50. Cross-package usage filtering for `cnavUsages` (Medium value, low effort)~~ DONE

Implemented as `-Poutside-package=<pkg>` parameter on `cnavUsages` / `cnav:find-usages`. Filters results to only show callers outside the specified package boundary, using dot-boundary matching to avoid partial prefix matches. Wired in both Gradle (`FindUsagesTask.kt`) and Maven (`FindUsagesMojo.kt`).

## ~~47. `cnavComplexity` — method-level fan-in/fan-out for a class (Medium value, low effort)~~ DONE

Implemented as `cnavComplexity` (Gradle) / `cnav:complexity` (Maven). Shows fan-in/fan-out complexity per class — how many calls go out to other classes and how many come in from other classes, with counts grouped by target/source class. Parameters: `-Pclass=<pattern>` (required, regex), `-Pprojectonly=true` (default true), `-Pdetail=true`. Supports all three output formats (TEXT, JSON, LLM). Core analysis in `ClassComplexityAnalyzer`, formatting in `ComplexityFormatter`/`JsonFormatter`/`LlmFormatter`.

## ~~44. Deduplicate `cnavUsages` output (Low effort, high polish)~~ DONE

Fixed by switching `UsageScanner` from `mutableListOf<UsageSite>()` to `mutableSetOf<UsageSite>()` at the scanner level. Since `UsageSite` is a data class, set equality deduplicates automatically. Follows the same pattern as `DsmDependencyExtractor` which already used `mutableSetOf<PackageDependency>()`.

## ~~45. Fix `cnavDsm` HTML path resolution (Low effort, bug fix)~~ DONE

Fixed `DsmTask.kt` to use `project.file(config.htmlPath)` instead of `File(htmlPath)` so relative paths resolve against the project directory rather than the Gradle daemon's working directory. Maven `DsmMojo.kt` also fixed to use `File(project.basedir, config.htmlPath)`.

## ~~52. Fix `cnavComplexity` LLM output readability (Low effort, high polish)~~ DONE

Rewrote `LlmFormatter.formatComplexity()` to use multi-line format instead of cramming all outgoing/incoming types into a single line. Each class now shows its header line followed by indented `outgoing:` and `incoming:` sections with one type per line. Empty lists show `none` on the same line. Multiple classes are separated by blank lines.

## ~~17. Refactor Gradle tasks to use Config data classes (Medium value, low effort)~~ DONE

Already implemented. All 19 Gradle tasks delegate to `XxxConfig.parse()` via `project.buildPropertyMap()`. No changes needed.

## ~~63. Collapse Kotlin lambdas (Very high value, medium effort)~~ DONE

Implemented `LambdaCollapser` utility that collapses Kotlin lambda inner classes (e.g., `Foo$bar$1$2`) into their enclosing class (`Foo`). Applied to `cnavComplexity` and `cnavRank` tasks via `-Pcollapse-lambdas=true` (default). Design follows "collapse as late as possible" principle: `TypeRanker.rank()` collapses in the resolution layer (affects PageRank topology), while `ClassComplexityAnalyzer.analyze()` returns raw data and collapsing is applied via reusable `LambdaCollapser.collapseComplexity()` transformer in the task layer just before formatting. Named inner classes (uppercase-starting segments like `$Bar`) are preserved.

## ~~24. `cnavCycles` — explicit cycle detection task (High value, medium effort)~~ DONE

Implemented `cnavCycles` task using Tarjan's SCC (Strongly Connected Components) algorithm to detect true multi-node dependency cycles in the package dependency graph. Unlike the existing `cnavDsm -Pcycles=true` which only finds pairwise bidirectional edges (A<->B), `cnavCycles` detects cycles of any size (A->B->C->A). Uses the DSM pipeline for comprehensive dependency extraction (superclass, interfaces, field types, method signatures, method calls). Supports TEXT, JSON, and LLM output formats. Parameters: `-Proot-package=<pkg>`, `-Pdepth=N`, `-Pformat=json|text|llm`. Available as both Gradle task (`cnavCycles`) and Maven goal (`cycles`).

## ~~66. Fix `<unknown>` source locations for project-internal classes (Very high value, medium effort)~~ DONE

Modified `CallGraph.sourceFileOf()` to progressively strip `$` inner class suffixes when the direct lookup fails. For example, `Foo$bar$1` → `Foo$bar` → `Foo`, returning the first match. This resolves `<unknown>` source files for Kotlin lambda inner classes, companion objects, and nested anonymous classes. Inner classes share the source file attribute in bytecode, so the outer class's source file is correct. Tests cover inner class fallback, multi-level fallback, and no-match returning `<unknown>`.

## ~~67. Kotlin-aware property name resolution (Very high value, medium effort)~~ DONE

Modified `CallGraph.findMethods()` to auto-expand to `get<Name>`/`set<Name>`/`is<Name>` when the original pattern finds no direct match. The `expandPropertyAccessors()` private method handles both escaped dots (`\.`) and unescaped dots (`.`) in patterns, expanding only the method name portion after the last dot. This allows patterns like `Account.accountNumber` to automatically match `Account.getAccountNumber`. Expansion only fires when the original pattern returns zero results, so exact method names are never overridden.

## ~~68. Filter synthetic/generated methods from `cnavCallers`/`cnavCallees` output (High value, low effort)~~ DONE

Added `-Pfilter-synthetic=true` parameter (default: true) to `cnavCallers` and `cnavCallees`. When enabled, filters out Kotlin compiler-generated methods (`<init>`, `<clinit>`, `equals`, `hashCode`, `toString`, `copy`, `componentN`, `access$*`, `$lambda$`, etc.) using the existing `KotlinMethodFilter`. Wired in both Gradle tasks (`FindCallersTask`, `FindCalleesTask`) and Maven mojos (`FindCallersMojo`, `FindCalleesMojo`). The filter composes with the existing `projectOnly` filter.

## ~~70. Type-usage query discoverability — improve `cnavUsages -Ptype` documentation (Medium value, low effort)~~ DONE

Added a "Common Questions → Which Task" section to `AgentHelpText.kt`, placed between "When to Use What" and "Recommended Workflow". Maps natural-language questions to the correct task and parameters: "Where is type X used?" → `cnavUsages -Ptype=X`, "Who calls method X?" → `cnavCallers -Pmethod=X`, "What does class X look like?" → `cnavClass -Ppattern=X`, plus entries for callees, interfaces, package deps, dead code, rank, and hotspots. Section uses the build-tool-aware `u()` and `p()` helpers so it renders correctly for both Gradle and Maven.

## ~~65. Include line numbers when listing classes, methods, or symbols (Medium value, low effort)~~ DONE

Implemented line numbers for `cnavCallers` and `cnavCallees` call tree tasks. During bytecode scanning, `CallGraphBuilder.extractCalls()` captures the first line number per method via ASM's `visitLineNumber` callback, stored in `CallGraph.lineNumbers`. `CallTreeBuilder` propagates line numbers into `CallTreeNode`. All three formatters render line numbers: TEXT format uses `(File.kt:42)` parenthesized style, LLM format uses `File.kt:42` space-separated style, and JSON includes a `lineNumber` field (omitted when null). Cache format extended with a backward-compatible `[LINES]` section. Also fixed a bug in `OutputFormat.from()` where `-Pformat=llm` was not recognized — it only checked the boolean `-Pllm=true` flag, not the string `format` parameter.

## ~~71. `cnavUsages` — simple name matching for ownerClass and type (Bug fix, high value)~~ DONE

`cnavUsages` used exact `String.equals(ignoreCase=true)` for `ownerClass` and `type` matching, while every other task uses `Regex.containsMatchIn(IGNORE_CASE)`. This meant `-PownerClass=PollsRepository` found nothing in `cnavUsages` but worked in `cnavCallers`. Fixed by changing `matchesOwner` and `matchesType` in `UsageScanner` to use `Regex.containsMatchIn(IGNORE_CASE)`, consistent with the rest of the codebase. Regex is compiled once in `scan()` and passed to `extractUsages()` to avoid per-instruction compilation.

## ~~69. `cnavFieldUsages` — find all reads/writes of a field or Kotlin property (High value, medium effort)~~ DONE

Enhanced `cnavUsages` with `-Pfield=<name>` parameter (Option A from plan). When `field` is set, `UsageScanner` matches both direct field access via `visitFieldInsn` and Kotlin property accessor calls (`get<Field>`, `set<Field>`, `is<Field>`) via `visitMethodInsn`. The `field` parameter requires `ownerClass` and is mutually exclusive with `method`. Validation in `FindUsagesConfig.parse()` with clear error messages. Wired in both Gradle (`FindUsagesTask.kt`) and Maven (`FindUsagesMojo.kt`). Updated `HelpText.kt`, `AgentHelpText.kt`, and `noResultsGuidance()` with field-specific documentation and hints.

## ~~S1. Break cyclic package dependencies — move `OutputFormat`~~ DONE

Created `no.f12.codenavigator.config` package as a dependency-free leaf. Moved `OutputFormat` there, breaking the `codenavigator` <-> `navigation`/`analysis` cycles caused by `*Config` classes importing from the root package. Updated 78 files.

## ~~S2. Dead classes — delete `CalleeTreeFormatter` and `CallerTreeFormatter`~~ DONE

Deleted both wrapper classes. Updated 5 test files to use `CallTreeFormatter` directly.

## ~~S3. Remove resolution logic from `JsonFormatter`~~ DONE

Deleted `JsonFormatter.formatCallTree` which mixed `CallTreeBuilder.build()` resolution with formatting — violating the parsing/resolution/formatting separation. Updated 7 test call sites to call resolution then formatting separately. Removed 4 unused imports. Remaining ideas (extract per-feature format functions, `ResultFormatter` interface) are optional future work tracked in S6.

## ~~S4. Consolidate cache classes into generic `FileCache<T>`~~ DONE

Extracted `FileCache<T>` abstract base class with shared `isFresh()`, `getOrBuild()`, and `FIELD_SEPARATOR`. Migrated all four caches (`ClassIndexCache`, `SymbolIndexCache`, `InterfaceRegistryCache`, `CallGraphCache`) to extend it. Unified `getOrScan`/`getOrBuild` naming to `getOrBuild` everywhere.

## ~~S5. Consolidate duplicated methods across extractors~~ DONE

Moved `isAccessorForField`, `isExcludedMethod`, `KOTLIN_ACCESSOR`, and `EXCLUDED_FIELDS` into `KotlinMethodFilter`. Both `SymbolExtractor` and `ClassDetailExtractor` now delegate to it.

## ~~64. Fan-in/fan-out interpretation guidance in agentHelp~~ DONE

Added a "Result Interpretation" section to `AgentHelpText` with heuristics for fan-in, fan-out, dead code, change coupling, and hotspots.

## ~~63. `cnavUsages` fuzzy/short-name matching — centralized via ParamDef (Medium value, low effort)~~ DONE

Added `enhancePattern: Boolean` to `ParamDef` and `TaskDef.enhanceProperties()` method that applies `PatternEnhancer.enhance()` to marked params. Added `Project.buildPropertyMap(TaskDef)` overload in `GradleSupport.kt`. Marked `PATTERN`, `OWNER_CLASS`, `TYPE` with `enhancePattern = true`. Updated 5 Gradle tasks and 5 Maven mojos to use centralized enhancement. Removed `PatternEnhancer.enhance()` calls from 5 Config.parse() methods.

## ~~65. Show annotations in `cnavClass` output (High value, medium effort)~~ DONE

Added `AnnotationDetail(name, parameters)` data class. Extracts class-level, method-level, and field-level annotations via ASM `visitAnnotation()`. Simple parameter values (`String`, `int`, `boolean`, etc.) captured via `AnnotationVisitor.visit(name, value)`. All three formatters updated (TEXT, LLM, JSON). `AgentHelpText` JSON schema updated to include annotations. Limitation: enum, array, and nested annotation parameters not yet captured (tracked as 65a).

## ~~65a. Annotation parameter completeness (Low value, low effort)~~ DONE

Added `visitEnum()`, `visitArray()`, and `visitAnnotation()` (nested) callbacks to the ASM `AnnotationVisitor` in `ClassDetailExtractor`. Enum parameters format as `EnumSimpleName.CONSTANT`, array parameters as `[val1, val2]` (bare value for single-element, `[]` for empty), nested annotations as `@AnnotationName(param=val)`. Array inner visitor also handles `visitEnum` for arrays of enums. 7 new tests in `ClassDetailExtractorTest`.

## ~~53+54. `cnavDead` improvements — entry points and confidence scoring (Medium value, medium effort)~~ DONE

**Entry point awareness (53):** Added `-Pexclude-annotated=<annotations>` parameter to exclude classes/methods with specific annotations from dead code results. Comma-separated annotation simple names (e.g., `-Pexclude-annotated=Scheduled,EventListener`). `AnnotationExtractor` created as lightweight scanner that collects annotation simple names on classes and methods. `ParamType` enum (`STRING`, `LIST_STRING`) added to `ParamDef` for centralized list parsing. Wired in `DeadCodeTask.kt`, `DeadCodeMojo.kt`, `HelpText.kt`, and `AgentHelpText.kt`.

**Confidence scoring (54):** Added `DeadCodeConfidence` enum (HIGH, MEDIUM, LOW) and `confidence` field to `DeadCode` data class. `DeadCodeFinder.find()` takes optional `testGraph: CallGraph?` parameter — unreferenced everywhere = HIGH, referenced only in test graph = MEDIUM, class/method has annotations = LOW. All formatters updated (TEXT "Confidence" column, JSON `"confidence"` field, LLM `confidence=`). Test graph built from test source set in `DeadCodeTask.kt` and `DeadCodeMojo.kt`.

## ~~66. `cnavFindStringConstant` — search string literals in bytecode (Medium value, medium effort)~~ DONE

New task to search string literals embedded in bytecode via ASM's `visitLdcInsn()`. Three-layer architecture: `StringConstantExtractor` (parsing), `StringConstantScanner` (resolution), `StringConstantFormatter` + `JsonFormatter.formatStringConstants()` + `LlmFormatter.formatStringConstants()` (formatting). Parameters: `-Ppattern=<regex>` (required, plain regex without camelCase enhancement). Registered as `cnavFindStringConstant` (Gradle) / `cnav:find-string-constant` (Maven). Added to `BuildTool.kt` GRADLE_TASK_NAMES map, `TaskRegistry` (24 goals total), `HelpText.kt`, and `AgentHelpText.kt`.

## ~~57. `cnavTypeHierarchy` — inheritance tree traversal (Medium value, low effort)~~ DONE

New task to show the full type hierarchy for classes matching a pattern. Walks supertypes recursively upward (superclass chain + interfaces) and shows implementors downward via `InterfaceRegistry`. Three-layer architecture: `TypeHierarchyBuilder` (scans all classes into `ClassIndexEntry` map, then walks upward recursively), `TypeHierarchyFormatter` (TEXT) + `JsonFormatter.formatTypeHierarchy()` + `LlmFormatter.formatTypeHierarchy()` (formatting). Domain types: `TypeHierarchyResult`, `SupertypeInfo`, `SupertypeKind`, `ClassIndexEntry`. Parameters: `-Ppattern=<regex>` (required), `-Pprojectonly=true|false` (optional). Filters `java.lang.Object` from supertype chain. Registered as `cnavTypeHierarchy` (Gradle) / `cnav:type-hierarchy` (Maven). Added to `BuildTool.kt` GRADLE_TASK_NAMES map, `TaskRegistry` (25 goals total), `HelpText.kt`, and `AgentHelpText.kt`.

## ~~72. `cnavDead` improvements — test-awareness, reason tagging, prod-only filter~~ DONE

Based on external feedback (60% false positive rate in real-world triage). Three improvements:

**Reason tagging:** Added `DeadCodeReason` enum (`NO_REFERENCES`, `TEST_ONLY`) and `reason` field to `DeadCode` data class. `NO_REFERENCES` means unreferenced in both production and test code (highest removal confidence). `TEST_ONLY` means referenced in test code but not in production (needs human judgment). All formatters updated: TEXT "Reason" column, JSON `"reason"` field, LLM `reason=`.

**`-Pprod-only=true` filter:** New parameter that filters dead code results to only show items with `reason=NO_REFERENCES`, hiding `TEST_ONLY` items. This directly answers the feedback request to distinguish "only used in tests" from "never referenced anywhere."

**Always scan annotations:** `AnnotationExtractor.scanAll()` now always runs (not just when `-Pexclude-annotated` is set), so confidence scoring always benefits from annotation awareness. Previously, classes with `@JsonCreator` or framework annotations would get `HIGH` confidence unless the user explicitly passed `-Pexclude-annotated`.

## ~~75. Framework annotation presets for `cnavDead`~~ DONE

Added `-Dframework=spring` (also: `jpa`, `jackson`, `jakarta`, `validation`, `junit`) parameter to `cnavDead` that auto-excludes known framework annotations from dead code results. Eliminates most false positives in framework-heavy projects without requiring manual `-Dexclude-annotated` lists.

**Spring preset** includes: `Controller`, `RestController`, `Service`, `Component`, `Repository`, `Configuration`, `Bean`, `Scheduled`, `EventListener`, `ExceptionHandler`, `ControllerAdvice`, `Endpoint`, `SpringBootApplication`, `EnableAutoConfiguration`, `ComponentScan`, plus all JPA, Jakarta, and Validation annotations (via set composition).

**Jakarta preset**: `PostConstruct`, `PreDestroy`, `Inject`, `Named`, `Singleton`, `Qualifier`.

**Validation preset**: All `jakarta.validation.constraints.*` (NotNull, NotBlank, NotEmpty, Size, Min, Max, Pattern, Email, Positive, Negative, Past, Future, Digits, DecimalMin, DecimalMax, AssertTrue, AssertFalse, Null, and their OrZero/OrPresent variants), plus `jakarta.validation.Valid` and Hibernate Validator annotations (Length, Range, URL, CreditCardNumber).

**JUnit preset**: `Test`, `BeforeEach`, `AfterEach`, `BeforeAll`, `AfterAll`, `ParameterizedTest`, `RepeatedTest`, `TestFactory`, `Disabled`, `ExtendWith`, `Tag`, `Nested`, `DisplayName`.

**Multiple presets** can be combined: `-Dframework=spring,jackson`. Framework annotations are merged with any explicit `-Dexclude-annotated` values.

**Type-safe AnnotationName**: All annotation storage refactored from raw `String` to `AnnotationName` inline value class (in `DomainTypes.kt`), following the existing `ClassName` and `PackageName` patterns. `AnnotationName` stores the full FQN and provides `.simpleName()`, `.packageName()`, `.matches(Regex)` methods. TEXT/LLM formatters use `.simpleName()` for display; JSON formatter uses `.value` for full FQN output.

**Tested on Spring Petclinic**: reduced dead code results from 22 items (18 false positives) to 8 items (5 `package-info` files + 3 legitimate edge cases). Implementation: `FrameworkPresets.kt` lookup object, wired through `DeadCodeConfig.parse()`, `TaskRegistry.FRAMEWORK` param, Gradle `DeadCodeTask`, and Maven `DeadCodeMojo`.

## ~~77. Interface dispatch resolution in `cnavCallers`/`cnavCallees`~~ DONE

Added interface dispatch resolution to `CallTreeBuilder` so that `cnavCallers` and `cnavCallees` follow calls through interfaces. When tracing callers of `Impl.method()`, also finds callers of `Interface.method()` where `Impl` implements `Interface`. When tracing callees from a call to `Interface.method()`, also shows concrete implementor methods.

**Implementation**: `CallTreeBuilder.resolveInterfaceDispatch()` uses two maps from `InterfaceRegistry`: `implementorMap()` (interface → set of implementor class names) and `classToInterfacesMap()` (class → set of interfaces it implements). Always on — no flag needed since results are strictly better with dispatch resolution.

**Wired into**: Gradle `FindCallersTask`, `FindCalleesTask` (via `InterfaceRegistryCache`), Maven `FindCallersMojo`, `FindCalleesMojo` (via `InterfaceRegistry.build()`). Added `implementorMap()` and `classToInterfacesMap()` convenience methods to `InterfaceRegistry`.

**Tested on Spring Petclinic**: `find-callers` for `OwnerRepository.findById` now correctly shows callers from `OwnerController`, `PetController`, and `VisitController`. 5 new tests (3 in `CallTreeBuilderTest`, 2 in `InterfaceRegistryTest`).

## ~~79. `cnavAnnotations` — query by annotation~~ DONE

New task to query classes and methods by annotation pattern. Parameters: `-Ppattern=<annotation-name-regex>` (required), `-Pmethods=true` (show method-level matches, not just class-level).

**Implementation**: Three-layer architecture following project conventions:
- `AnnotationQueryConfig` — parses pattern (required) and methods flag (optional), 6 tests
- `AnnotationQueryBuilder` — uses `AnnotationExtractor.scanAll()` results, filters with `regex.containsMatchIn()` (substring matching, consistent with all other tasks), returns `AnnotationMatch` / `MethodAnnotationMatch` data classes, 9 tests
- `AnnotationQueryFormatter` — TEXT format output, 6 tests
- `LlmFormatter.formatAnnotations()` — 3 tests
- `JsonFormatter.formatAnnotations()` — 3 tests

**Enhanced `AnnotationExtractor`** with `sourceFile` field via `visitSource()` callback, so results include source file locations.

**Registered as**: `cnavAnnotations` (Gradle) / `cnav:annotations` (Maven). Added `METHODS` ParamDef and `ANNOTATIONS` TaskDef to `TaskRegistry` (26 goals total). Updated `BuildTool`, `HelpText`, `AgentHelpText`.

**Tested on Spring Petclinic**: `cnav:annotations -Dpattern=Controller`, `-Dpattern=Mapping -Dmethods=true`, `-Dpattern=Entity -Dformat=json` all work correctly.

## ~~81. Framework annotation support in `cnavMetrics`~~ DONE

`cnavMetrics` internally calls `DeadCodeFinder.find()` to compute dead code counts for the project health snapshot. Previously it hard-coded `excludeAnnotated = emptySet()` and `classAnnotations = emptyMap()`, producing inflated dead code numbers for framework-heavy projects.

**Changes**:
- `MetricsConfig` — added `excludeAnnotated: List<String>` field, parses both `-Pexclude-annotated` and `-Pframework` parameters (same merge+dedup logic as `DeadCodeConfig`)
- `TaskRegistry.METRICS` — added `EXCLUDE_ANNOTATED` and `FRAMEWORK` params
- `MetricsTask` (Gradle) — reads new params, runs `AnnotationExtractor.scanAll()`, passes results to `DeadCodeFinder.find()`
- `MetricsMojo` (Maven) — same wiring with `@Parameter` annotations
- 4 new tests in `MetricsConfigTest`

## ~~80. Annotation tags on call tree nodes~~ DONE

`cnavCallers` and `cnavCallees` now display annotations on each node in the call tree, making framework entry points (e.g., `@GetMapping`, `@RestController`) immediately visible in call chains.

**Resolution logic** (`CallTreeBuilder.resolveAnnotations()`):
- Method-level annotations take priority (if a method has `@GetMapping`, show that)
- Falls back to class-level annotations (if method has none, show class's `@RestController`)
- Returns empty if neither exists

**Changes**:
- `CallTreeNode` — added `annotations: List<AnnotationTag>` field (defaults to `emptyList()`)
- `AnnotationTag(name: String, framework: String? = null)` — data class for annotations with optional framework origin
- `CallTreeBuilder.build()`/`buildNode()` — accept `classAnnotations` and `methodAnnotations` maps, call `resolveAnnotations()` which uses `FrameworkPresets.frameworkOf()` to resolve framework
- `CallTreeFormatter` (TEXT) — renders `[@GetMapping [spring]]` after source file reference on each node; unknown annotations render without tag
- `LlmFormatter.renderCallTrees()` — same framework tag rendering in compact LLM format
- `JsonFormatter.renderCallNode()` — annotations as `[{"name":"GetMapping","framework":"spring"}]`; `framework` key omitted for unknown annotations
- `FrameworkPresets.frameworkOf()` — reverse lookup with specificity ordering (JPA/Jackson checked before Spring)
- `FindCallersTask`/`FindCalleesTask` (Gradle) — wire `AnnotationExtractor.scanAll()` and pass maps to `CallTreeBuilder.build()`
- `FindCallersMojo`/`FindCalleesMojo` (Maven) — same wiring
- 16 new tests across `CallTreeBuilderTest`, `CallerTreeFormatterTest`, `LlmFormatterTest`, `JsonFormatterTest`, `FrameworkPresetsTest`

## ~~82. Kebab-case parameter consistency and Maven enhanceProperties coverage~~ DONE

Comprehensive refactoring to make all user-facing parameter names use kebab-case and ensure Maven mojos call `enhanceProperties()` for pattern enhancement. Seven sub-tasks:

**1. Migrate all Gradle tasks to `buildPropertyMap(TaskDef)`:** Replaced raw `buildPropertyMap(propertyNames, flagNames)` calls in all 16 Gradle tasks with `buildPropertyMap(TaskDef)`, which centralizes property extraction and pattern enhancement. Made the raw overload `private`.

**2. Split METHOD ParamDef into CALL_PATTERN and METHOD:** `METHOD` was shared by callers/callees and find-usages despite having different semantics. Split into `CALL_PATTERN` ("Class.method name regex") for callers/callees and `METHOD` ("Method name filter") for find-usages only.

**3. Rename `classname` → `pattern` in complexity task:** Complexity task had its own `classname` parameter while 6 other tasks used `pattern` for the same purpose. Switched to the shared `PATTERN` ParamDef, gaining `enhancePattern=true` support for free.

**4. Rename `projectonly` → `project-only`:** Updated across 22 files — `TaskRegistry`, 6 Config parsers, all affected Gradle tasks and Maven mojos, `HelpText`, `AgentHelpText`, `CodeNavigatorPlugin` descriptions, and all tests.

**5. Rename `includetest` → `include-test`:** Updated across 7 files — `TaskRegistry`, `FindInterfaceImplsConfig`, Gradle task, Maven mojo, `HelpText`, `AgentHelpText`, and tests.

**6. Rename `ownerClass` → `owner-class`:** Updated across 10 files — `TaskRegistry`, `FindUsagesConfig` (including error messages), Gradle task, Maven mojo, `HelpText`, `AgentHelpText`, and tests. Internal Kotlin identifiers (`config.ownerClass`, `UsageScanner.scan(ownerClass=...)`) preserved as-is.

**7. Add `enhanceProperties()` to Maven mojos:** 4 mojos were missing the call — `FindCallersMojo`, `FindCalleesMojo`, `ComplexityMojo`, `AnnotationsMojo`. Without it, camelCase pattern shorthand (e.g., `OwnCont` → `Own.*Cont`) didn't work in Maven. The remaining 12 mojos either already had it or have no `enhancePattern=true` params.

**Verified on Spring Petclinic:** All new parameter names tested and confirmed working with Maven plugin.

## ~~Spring Data repository awareness in dead code (Medium value)~~ DONE

Spring Data repositories (e.g., `OwnerRepository extends JpaRepository`) are interfaces whose implementations are generated at runtime by Spring — they have no implementing class in bytecode. `cnavDead` always flagged them as dead code (false positive).

**Approach**: Extended the `FrameworkPresets` system with a third dimension: `supertypeEntryPoints`. If a project interface extends a known framework supertype (like `JpaRepository`, `CrudRepository`, `PanacheRepository`), it is excluded from dead code results entirely — same as annotation-based entry points.

**Changes**:
- `FrameworkPresets.kt`: Added `SPRING_DATA_SUPERTYPES` (12 Spring Data repository interfaces) and `PANACHE_SUPERTYPES` (4 Quarkus Panache types). Extended `Preset` data class with `supertypeEntryPoints` field. Added `resolveSupertypeEntryPoints()` and `resolveAllSupertypeEntryPointsExcept()` methods.
- `DeadCodeFinder.kt`: Added `supertypeEntryPoints` parameter to `find()`. Added `isExcludedBySupertype()` filter that checks if a class's external interfaces overlap with known supertype entry points.
- `DeadCodeConfig.kt`: Resolves `supertypeEntryPoints` from `FrameworkPresets` using the same `exclude-framework` mechanism as annotations.
- `DeadCodeTask.kt` / `DeadCodeMojo.kt`: Wired `config.supertypeEntryPoints` through to `DeadCodeFinder.find()`.
- Tests: 5 new `FrameworkPresetsTest` tests, 3 new `DeadCodeFinderTest` tests, 3 new `DeadCodeConfigTest` tests.

## ~~Auto-detect project classes for DSM / Cycles / Metrics — replace `root-package` with `package-filter`~~ DONE

From user feedback (v0.38): the default DSM with no `rootPackage` produced a 43x43 matrix dominated by `kotlin.*`, `java.*`, `io.ktor.*` — useless. Users had to discover the `-Proot-package` flag by trial and error.

**New design**: Instead of computing a root package prefix, filter DSM/cycles/metrics to project classes only (from compiled `src` directories). No configuration needed for the default case. Three new parameters replace `root-package`:

1. **Default**: Only include project classes (from compiled class directories) — no config needed
2. **`package-filter`** (`-Ppackage-filter`): Optional prefix filter to narrow scope within project
3. **`include-external`** (`-Pinclude-external=true`): Expands view to include non-project dependencies (combinable with `package-filter`)

**`root-package` deprecated**: Aliased to `package-filter` with deprecation warning. CLI (`-P`) takes precedence over plugin config.

**Changes**:
- `ProjectClassScanner` (core) — `scanProjectClasses(classDirectories)` returns `Set<ClassName>` of top-level project classes. Shared by Gradle tasks and Maven mojos.
- `RootPackageDetector` (core) — `detect(List<PackageName>)` finds longest common prefix for **display truncation** (shortening labels), not for filtering.
- `DsmDependencyExtractor` — new `extract(classDirectories, projectClasses, packageFilter, includeExternal)` overload that filters source/target classes by project membership + optional package prefix.
- `DsmConfig`, `CyclesConfig`, `MetricsConfig` — added `packageFilter`, `includeExternal` fields with `root-package` aliasing and precedence logic. Added `deprecations()` method returning warnings when `root-package` is used.
- `CodeNavigatorExtension` — added `packageFilter`, `includeExternal` config properties. Added `resolveProperties()` for merging extension config with CLI. Removed old `resolveRootPackage()`.
- `TaskRegistry` — added `PACKAGE_FILTER` and `INCLUDE_EXTERNAL` param definitions; updated DSM, CYCLES, METRICS task defs.
- `HelpText.kt` — DSM, Cycles, Metrics sections updated with new params; `root-package` documented as deprecated.
- `AgentHelpText.kt` — workflow step 11 updated to use `package-filter`.
- All 3 Gradle tasks (`DsmTask`, `CyclesTask`, `MetricsTask`) and 3 Maven mojos (`DsmMojo`, `CyclesMojo`, `MetricsMojo`) updated to use project class scanning, new extract overload, and display prefix auto-detection.
- Tests: `RootPackageDetectorTest` (11 tests), `DsmDependencyExtractorTest` (4 new), `DsmConfigTest` (10 new), `CyclesConfigTest` (6 new), `MetricsConfigTest` (6 new), `ProjectClassScannerTest` (7 tests), `CodeNavigatorExtensionTest` (10 tests), `TaskRegistryTest` updated.

## ~~Centralize command config — auto-generate help text, plugin registration, and validation from TaskRegistry~~ DONE

Comprehensive centralization of task/parameter metadata so that `TaskRegistry` is the single source of truth. Eliminated manual duplication across TaskRegistry, BuildTool, CodeNavigatorPlugin, ConfigHelpText, HelpText, and Maven mojos. Six sub-tasks completed:

**A. Standardize Config.parse() to use parseFrom(properties):** All Config.parse() methods now use `ParamDef.parseFrom(properties)` instead of raw `properties["key"]` lookups. This ensures param name, type parsing, and default values come from a single definition.

**B. Add deprecated/deprecatedMessage to ParamDef:** `ParamDef` gained `deprecated: Boolean` and `deprecatedMessage: String?` fields. Used for `root-package` deprecation warnings.

**C. Auto-generate HelpText param docs from ParamDef.description:** `HelpText.kt` `pd()` calls now use `param.description` as the default, with optional override for task-specific context. ~58 of 91 `pd()` calls switched to use ParamDef descriptions directly; ~33 retain custom descriptions where they add genuine task-specific value.

**D. Add required validation to ParamDef.parseFrom():** Added `parseRequiredFrom(properties)` method that throws `IllegalArgumentException` when a required param is missing. Applied to 9 Config files: `FindClassConfig`, `FindClassDetailConfig`, `FindSymbolConfig`, `AnnotationQueryConfig`, `FindInterfaceImplsConfig`, `TypeHierarchyConfig`, `CallGraphConfig`, `ContextConfig`, `StringConstantConfig`. `FindUsagesConfig` kept custom validation due to complex mutual exclusion rules.

**E. Runtime validation in enhanceProperties:** `TaskDef.enhanceProperties()` now validates that all property map keys are known param names, throwing `IllegalArgumentException` with the task goal name and unknown keys listed. This catches drift between Maven mojo `buildPropertyMap()` and TaskDef at runtime. Gradle side is validated by construction (`GradleSupport.buildPropertyMap(TaskDef)` uses `TaskDef.params` directly).

**F. Maven mojo simplification evaluated — reflection rejected:** User explicitly rejected reflection-based approaches for auto-generating `buildPropertyMap()`. Maven `@Parameter` fields cannot be eliminated (annotation processing requires them). Runtime validation (sub-task E) is the pragmatic solution: some duplication is accepted as long as it's detected at runtime.

Verified on real projects: all Gradle tasks pass on spring-petclinic, all Maven goals pass on realworld-springboot.

## ~~Filter Kotlin compiler annotations from output~~ DONE

From v0.1.44 field test: `@Metadata`, `@DebugMetadata`, and `@SourceDebugExtension` annotation content leaked into output. Added `AnnotationName.isInternal()` predicate with a blocklist in `DomainTypes.kt`. Filtering in `ClassDetailExtractor.collectAnnotation()` and `AnnotationExtractor.collectAnnotation()`. `InlineMethodDetector` reads `@Metadata` via its own ASM visitor — not affected.

## ~~Fix `cnavDead -Pprod-only=true` — ensure test classes are compiled~~ DONE

From v0.1.44 field test: `-Pprod-only=true` had no effect. `DeadCodeTask` only built test call graph when test class directories existed, but only depended on `classes` (main). Added `requiresTestCompilation` field to `TaskDef` (default `false`), set `true` for `DEAD`, wired `dependsOn("testClasses")` in `CodeNavigatorPlugin`. Changed Maven `DeadCodeMojo` from `COMPILE` to `TEST_COMPILE` phase.

## ~~Add `include-test` to `cnavAnnotations`~~ DONE

From v0.1.44 field test: `cnavAnnotations -Ppattern=Test` returned empty despite `@Test` annotations existing in test sources. Added `INCLUDETEST` to `ANNOTATIONS.params` in TaskRegistry, `includeTest` field to `AnnotationQueryConfig`, conditional test directory inclusion in both `AnnotationsTask.kt` and `AnnotationsMojo.kt`.

## ~~Add `include-test` to `cnavFindSymbol`~~ DONE

From v0.1.44 field test: `cnavFindSymbol -Ppattern=verify` returned empty for test-only methods. Added `INCLUDETEST` to `FIND_SYMBOL.params` in TaskRegistry, `includeTest` field to `FindSymbolConfig`, conditional test directory inclusion in `FindSymbolTask.kt` and `FindSymbolMojo.kt`. Separate cache file (`symbol-index-all.cache`) when test classes included.

## ~~Generate error messages from TaskDef~~ DONE

Error messages in 10 Gradle tasks hardcoded task names, with 5 referencing deprecated aliases (`cnavClass`, `cnavCallers`, `cnavCallees`, `cnavUsages`, `cnavInterfaces`). Added `TaskDef.usageHint(BuildTool)` method that generates usage strings from task params — excludes format/llm and deprecated params, shows required params without brackets and optional params in brackets. Updated all 10 tasks to use generated hints. Removed `usageHint` parameter from `CallTreeTaskSupport.execute()`. `FindUsagesTask` retains custom hint for its two mutually exclusive modes but generates the task name from `TaskDef`.

## ~~Unified source set model — all tasks scan main+test by default~~ DONE

Large cross-cutting refactoring to make filtering/exclusion consistent across all commands. Previously three different strategies existed: Strategy A (10 tasks, main-only), Strategy B (3 tasks, optional `include-test`), Strategy C (6 tasks, always tagged with `prod-only`/`test-only`). Now all bytecode tasks follow Strategy C: scan both main and test source sets by default, tag each class with `SourceSet.MAIN`/`SourceSet.TEST`, support `prod-only`/`test-only` filtering.

**Key design decisions:**
- `SourceSetResolver` utility maps `ClassName → SourceSet` by walking tagged directories (file path math, no bytecode reading). Tasks pass flat `List<File>` to existing scanners unchanged, then use `SourceSetResolver` to filter results.
- Two conversion patterns: Pattern 1 (class-level tasks) filters after scanning via `resolver.sourceSetOf()`. Pattern 2 (package-level tasks like DSM, Cycles, Metrics) filters at the input directory level before aggregation.
- `PROJECTONLY` default changed from `false` to `true`, eliminating the `PROJECTONLY_ON` variant.
- `include-test` deprecated (test is now always included). `SOURCE_SET_PARAMS` (`prod-only`, `test-only`) added to all bytecode TaskDefs.

**Converted tasks (Strategy A → C):** ListClasses, FindClass, FindClassDetail, StringConstant, TypeHierarchy, ChangedSince, PackageDeps, DSM, Cycles, Metrics.
**Converted tasks (Strategy B → C):** FindSymbol, FindInterfaces, Annotations.
**Updated help text:** HelpText.kt and AgentHelpText.kt updated to reflect new model. Deprecated `include-test` references replaced with `prod-only`/`test-only`.
**Test coverage:** Config tests for all converted tasks verify `prodOnly`/`testOnly` parsing and defaults. HelpTextTest updated to exclude deprecated params.

## ~~Rename `exclude-framework` to `treat-as-dead`~~ DONE

The `exclude-framework` parameter name had confusing inverted semantics: `-Pexclude-framework=spring` meant "remove Spring from the protection list" (treat Spring-annotated code as potentially dead), not "exclude Spring from scanning." Renamed to `treat-as-dead` which reads naturally: `-Ptreat-as-dead=spring` means "treat Spring-annotated code as potentially dead."

**Changes**: `TaskRegistry.kt` (`EXCLUDE_FRAMEWORK` → `TREAT_AS_DEAD`, param name `"exclude-framework"` → `"treat-as-dead"`), `DeadCodeConfig.kt`, `MetricsConfig.kt`, `DeadCodeMojo.kt`, `MetricsMojo.kt` (`@Parameter(property = "treat-as-dead")`), `HelpText.kt`, `AgentHelpText.kt`, plus all corresponding tests (`DeadCodeConfigTest`, `MetricsConfigTest`, `TaskRegistryTest`).

## ~~Uniform hint delivery with JSON/LLM output~~ DONE

When a query returns no results, agents consuming JSON/LLM output previously received just `[]` — losing the actionable hints that TEXT output showed (e.g., "try -Pmethods=true"). Now `OutputWrapper.emptyResult()` accepts an optional `hints: List<String>` parameter and emits `{"results":[],"hints":["..."]}` for JSON/LLM output. TEXT output appends hints as plain text lines after the message.

**Changes**: `OutputWrapper.emptyResult()` — new `hints` parameter with `emptyList()` default. `AnnotationQueryFormatter.noResultsHints()` and `UsageFormatter.noResultsTarget()` + `noResultsHints()` split from the old `noResultsGuidance()` methods. `AnnotationsTask.kt`, `FindUsagesTask.kt` (Gradle), and `FindUsagesMojo.kt` (Maven) updated to pass hints. `AgentHelpText.kt` schemas section documents the hint shape. Tests: 4 new `OutputWrapperTest` tests, updated `AnnotationQueryFormatterTest` and `UsageFormatterTest`.

## ~~Filter coroutine continuation classes from caller/callee trees~~ DONE

From field tests (v0.1.44 and v0.1.45): suspend function caller/callee trees showed inner `$1.invokeSuspend` continuation classes with synthetic fields. `-Pfilter-synthetic=true` filtered data class methods but not coroutine continuations. v0.1.46 field test confirmed fix: 413→348 entries, 12 coroutine lambdas properly filtered by the existing `KotlinMethodFilter` / `LambdaCollapser` infrastructure.

## ~~Dead code: test source classes excluded by `-Pprod-only=true`~~ DONE

From v0.1.46 field test: `-Pprod-only=true` had no visible effect on TAC (146→146 items). Root cause: test classes (e.g. `FooTest`) are tagged `NO_REFERENCES` instead of `TEST_ONLY` because JUnit invokes them reflectively, not via source-level calls. Fix: added `testClasses: Set<ClassName>` parameter to `DeadCodeFinder.find()`. When `prodOnly=true`, items with `NO_REFERENCES` reason whose className is in `testClasses` are filtered out. Both Gradle task and Maven mojo derive `testClasses` from `testGraph?.projectClasses() ?: emptySet()`.

**Changes**: `DeadCodeFinder.kt` — new `testClasses` parameter in `find()`, filter updated. `DeadCodeTask.kt` and `DeadCodeMojo.kt` — wire `testClasses`. `DeadCodeFinderTest.kt` — 3 new tests.

## ~~CamelCase splitting stopword list~~ DONE

From v0.1.46 field test: `cnavFindSymbol -Ppattern=TermsAndConditionsService` produced regex `Terms.*And.*Conditions.*Service` with mandatory stopword segments, causing zero matches when the class name contained "And" only as a word boundary. Fix: rewrote `PatternEnhancer.enhance()` from a one-line regex replace to a segment-based approach. Splits on camelCase boundaries, checks each segment against a stopword set (`And`, `Or`, `Of`, `The`, `For`, `In`, `To`, `By`, `On`, `With`), wraps stopwords in `(?:...)?` making them optional. Non-stopword segments get `.*` prefix as before. Patterns containing regex metacharacters or dots pass through unchanged.

**Changes**: `PatternEnhancer.kt` — rewritten with stopword logic. `PatternEnhancerTest.kt` — 5 new tests.

## ~~Fix `cnavFindSymbol` broad matching~~ DONE

From v0.1.44 field test: searching "Service" returned 272+ results because it substring-matched package `selfservice.*`. Root cause: `SymbolFilter.filter()` applied `containsMatchIn` against all four fields (packageName, className FQN, symbolName, sourceFile). Fix: when pattern contains no dots (simple name search), match only against `symbolName` and `sourceFile`. When pattern contains dots, keep full FQN/package matching.

**Changes**: `SymbolFilter.kt` — `isQualified` heuristic, simpleName-only matching for unqualified patterns. `SymbolFilterTest.kt` — 3 new tests, 1 updated.

## ~~Fix `cnavFindClass` broad matching~~ DONE

From v0.1.44 field test: `-Ppattern=main` matched all 58 classes because "main" substring-matches "domain" in FQN `com.example.domain.*`. Fix: same approach as FindSymbol — when pattern contains no dots, match against `className.simpleName()` and `sourceFileName` only. When pattern contains dots, match against full FQN.

**Changes**: `ClassFilter.kt` — `isQualified` heuristic, simpleName-only matching for unqualified patterns. `ClassFilterTest.kt` — 2 new tests, 1 updated.

## ~~Ktor framework preset for dead code~~ DONE

Added `ktor` framework preset for `cnavDead -Ptreat-as-dead=ktor`. Ktor is DSL/lambda-based (not annotation-based), so the preset only has `supertypeEntryPoints`: `AuthenticationProvider`, `BaseApplicationPlugin`, `BaseRouteScopedPlugin`, `ContentConverter`, `Template`. No annotation entry points.

**Changes**: `FrameworkPresets.kt` — added `KTOR_SUPERTYPES` and `ktor` preset entry. `FrameworkPresetsTest.kt` — 4 new tests.

## ~~Receiver-type-based entry point detection for Ktor dead code~~ DONE

Ktor extension functions on `Route` and `Application` are framework entry points but have no annotations or interface inheritance. Added receiver type detection: `ReceiverTypeExtractor` scans Kotlin `@Metadata` to find the receiver type of top-level extension functions (compiled as `*Kt` classes). `DeadCodeFinder` now has `classReceiverTypes` and `receiverTypeEntryPoints` parameters. `FrameworkPresets` gained `receiverTypeEntryPoints` dimension with Ktor routes/application types. Wired through `DeadCodeConfig`, `DeadCodeTask`, and `DeadCodeMojo`.

## ~~`cnavDead -Ptest-only=true` filter~~ DONE

New `-Ptest-only=true` parameter that filters dead code results to only show items with `reason=TEST_ONLY`. Complementary to `-Pprod-only=true`. Wired through `DeadCodeConfig`, `DeadCodeFinder`, `DeadCodeTask`, and `DeadCodeMojo`. Added to `TaskRegistry` and help text.

## ~~Nimbus JWT interfaces added to Ktor supertype entry points~~ DONE

Added `DefaultJWTClaimsVerifier` and `JWTClaimsSetVerifier` to `KTOR_SUPERTYPES` in `FrameworkPresets`. These are Nimbus JWT types commonly used in Ktor auth projects.

## ~~Bug #13: InterfaceRegistry superclass tracking~~ DONE

`InterfaceRegistry.extractInterfaces()` only captured Java interfaces from ASM's `visit()` callback, completely ignoring the `superName` parameter. This meant `externalInterfacesOf()` never returned abstract class parents like `DefaultJWTClaimsVerifier`, so `DeadCodeFinder.isExcludedBySupertype()` couldn't match against them. Fixed by also capturing `superName` (excluding `java/lang/Object`) and including it in the supertypes list alongside interfaces. Now `externalInterfacesOf()` returns both external interfaces and external superclasses.

**Changes**: `InterfaceRegistry.kt` — `extractInterfaces()` captures `superName`, builds combined supertypes list. `InterfaceRegistryTest.kt` — 4 new tests for superclass tracking. `DeadCodeFinderTest.kt` — 1 new test for abstract superclass entry point exclusion.

## ~~Bug #16: SymbolFilter source file matching removed~~ DONE

`SymbolFilter.filter()` had `regex.containsMatchIn(symbol.sourceFile)` in both the qualified and unqualified matching branches. This caused searching for `Service` to match every symbol in any file named `*Service.kt` (e.g., `Faktura.customerId` in `OpplastingService.kt`). Removed source file matching from both branches.

**Changes**: `SymbolFilter.kt` — removed `regex.containsMatchIn(symbol.sourceFile)` from both branches. `SymbolFilterTest.kt` — existing "matches against source file" test inverted to "does not match against source file", plus 1 new test.

## ~~Bug #15: Empty-result output consistency~~ DONE

`FindSymbolTask`, `FindClassTask`, `ListClassesTask`, and `CyclesTask` (plus their Maven counterparts) called `formatAndWrap()` directly without checking for empty results, producing bare `[]` in JSON/LLM mode instead of `{"results":[],"hints":[]}`. Added `isEmpty()` guards with `OutputWrapper.emptyResult()` calls to all 8 files (4 Gradle tasks + 4 Maven mojos).

**Changes**: `FindSymbolTask.kt`, `FindClassTask.kt`, `ListClassesTask.kt`, `CyclesTask.kt`, `FindSymbolMojo.kt`, `FindClassMojo.kt`, `ListClassesMojo.kt`, `CyclesMojo.kt` — added empty-result guards.

## ~~Structural distance between packages~~ DONE — `[Balanced Coupling]`

**Value: medium** | **Effort: low**

New standalone `cnavDistance` task computing structural distance between coupled packages. Distance represents how far knowledge must travel (package hierarchy hops) between two coupled packages.

- **`PackageDistanceCalculator`** — pure function computing tree distance between two `PackageName`s.
- **`PackageDistanceBuilder`** — takes a `DsmMatrix`, computes distances for all dependency edges, supports `top` and `packageFilter`.
- **`PackageDistanceFormatter`** — TEXT format with `noResultsHints`.
- **`PackageDistanceConfig`** — config data class parsing from property map.
- **Output formats**: TEXT (`source → target  distance=N  deps=N`), JSON (`{source, target, distance, deps}`), LLM (compact `source->target distance=N deps=N`).
- **Gradle task**: `PackageDistanceTask` registered as `cnavDistance`.
- **Maven mojo**: `PackageDistanceMojo` with goal `distance`.

**Changes**: New files: `PackageDistanceCalculator.kt`, `PackageDistanceBuilder.kt`, `PackageDistanceConfig.kt`, `PackageDistanceFormatter.kt`, `PackageDistanceTask.kt`, `PackageDistanceMojo.kt` + test files. Modified: `TaskRegistry.kt`, `JsonFormatter.kt`, `LlmFormatter.kt`, `HelpText.kt`, `AgentHelpText.kt`, `CodeNavigatorPlugin.kt`.

## ~~Integration strength classification~~ DONE — `[Balanced Coupling]`

**Value: high** | **Effort: medium**

New standalone `cnavStrength` task classifying each inter-package dependency edge by the type of knowledge shared, based on Vlad Khononov's Balanced Coupling theory. Three strength levels (weakest to strongest):

1. **CONTRACT** — target type is an interface or abstract class. Caller depends on a contract, not an implementation.
2. **MODEL** — target type is a Kotlin data class (detected via `component1` + `copy` methods in bytecode) or a Java record (`ACC_RECORD` flag). Caller knows the shape of the data, not behavior.
3. **FUNCTIONAL** — target type is any other concrete class. Caller depends on behavior and implementation.

Classification is based on the **target type**, not individual method calls. When a package pair has edges at multiple strength levels, the **strongest** level wins. Only inter-package edges are classified — intra-package coupling is intentionally excluded.

- **`ClassTypeCollector`** — first-pass bytecode scanner using `ClassKindVisitor` that reads class flags and builds a `Map<ClassName, ClassKind>`. `ClassKind` enum: INTERFACE, ABSTRACT, DATA_CLASS, RECORD, CONCRETE.
- **`StrengthClassifier`** — classifies dependency edges using the class type registry, aggregates per package pair with strongest-wins logic. Returns `StrengthResult` with `List<PackageStrengthEntry>`.
- **`StrengthConfig`** — config data class parsing `top`, `package-filter`, `format` from property map.
- **`StrengthFormatter`** — TEXT format with `noResultsHints`.
- **Output formats**: TEXT (`source → target  strength=FUNCTIONAL  (contract=1, model=2, functional=3)`), JSON (`{source, target, strength, counts}`), LLM (compact `source->target strength=FUNCTIONAL contract=1 model=2 functional=3`).
- **Gradle task**: `IntegrationStrengthTask` registered as `cnavStrength`.
- **Maven mojo**: `IntegrationStrengthMojo` with goal `strength`.

**Changes**: New files: `ClassTypeCollector.kt`, `StrengthClassifier.kt`, `StrengthConfig.kt`, `StrengthFormatter.kt`, `IntegrationStrengthTask.kt`, `IntegrationStrengthMojo.kt` + test files (`ClassTypeCollectorTest.kt`, `StrengthClassifierTest.kt`, `StrengthConfigTest.kt`, `StrengthFormatterTest.kt`). Modified: `TaskRegistry.kt`, `JsonFormatter.kt`, `LlmFormatter.kt`, `HelpText.kt`, `AgentHelpText.kt`, `CodeNavigatorPlugin.kt`.

---

## Fix `top` default in AgentHelpText and HelpText

**Value: medium** | **Effort: low**

`TaskRegistry.TOP.defaultValue` is `"50"`, and `AgentHelpText.kt` / `HelpText.kt` render `default: 50` for all tasks that use the `top` parameter. However, Distance and Strength override the default to `Int.MAX_VALUE` (unlimited) in their `parseTop()` calls. An agent reading the help would wrongly believe it gets only 50 results from `cnavDistance` or `cnavStrength`.

- **Approach**: Allow `ParamDef.defaultValue` to be overridden per-task in `TaskDef`. When rendering help, use the task-specific default if present, otherwise fall back to `ParamDef.defaultValue`. For Distance/Strength, the rendered default would be "unlimited" or "all".
- **Alternative**: Duplicate the `TOP` param definition with a different default for Distance/Strength. Simpler but less DRY.

**Implementation**: Added `paramDefaultOverrides: Map<String, String>` to `TaskDef` with `effectiveDefault(param)` helper. `DISTANCE` and `STRENGTH` have `paramDefaultOverrides = mapOf("top" to "all")`. `AgentHelpText.appendGlobalParameters()` groups tasks by effective default when defaults differ. `HelpText.paramDoc()` accepts optional `task` parameter for task-specific defaults.

**Changes**: Modified: `TaskRegistry.kt` (added `paramDefaultOverrides` + `effectiveDefault()`), `AgentHelpText.kt` (grouped defaults), `HelpText.kt` (per-task defaults). New tests: `AgentHelpTextTest.kt` (1 test), `HelpTextTest.kt` (3 tests).

---

## Resolve extractor filtering asymmetry

**Value: low** | **Effort: medium**

DSM and Cycles tasks pass `config.packageFilter` directly to `DsmDependencyExtractor` (filtering during extraction), while Distance and Strength pass `PackageName("")` (no extraction-level filter) and filter afterwards. This means Distance and Strength extract more data than needed — they scan all project classes even when the user specified a `package-filter`.

- **Approach**: Added `filterTargets: Boolean = true` parameter to `DsmDependencyExtractor.extract()` and `extractFromClassWithProjectFilter()`. When `filterTargets = false`, the target-side `startsWith(packageFilter)` check is skipped at extraction time. Distance/Strength pass the actual `packageFilter` with `filterTargets = false` (source-only filtering). DSM/Cycles unchanged (default `filterTargets = true`).
- **Risk**: Must not reintroduce the double-filter bug (FIX 1). Source-side filtering at extraction is safe; target-side filtering remains at result level for Distance/Strength.

**Changes**: Modified: `DsmDependencyExtractor.kt` (added `filterTargets` parameter), `PackageDistanceTask.kt`, `IntegrationStrengthTask.kt`, `PackageDistanceMojo.kt`, `IntegrationStrengthMojo.kt` (pass actual `packageFilter` with `filterTargets = false`). New tests: `DsmDependencyExtractorTest.kt` (4 tests).

---

## ~~Volatility per package~~ DONE — `[Balanced Coupling]`

**Value: high** | **Effort: low-medium**

Aggregates file-level git metrics (change frequency, churn) to the package level. Produces a per-package volatility score.

- **`FileToPackageMapper`** — maps git file paths to package names by stripping known source roots (`src/main/kotlin/`, `src/main/java/`, `src/test/kotlin/`, `src/test/java/`). Files with unrecognized source roots are silently skipped.
- **`PackageVolatilityBuilder`** — takes `HotspotBuilder` output (file-level) and aggregates to package level. Data types: `PackageVolatility(packageName, revisions, totalChurn, fileCount, avgRevisionsPerFile)` and `PackageVolatilityResult`.
- **`PackageVolatilityFormatter`** — text table output.
- **`VolatilityConfig`** — config parsing following `HotspotConfig` pattern.
- **Output formats**: TEXT (table), JSON (array), LLM (compact key=value).
- **Gradle task**: `PackageVolatilityTask` registered as `cnavVolatility`.
- **Maven mojo**: `PackageVolatilityMojo` with goal `volatility`.
- **Registered as task 31** in `TaskRegistry` under GIT_HISTORY category.

**Changes**: New files: `FileToPackageMapper.kt`, `PackageVolatilityBuilder.kt`, `PackageVolatilityFormatter.kt`, `VolatilityConfig.kt`, `PackageVolatilityTask.kt`, `PackageVolatilityMojo.kt` + 4 test files (26 tests total). Modified: `TaskRegistry.kt`, `JsonFormatter.kt`, `LlmFormatter.kt`, `HelpText.kt`, `AgentHelpText.kt`, `CodeNavigatorPlugin.kt`, `TaskRegistryTest.kt`.

## ~~`cnavBalance` — balanced coupling analysis~~ DONE — `[Balanced Coupling]`

**Value: high** | **Effort: medium**

Composite Balanced Coupling heuristic. For each package pair, evaluates three dimensions — integration strength, structural distance, and package volatility — and produces a single verdict.

- **Formula**: `modularity = strength XOR distance; balance = modularity OR NOT volatility`
- **Verdicts**: `BALANCED` (good modularity), `TOLERABLE` (poor modularity but low volatility), `OVER_ENGINEERED` (low strength + low distance), `DANGER` (high strength + high distance + high volatility).
- **Thresholds**: Distance >= 3 = HIGH, Strength >= FUNCTIONAL = HIGH, Volatility uses median-based classification (revisions >= project median = HIGH).
- **`BalanceBuilder`** — core logic with `BalanceVerdict` enum, `BalanceEntry` and `BalanceResult` data classes, `classify()`, `suggest()`, `computeMedianRevisions()`.
- **`BalanceConfig`** — hybrid config parsing DSM + git history params.
- **`BalanceFormatter`** — TEXT format with verdict, strength, distance, volatility display, suggestions.
- **Output formats**: TEXT (table with suggestions), JSON (array), LLM (compact key=value).
- **Gradle task**: `BalanceTask` registered as `cnavBalance` — hybrid task orchestrating bytecode (strength + distance) and git history (volatility).
- **Maven mojo**: `BalanceMojo` with goal `balance`.
- **Registered as task 32** in `TaskRegistry` under COMPOSITE category (new category added to distinguish composite/aggregated tasks from base analyses).

**Changes**: New files: `BalanceBuilder.kt`, `BalanceConfig.kt`, `BalanceFormatter.kt`, `BalanceTask.kt`, `BalanceMojo.kt` + 3 test files (15 tests total). Modified: `TaskRegistry.kt` (added COMPOSITE category + BALANCE TaskDef), `JsonFormatter.kt`, `LlmFormatter.kt`, `HelpText.kt`, `AgentHelpText.kt`, `CodeNavigatorPlugin.kt`, `ConfigHelpText.kt`, `TaskRegistryTest.kt`, `AgentHelpTextTest.kt`, `ConfigHelpTextTest.kt`.

## ~~`cnavRenameParam` — deterministic parameter renaming via OpenRewrite~~ DONE

**Value: high** | **Effort: medium**

First deterministic refactoring task. Renames a Kotlin function parameter — updating the declaration, all body references (including string template `$name` references), and all named-argument call sites across the project. Uses OpenRewrite (`rewrite-kotlin` 8.78.6) for AST-based source transformation. Source-level task (no compilation required for the rename itself, though tests verify compile-before and compile-after).

**Usage**:
```bash
# Preview mode (default) — shows what would change
./gradlew cnavRenameParam -Ptarget-class=com.example.UserService -Pmethod=findUsers -Pparam=limit -PnewName=maxResults

# Apply mode — writes changes to source files
./gradlew cnavRenameParam -Ptarget-class=com.example.UserService -Pmethod=findUsers -Pparam=limit -PnewName=maxResults -Ppreview=false
```

**Implementation**:
- `RenameParamConfig` — config data class with `parse()` companion, validates all 4 required params (target-class, method, param, newName) plus optional `preview` flag. 8 tests.
- `RenameParamRewriter` — OpenRewrite-based rewriter using `KotlinParser` and `RenameParamVisitor` (`KotlinIsoVisitor<ExecutionContext>`). Handles three rename dimensions: parameter declarations (`visitMethodDeclaration`), body references including string templates (`visitIdentifier`), and named arguments at call sites (`visitMethodInvocation`). Scoped by `inTargetClass`/`inTargetMethod` flags. Returns `RenameResult` with `List<RenameChange>` diffs. `preview` parameter controls whether changes are written to disk (default: preview only). 5 tests (all with compile-before/compile-after verification).
- `RenameParamFormatter` — TEXT/JSON/LLM formatting with unified diff computation. 7 tests.
- `RenameParamTask` (Gradle) — follows `SizeTask` pattern, uses `project.sourceDirectories()` to find source files. `requiresCompilation = false`.
- Registered as `RENAME_PARAM_TASK` in `TaskRegistry` (goal `rename-param`, category `SOURCE`). Task count: 35.

**Param naming**: Uses `target-class` instead of `class` because Gradle's `findProperty("class")` returns the Java Class object of the Project, not the user's `-Pclass=...` value. Also fixed `GradleSupport.kt` property detection to use `project.gradle.startParameter.projectProperties.keys` instead of `findProperty`/`hasProperty` to avoid false positives from Gradle internals (e.g., `hasProperty("init")` returns true even without `-Pinit`).

**OpenRewrite version**: Upgraded from `rewrite-kotlin:8.56.1` to `8.78.6` because the 8.56.1 version ships `kotlin-compiler-embeddable:1.9.25` which cannot run on Java 25 (throws `IllegalArgumentException: 25.0.2` when parsing `JavaVersion`). Version 8.78.6 ships `kotlin-compiler-embeddable:2.2.0` and works correctly.

**Known limitations**:
- Companion object methods require specifying `target-class` as the companion's FQN (e.g., `com.example.Foo$Companion`). Using the outer class name does NOT find companion methods.
- OpenRewrite's `KotlinIsoVisitor` does not traverse deeply nested lambda expressions (3+ levels, e.g., Ktor DSL `rateLimit { post { withAdminPoll { ... } } }`). Call sites inside such lambdas are not renamed. Shallow lambda nesting works correctly. Output includes a "Compile to verify all call sites were updated." recommendation when changes are applied.
- Constructor parameter renaming not tested.
- Maven mojo not yet created (Gradle only).

**Bug fixes (post-initial release)**:
- **PREVIEW flag type mismatch**: `PREVIEW` was declared with `type = ParamType.BOOLEAN` but `flag = true`. Changed to `type = ParamType.FLAG` so `-Ppreview` works correctly without a value.
- **Named argument key collision**: `visitIdentifier` in `RenameParamVisitor` was renaming the key side of named arguments in calls to OTHER methods/constructors. Added guard to skip identifiers that are the left side of `J.Assignment` parent nodes. E2E verified on bass-ra-backend.
- **Preview/Apply inversion**: Default behavior changed from preview-by-default to apply-by-default. `-Ppreview` flag opts into dry-run mode. JSON output key changed from `"applied"` to `"preview"`.
- **Compile recommendation**: All three output formats (TEXT, JSON, LLM) now include a "Compile to verify all call sites were updated." recommendation when changes are applied.

**E2E validated**: bass-self-service (`Translations.getLocalizedString` param `localizedFeature` → `feature`, declaration + body references), greitt (`ParticipantService.findOrCreateResponse` param `verifiedEmail` → `emailAddress`, declaration + 20 named-argument call sites across 2 files).

**Changes**: New files: `RenameParamConfig.kt`, `RenameParamRewriter.kt`, `RenameParamFormatter.kt`, `RenameParamTask.kt`, `RenameParamConfigTest.kt`, `RenameParamRewriterTest.kt`, `RenameParamFormatterTest.kt`, `OpenRewriteApiExplorationTest.kt`. Modified: `build.gradle.kts` (OpenRewrite deps 8.56.1 → 8.78.6), `TaskRegistry.kt`, `HelpText.kt`, `AgentHelpText.kt`, `GradleSupport.kt`, `CodeNavigatorPlugin.kt`, `TaskRegistryTest.kt`.

## ~~Reduce test data duplication across formatter tests~~ DONE

**Value: high** | **Effort: medium**

Extracted shared test fixtures (object mothers) from duplicated test data construction across formatter test files, and extracted repeated filter/map patterns in `DeadCodeFinderTest`.

**Shared formatter fixtures** (`FormatterTestFixtures.kt`, 163 lines):
- 14 object mother functions: `aContextResult()`, `aMetricsResult()`, `aDeadCode()`, `aHotspotEntry()`, `aChangeCouplingEntry()`, `aCodeAgeEntry()`, `aChurnEntry()`, `anAuthorEntry()`, `aPackageVolatility()`, `aBalanceEntry()`, `aPackageStrengthEntry()`, `aPackageDistanceEntry()`, `aCycleDetail()`, `aCycleEdge()`.
- All use sensible defaults with data classes enabling `.copy()` for test variations.
- Follows existing project precedent (`TestCallGraphBuilder.kt`, `DelegationFixtures.kt`).

**Formatter test reductions**:
- `ContextFormatterTest.kt`: -24 lines (replaced inline `ContextResult` construction with `aContextResult()`)
- `JsonFormatterTest.kt`: -97 lines (~13 inline constructions replaced)
- `LlmFormatterTest.kt`: -103 lines (~14 inline constructions replaced)
- `CallerTreeFormatterTest.kt`: Evaluated and skipped — `CallTreeNode` constructions are per-test tree structures that test specific rendering behavior; extracting would reduce readability.

**DeadCodeFinderTest helper extraction**:
- Added 4 private extension functions: `deadClassNames()`, `deadMethodNames()`, `deadClasses()`, `deadMethods()` — replacing ~40 repeated `filter { it.kind == ... }.map { it.className.value }` expressions across the 1474-line test file.
- The 9 occurrences of `dead.map { it.className.value }` (mapping ALL dead items without kind filter) were left as-is since they're semantically different.

**Changes**: New file: `FormatterTestFixtures.kt`. Modified: `ContextFormatterTest.kt`, `JsonFormatterTest.kt`, `LlmFormatterTest.kt`, `DeadCodeFinderTest.kt`.

## ~~`cnavMoveClass` — move class to different package~~ DONE

**Value: high** | **Effort: high**

Moves a Kotlin class to a different package and updates all references project-wide using OpenRewrite's `ChangeType` recipe with classpath-based type resolution.

**Parameters**: `-Ptarget-class=<FQN>` (required), `-Pnew-package=<pkg>` (required), `-Ppreview` (dry-run mode).

**Implementation**:
- Uses OpenRewrite's `ChangeType(oldFqcn, newFqcn, null)` recipe which handles: import updates, type reference updates (fields, params, return types, generics), and package declaration/class name updates via its inner `ChangeClassDefinition` visitor.
- `KotlinParser.builder().classpath(...)` for precise type resolution from compiled classes.
- `InMemoryLargeSourceSet`-based recipe execution via `recipe.run()` returning `RecipeRun` with `changeset.allResults`.
- File relocation: moves the `.kt` file from old package directory to new package directory.
- `requiresCompilation = true` — project must be compiled before move-class runs.
- Gradle task uses classloader-isolated `WorkAction` pattern (same as `cnavRenameMethod`).
- `rewrite-java-21` runtime dependency required for `ChangeType`'s `JavaTemplate` (added to both `build.gradle.kts` and `pom.xml`).

**Key discovery**: `ChangeType` handles everything including the package declaration change — the rewriter only needs to additionally handle file relocation to the new package directory.

**Output**: TEXT, JSON, and LLM formats. LLM output includes "Compile to verify all references were updated." recommendation.

**Changes**: New files: `MoveClassConfig.kt`, `MoveClassRewriter.kt`, `MoveClassFormatter.kt`, `MoveClassTask.kt`, `MoveClassWorkAction.kt`, `MoveClassMojo.kt`, `MoveClassConfigTest.kt`, `MoveClassRewriterTest.kt`, `MoveClassFormatterTest.kt`. Modified: `build.gradle.kts`, `pom.xml`, `TaskRegistry.kt`, `CodeNavigatorPlugin.kt`, `HelpText.kt`, `AgentHelpText.kt`, `TaskRegistryTest.kt`, `FileSizeScannerTest.kt`. Test fixtures: 4 Kotlin files under `test-project/src/main/kotlin/com/example/variants/moveclass/`.

## ~~Companion object support in rename rewriters + constructor val/var warnings~~ DONE

**Value: medium** | **Effort: medium**

Three improvements to the refactoring tasks:

### Companion object support in `cnavRenameMethod` and `cnavRenameParam`

Both rename rewriters now match companion object methods when the user specifies the outer class FQN. OpenRewrite represents companion objects as `Foo.Companion` (dot-separated) in its AST. A shared `matchesClassOrCompanion()` helper in `RewriterSupport.kt` checks both `.Companion` (dot) and `$Companion` (dollar sign) forms.

- `RenameMethodVisitor.isTargetOrImplementor()` — matches companion class FQNs
- `RenameMethodVisitor.visitMethodInvocation()` — matches companion call sites
- `RenameParamVisitor.visitClassDeclaration()` — matches companion class FQNs
- `RenameParamVisitor.visitMethodInvocation()` — matches companion call sites

### Constructor val/var parameter warning in `cnavRenameParam`

Renaming a `val`/`var` constructor parameter requires renaming the property, all `instance.property` access sites, and getters/setters project-wide — a fundamentally harder refactoring than method parameter renaming. Rather than implementing full property rename, the rewriter detects constructor val/var params via regex scan and emits a warning explaining the limitation. Full property rename deferred to future `cnavRenameProperty` task.

- Added `warnings: List<String>` field to `RenameResult` data class
- Updated `toJson()`/`fromJson()` to serialize/deserialize warnings
- Added `isConstructorMethod()` and `isValVarConstructorParam()` detection
- Warning displayed in all 3 formatter output formats (TEXT, JSON, LLM)

### Updated recommendation messages across all formatters

Changed `COMPILE_RECOMMENDATION` constants in `MoveClassFormatter`, `RenameMethodFormatter`, and `RenameParamFormatter` to warn that "refactorings are not always fully accurate" and recommend compiling to verify.

### Added `cnavMoveClass` to README task table

**Changes**: Modified: `RewriterSupport.kt` (shared `matchesClassOrCompanion()`), `RenameMethodRewriter.kt` (companion matching), `RenameParamRewriter.kt` (companion matching + constructor warning + warnings field), `RenameParamFormatter.kt` (warnings display + recommendation), `RenameMethodFormatter.kt` (recommendation), `MoveClassFormatter.kt` (recommendation), `README.md` (task table). Test files: `RenameMethodRewriterTest.kt` (2 tests), `RenameParamRewriterTest.kt` (5 tests), `RenameParamFormatterTest.kt` (4 tests), `MoveClassFormatterTest.kt` (assertion fix), `FileSizeScannerTest.kt` (count update). New test fixtures: `test-project/src/main/kotlin/com/example/variants/companion/` (2 files), `test-project/src/main/kotlin/com/example/variants/constructorparam/` (1 file).

## ~~`cnavRenameProperty` — rename val/var property with full access site updates~~ DONE

**Value: high** | **Effort: medium**

Full property rename task that handles what `cnavRenameParam` deferred: renaming a Kotlin `val`/`var` constructor property and updating all access sites project-wide. Uses OpenRewrite for AST-based source transformation.

**Parameters**: `-Ptarget-class=<FQN>` (required), `-Pproperty=<name>` (required), `-Pnew-name=<name>` (required), `-Ppreview` (dry-run mode).

**Implementation**:
- `RenamePropertyRewriter` — OpenRewrite-based rewriter using `KotlinParser` and `RenamePropertyVisitor` (`KotlinIsoVisitor<ExecutionContext>`). Handles four rename dimensions:
  - `visitMethodDeclaration` — renames constructor parameter declarations (`val`/`var` in primary constructor)
  - `visitNewClass` — renames named arguments at constructor call sites (discovered as `J.NewClass` in AST, not `J.MethodInvocation`)
  - `visitMethodInvocation` — renames named arguments in `copy()` and other method calls
  - `visitIdentifier` — renames property access sites via `fieldType.owner` (cast to `JavaType.FullyQualified`)
- Returns `RenamePropertyResult` with `List<RenameChange>` diffs, `toJson()`/`fromJson()` serialization.
- `RenamePropertyConfig` — config data class with `parse()` companion, validates all 3 required params.
- `RenamePropertyFormatter` — TEXT/JSON/LLM formatting with `COMPILE_RECOMMENDATION`.
- Gradle: `RenamePropertyTask` + `RenamePropertyWorkAction` (classloader isolation).
- Maven: `RenamePropertyMojo`.
- `requiresCompilation = false` (source-level AST transformation).

**Key technical discovery**: Kotlin constructor calls are represented as `J.NewClass` in OpenRewrite's AST, not `J.MethodInvocation`. This required adding `visitNewClass()` to the visitor for named argument renaming at constructor call sites.

**`cnavRenameParam` updated**: Constructor `val`/`var` warning now points to `rename-property` instead of recommending manual update or IDE refactoring.

**Changes**: New files: `RenamePropertyRewriter.kt`, `RenamePropertyConfig.kt`, `RenamePropertyFormatter.kt`, `RenamePropertyTask.kt`, `RenamePropertyWorkAction.kt`, `RenamePropertyMojo.kt`, `RenamePropertyRewriterTest.kt` (11 tests), `RenamePropertyFormatterTest.kt` (11 tests). Test fixture: `test-project/src/main/kotlin/com/example/variants/property/UserProfile.kt`. Modified: `TaskRegistry.kt`, `CodeNavigatorPlugin.kt`, `HelpText.kt`, `AgentHelpText.kt`, `RenameParamRewriter.kt` (warning updated), `TaskRegistryTest.kt`, `FileSizeScannerTest.kt` (count 23→24), `README.md`, `CHANGELOG.md`.

## ~~Class rename via `cnavMoveClass`~~ DONE

Added optional `new-name` parameter to `cnavMoveClass`, enabling class renaming (same package, different name), package move, or both in a single operation. Uses the same OpenRewrite `ChangeType` recipe — the new FQN is constructed from `newPackage` + `newName` (or original name if not specified). `new-package` is now optional when `new-name` is provided.

**Changes**: Modified: `MoveClassRewriter.kt` (added `newName` param, uses `targetName` for FQN and file path), `MoveClassConfig.kt` (added `newName` field, `new-package` now optional with validation), `MoveClassFormatter.kt` (operation description varies by move/rename/both), `TaskRegistry.kt` (added `RENAME_NEW_NAME` to `MOVE_CLASS_TASK` params, updated description), `MoveClassTask.kt`, `MoveClassWorkAction.kt`, `MoveClassMojo.kt` (pass `newName` through), `MoveClassRewriterTest.kt` (5 new tests), `MoveClassFormatterTest.kt` (3 new tests), `HelpText.kt`, `AgentHelpText.kt`, `README.md`, `CHANGELOG.md`, `plan.md`.

## ~~`cnavDuplicates` — source-level code duplication detection~~ DONE

**Value: high** | **Effort: high**

New source-level task detecting duplicate code blocks across the codebase using token-based matching (Rabin-Karp rolling hash). Zero external dependencies — uses a simple regex-based tokenizer (~150 lines) instead of ANTLR.

**Algorithm**: `SourceTokenizer` tokenizes Kotlin/Java source into normalized tokens (identifiers → `ID`, string literals → `STR`, etc.). `DuplicateDetector` uses Rabin-Karp rolling hash over token windows of `minTokens` size, extends matches to maximum length, then deduplicates overlapping groups using a coverage set (sort by length descending, skip if both locations already subsumed). Same-file duplicates require an overlap check.

**Parameters**: `-Pmin-tokens=<n>` (default 50), `-Ptop=<n>` (default 50), `-Pscope=all|prod|test`, `-Pformat=text|json|llm`.

**Performance**: ~20k LOC projects in <1 second. `scope=prod` cleanly separates production duplicates from test boilerplate.

**Implementation**:
- `SourceTokenizer.kt` — regex-based tokenizer for Kotlin/Java source
- `DuplicateDetector.kt` — Rabin-Karp rolling hash algorithm
- `DuplicateScanner.kt` — walks tagged source directories, tokenizes, detects
- `DuplicateConfig.kt` — config with minTokens, top, scope, format
- `DuplicateFormatter.kt` — text output formatter
- `DuplicatesTask.kt` (Gradle) — uses `taggedSourceDirectories()` for scope support
- `DuplicatesMojo.kt` (Maven) — full scope support
- `JsonFormatter.formatDuplicates()` and `LlmFormatter.formatDuplicates()`
- Tests: `SourceTokenizerTest` (12 tests), `DuplicateDetectorTest` (6 tests), `DuplicateConfigTest` (2 tests), `DuplicateFormatterTest` (2 tests)

## ~~Migrate usage examples into TaskDef definitions~~ DONE

**Value: medium** | **Effort: medium**

Migrated all hand-written usage examples from `HelpText.kt` into `TaskDef` definitions in `TaskRegistry.kt`, making examples part of the CLI definitions rather than duplicated across help text files.

**Design**: `UsageExample` data class with `List<Pair<ParamDef<*>, String?>>` params (not string names). `TaskDef.init` block validates at construction time that every `ParamDef` in examples is in the task's `params` list — impossible to reference a wrong/removed param. `TaskDef.renderExamples(tool: BuildTool)` generates formatted usage strings.

**Changes**: Added `UsageExample` data class, `examples` field on `TaskDef`, `renderExamples()` method, init validation. Populated examples for all 39 tasks. Updated `HelpText.kt` to generate `Usage:` lines via `examples(task)` helper. Removed unused `u()` helper. Tests: `UsageExampleTest` (7 tests including validation), `every task has at least one usage example` test, `help text usage examples are generated from TaskDef examples` test.
## ~~Stale class file warning and drop forced compilation~~ — DONE

`ClassFileStaleness.check()` compares newest source vs class mtime. Warns when stale, errors when no class files. Gradle: removed `dependsOn("classes")`/`dependsOn("testClasses")`. Maven: `checkStaleness()` added to all bytecode mojos (`@Execute` still forces compilation). AgentHelpText updated with staleness guidance.

### ~~`cnavMoveClass`: top-level Kotlin declarations not updated when moving a file with a named class~~ — DONE (v0.1.89)

**Value: high** | **Effort: medium**

From field test: moving `Metrics.kt` (which contains class `Metrics` plus top-level `val metricsRegistry`) correctly updated the class and its references, but the top-level `metricsRegistry` property was ignored. Files referencing `no.bankid.selvbetjening.metricsRegistry` still had stale imports after the move.

v0.1.65 added `*Kt` facade class support (when `-Pfrom` ends with `Kt`), but that only handles the case where the *entire file* is top-level declarations. When a file has both a named class and top-level declarations, moving the named class doesn't update the top-level declaration references.

- **Approach**: When moving a class from a file, also detect top-level declarations in the same file and update their `*Kt` facade references in consumer files. May need to run `ChangeType` for both the named class and the `*Kt` facade class in a single operation.

### ~~BUG: `cnavMoveClass`: rewrites imports of sibling classes in the same package~~ — DONE (v0.1.80)

**Value: high** | **Effort: medium**

From field test: moving `CssUtilsKt` from `no.mikill.greitt.css` to `no.mikill.greitt.util`. The file only contains a top-level `buildCssUrl` function. However, the preview also rewrites imports of `LightningCssTransformer` (a separate class in `no.mikill.greitt.css` that is NOT being moved) from `no.mikill.greitt.css.LightningCssTransformer` to `no.mikill.greitt.util.LightningCssTransformer`.

The move operation appears to treat all same-package imports as belonging to the moved class, rather than only rewriting references to the specific class being moved.

- **Approach**: When rewriting imports, only update imports that resolve to the class actually being moved (the `*Kt` facade or named class). Do not touch imports of other classes that happen to share the source package.

### ~~BUG: `cnavMoveClass` strips same-package imports from the MOVED file itself~~ — DONE (v0.1.83)

**Value: high** | **Effort: medium**

Reopens the "sibling import" bug (marked DONE in v0.1.80). The v0.1.80 fix stopped cnav from rewriting imports in *consumer* files, but the *source file being moved* still loses its sibling imports.

**Reproduction** (greitt project, v0.1.81-SNAPSHOT):
1. `PollsRepositoryFake` in package `no.mikill.greitt.polls` — implements `PollsRepository`, references `Poll` (same package, no explicit import needed)
2. Run: `./gradlew cnavMoveClass -Pfrom=no.mikill.greitt.polls.PollsRepositoryFake -Pto=no.mikill.greitt.testutil.PollsRepositoryFake`
3. Result: the moved file gets `package no.mikill.greitt.testutil` but NO imports added for `Poll`, `PollsRepository` (former same-package classes that now need explicit imports)

Same issue with `DevicesRepositoryFake` (lost `Device`, `DevicesRepository` imports) and `DeviceMother.kt` (lost `Device` import).

**Root cause**: When a class moves to a new package, references to former same-package classes were previously implicit (no import needed). After the move, they're in a different package and need explicit imports. `cnavMoveClass` doesn't add these.

**Expected behavior**: After moving a file to a new package, any unqualified references to classes that were in the OLD package (and are NOT moving with it) should get new import statements added to the moved file.

- **Approach**: After updating the package declaration in the moved file, scan it for unqualified type references. For each one that resolved to a class in the old package (detectable via the call graph / class index), add an explicit import for `oldPackage.ClassName`.

**Additional symptom**: In consumer files that already imported the moved class, cnav sometimes swaps the import path for OTHER imports from the same package. Example: `TestUtils.kt` had `import no.mikill.greitt.auth.Device` and `import no.mikill.greitt.auth.DeviceMotherKt` (via extension `verified`). After moving `DeviceMotherKt` to `testutil`, cnav rewrote it as `import no.mikill.greitt.testutil.Device` (wrong — Device didn't move) and `import no.mikill.greitt.auth.verified` (wrong direction). This is a variant of the original sibling bug in consumer files.

### ~~Suppress composition root / DI container suggestions~~ — DONE (v0.1.82)

**Value: high** | **Effort: low**

Composition roots are detected by name patterns (`*Context`, `*Module`, `*Application*`, `*Wiring*`, `*Dependencies*`) and by fan-out heuristic (edges to 5+ distinct packages). Both suppress the class from suggestions.

### ~~Suppress route handler → domain service suggestions~~ — DONE (v0.1.82)

**Value: medium** | **Effort: low**

Driver patterns (`*Routes*`, `*Controller*`, `*Endpoint*`, `*Handler*`) are suppressed from suggestions since they're expected to call domain services.

### ~~Confidence should consider callers, not just callees~~ — DONE (v0.1.82)

**Value: medium** | **Effort: medium**

`callersFromSamePackage` is now factored into the confidence denominator. Classes heavily used by their own package get lower confidence scores.

### ~~Account for self-package dependencies when suggesting moves~~ — DONE (v0.1.82)

**Value: high** | **Effort: low**

`edgesToOwn` counts outgoing edges to own-package classes. Combined with `callersFromSamePackage` and `isFeatureSliceMember` checks, classes that depend on their package's collaborators are no longer falsely suggested for moves.

### ~~Extract ConfidenceScorer from DeadCodeFinder~~ — DONE

Confidence scoring logic extracted to `ConfidenceScorer` object. `DeadCodeFinder` delegates to it. Independently testable with `ConfidenceScorerTest`.

### ~~Introduce query/config objects for complex finders~~ — DONE

`DeadCodeQuery` data class bundles 21 parameters. `DeadCodeFinder.find(query)` accepts it. Old overload preserved for backward compatibility.

### ~~Dead class count mismatch between `cnavMetrics` and `cnavDead`~~ — DONE

Extracted `DeadCodeOrchestrator` as single source of truth for dead code scanning. `MetricsTask`/`MetricsMojo` now use `DeadCodeConfig` + `DeadCodeOrchestrator` with same defaults as `DeadCodeTask`. Removed `excludeAnnotated` from `MetricsConfig`. Verified on realworld-springboot: metrics and dead both report 2 dead classes (was 8 vs 2).

### ~~`@ControllerAdvice` not recognized as Spring entry point~~ — DONE

Added `@RestControllerAdvice` to `FrameworkPresets.SPRING` (the actual missing annotation — `@ControllerAdvice` was already present).

### ~~OVER_ENGINEERED false positives for standard domain layer packages~~ — DONE

Nearby packages with MODEL/CONTRACT coupling are now classified as BALANCED (intentional layering) or TOLERABLE (if volatile). The OVER_ENGINEERED verdict is effectively retired. Help text updated.

### ~~`cnavWhyDepends` — dependency edge explanation~~ — DONE

Implemented class-level dependency edge explanation. `WhyDependsBuilder` filters `PackageDependency` list by from/to package, collapses inner classes to top-level via `topLevelClass()`, groups by (source, target) pair with counts. Registered as `why-depends` goal with `from-package` and `to-package` params. Gradle task, Maven mojo, help text, and agent help all updated.

### ~~`scope=prod` support for cycles and rings~~ — DONE (v0.1.86)

**Value: high** | **Effort: low**

`cnavRings -Pscope=prod` now filters test source set directories before building the dependency graph. `cnavCycles` already had scope support.

### ~~Lazy JAR scanning for external class classification~~ — DONE (v0.1.80)

**Value: medium** | **Effort: medium**

When `include-external=true`, `ClassTypeCollector` only scans project class directories. External library classes aren't in the `classTypeRegistry`, so `classifyTarget` returns null and they're counted as `unknown`. The current workaround (FIX 5) tracks these as `unknownCount` and defaults all-unknown pairs to CONTRACT strength.

- **Approach**: When a target class is not in the registry, lazily resolve its `.class` file from the runtime classpath JARs and classify it. Only scan the specific classes that appear in dependencies, not entire JARs.
- **Reuses**: Classpath resolution infrastructure from `cnavJar`.
- **Benefits**: Eliminates `unknownCount` entirely. Strength classifications for external dependencies become accurate instead of defaulting to CONTRACT.
- **Trade-off**: Adds JAR I/O during classification. Mitigate with a per-run cache of resolved classes.

### ~~Collapse bytecode noise in find-usages output~~ — DONE (v0.1.73)

Implemented in `UsageCollapser`. Collapsed output is the default; `-Praw=true` for bytecode-level detail.

### ~~Call-site summary mode for find-usages~~ — DONE (v0.1.73)

Merged into the collapsing step. Each line is flat and self-contained with combined kind tags.

### ~~Smart usages — auto-include interface implementations~~ — DONE (v0.1.73)

Implemented: when `cnavFindUsages -Ptype=X` targets an interface, `[impl]` lines are auto-included. `-Pinclude-impls` expands the search to include usages of each implementor.

### ~~Goal-oriented task discovery — `-Psection=refactor`~~ — DONE

Added `-Psection=refactor` to `cnavAgentHelp`. Groups tasks by intent: move/rename, explore before refactoring, find targets, verify after. Listed in the "More Detail" section of compact output.

### ~~Investigate `[prod]`/`[test]` misclassification on Maven projects~~ — DONE

Fixed: `getOrBuildTagged` now validates that cached source sets match requested tags. Previously, a non-tagged `getOrBuild` call (from MetricsMojo, PackageDepsMojo, etc.) would write the cache with all classes tagged as MAIN, and subsequent `getOrBuildTagged` calls would read the stale cache with wrong source set tags.

### ~~Default `cnavDead` to exclude test classes~~ — DONE

Default scope for `cnavDead` changed from ALL to PROD. Test classes are excluded by default. Output includes notice: "Test classes excluded. Use scope=all to include test classes." Applies to TEXT and LLM formats.

### ~~Filter non-source files from git analysis recommendations~~ — DONE

Non-source files (paths not starting with `src/`) no longer get recommendation annotations in coupling and hotspot output. Files still appear in results but without `←` advice meant for source code.

### ~~Add interpretation section to all analysis task output~~ ✅

**Value: high** | **Effort: medium** | **Done**

All analysis tasks now include a short interpretation section in their LLM output. Uses `withInterpretation()` helper that guards against empty output. Constants are `internal` for test access. Covers: hotspots, coupling, age, churn, volatility, rank, complexity, distance, strength, balance, cohesion, move-suggest, layer-check, cycles.

### ~~`cnavReport` — consolidated full analysis~~ — DONE (v0.1.86)

**Value: high** | **Effort: low**

Single task runs metrics, cycles, rings, move-suggest, cohesion, and dead code, producing sectioned output. Both Gradle and Maven.

### ~~`cnavAgentHelp -Ptopic=<name>` — philosophy-specific guidance~~ — DONE (v0.1.87)

**Value: high** | **Effort: low**

Added `-Ptopic=` parameter to `cnavAgentHelp` for on-demand philosophy guidance. Each topic explains: the philosophy (2-3 sentences), which cnav tasks support it, how to interpret results toward that goal, and concrete actions when violations are found.

Topics: `hexagonal` (rings, layer-check, strength, cycles), `tttd` (test-coupling), `fakes` (find-interfaces, test-coupling), `manual-di` (annotations, find-usages, rings, find-interfaces).

Design principle: a topic exists only if cnav has tasks that actively detect violations or measure progress. Skills teach portable philosophy; topics teach how to use cnav to enforce it on a specific codebase.

### ~~`cnavCohesion` — package cohesion scoring~~ — DONE (v0.1.79)

Measures ratio of internal class dependencies to total outgoing dependencies per package. Includes class count, verdict (COHESIVE/REVIEW/THIN_LAYER), `min-edges` threshold filter, and `CohesionScorer.detail()` for per-class breakdown. `DsmDependencyExtractor` enhanced with `includeSamePackage` parameter.

### ~~`cnavMoveSuggest` — misplaced class detection~~ — DONE (v0.1.79)

Identifies classes with more outgoing edges to another package than their own. Filters ubiquitous types via `max-fan-in` parameter. Sorted by confidence (ratio of target edges to total). Validated on ra-backend (48 suggestions).

### ~~Fix `type-hierarchy` to show full supertype/interface chain~~ — DONE (v0.1.80)

**Value: high** | **Effort: medium**

From evaluation on spring-petclinic and realworld-springboot: `type-hierarchy` only shows the class itself — not the inheritance chain. For `OwnerRepository` extending `JpaRepository`, the output is nearly empty. For framework types with deep hierarchies, the command is near-useless.

- **Expected**: Show the full chain of supertypes and interfaces, including library types resolved from classpath JARs.
- **Minimum**: Show supertypes/interfaces found in project bytecode. Extend with classpath scanning when that infrastructure is available.
- **Relates to**: Classpath/JAR scanning section — full hierarchy requires resolving library types.

### ~~Break `formatting` ↔ `navigation.dsm` cycle~~ — DONE

Orchestrators (`DistanceOrchestrator`, `StrengthOrchestrator`) now return result data classes instead of formatted strings. Formatting moved to `DsmOutputFormatter` in the `formatting` package. Callers (Gradle tasks / Maven mojos) use `DsmOutputFormatter.format(output, config.format)`. No production cycles remain.

### ~~Align test packages with production packages~~ — DONE

Moved ~95 test files from flat `navigation/` test package to sub-packages matching production structure (`annotation/`, `bytecode/`, `changedsince/`, `classinfo/`, `complexity/`, `context/`, `deadcode/`, `dsm/`, `metrics/`, `rank/`, `relations/callgraph/`, `relations/hierarchy/`, `relations/implementors/`, `stringconstant/`, `symbol/`, `types/`). Shared test utilities (`TestClassWriter`, `TestCallGraphBuilder`) remain in `navigation/` and are accessed via wildcard import.

### ~~Move `DsmOutputFormatter` to `navigation.dsm`~~ — REJECTED

Self-analysis (v0.1.83) suggested this move (confidence=1.0, own=0, target=13). However, `DsmOutputFormatter` depends on `JsonFormatter`, `LlmFormatter`, and `OutputWrapper` in its own `formatting` package. Moving it would create a cycle (`navigation.dsm` → `formatting`). This exposed a gap in `cnavMoveSuggest` — see "Account for self-package dependencies" above.

### ~~Break 6-package core cycle~~ — DONE

Resolved by the package restructure (commit `a3c3bf9`). Split `navigation.core` into `types/`, `bytecode/`, `cache/`. Moved `PatternEnhancer` to `types/`, `CacheFreshness` to `cache/`, `FrameworkPresets` to `types/`. No production cycles remain.

### ~~Extract shared orchestration from Gradle tasks and Maven mojos~~ — DONE

`StrengthOrchestrator` and `DistanceOrchestrator` extracted to core. Gradle tasks and Maven mojos are thin wrappers handling config parsing, directory resolution, and output routing.

### ~~Make `DsmDependencyExtractor.packageFilter` nullable~~ — DONE

Changed `packageFilter` from `PackageName` with `PackageName("")` magic value to `PackageName?` with null meaning "no filter." Updated all callers, config classes, and tests. No default values on parameters.

### ~~Unified diff output for refactoring tasks~~ — DONE (v0.1.88)

**Value: high** | **Effort: low**

Refactoring tasks' LLM format now produces standard unified diff (--- a/ +++ b/ @@ @@) with context lines instead of one-line summaries. Agents can read the exact edit plan from `-Ppreview -Pllm=true` and verify changes before applying. Uses LCS-based diff algorithm in `computeUnifiedDiff()`. Updated AgentHelpText to document the preview workflow for agents.

---

## ~~cnavTypeAffinity — type affinity analysis for move suggestions~~ — DONE (v0.1.102)

**Value: high** | **Effort: medium**

Analyzes a shared package (e.g. `domain/`) to find types that are exclusively owned by one feature package — candidates to move into that feature's package. Includes transitive port check, full ring recomputation for impact prediction, threshold parameter. Wired as Gradle task + Maven mojo with TaskOptionSyncTest enforcement.

---

## ~~cnavMoveFile produces no output~~ — DONE (v0.1.102)

**Value: low** | **Effort: low**

Fixed: error handling added so failures produce proper CNAV_BEGIN/CNAV_END output.

## ~~cnavRenameProperty inconsistent resolution~~ — DONE (v0.1.102)

**Value: medium** | **Effort: high**

Fixed: constructor params without `val/var` that initialize body properties are now renamed correctly (both the param name and the initializer reference).

## ~~cnavDead false positive on extension functions~~ — DONE (v0.1.102)

**Value: medium** | **Effort: low**

Fixed: `*Kt` facade classes excluded from class-level dead code detection entirely (`DeadCodeFinder.kt:203`). Method-level dead code on facades is still reported. `ConfidenceScorer` cleaned up.

## ~~cnavHotspots shows deleted files and splits history across renames~~ — DONE

**Value: medium** | **Effort: low**

Two bugs, same root cause: `HotspotBuilder` aggregated purely by `FileChange.path` string, and `GitLogParser.resolveRenamePath` discarded the old side of a rename entirely, keeping only the new path. This meant (1) a file's revision history got split into two separate hotspot entries — one under the old path (pre-rename commits) and one under the new path — instead of being summed, and (2) files deleted at any point in history (including old rename names, once no longer summed) stayed in the output forever, since there was no check against what still exists on disk.

Fixed: `FileChange` gained a `renamedFrom: String?` field (`GitLogParser` now captures both sides of a rename instead of only the new path). `HotspotBuilder.build` builds a rename-chain map from all `renamedFrom` edges in the commit list (handles transitive chains, A→B→C, via forward traversal with a cycle guard) and redirects every historical path through it before aggregating — so pre- and post-rename revisions land in one entry. Added an optional `projectDir: File?` param; when provided, a file is only included if it still exists on disk at its final path — `HotspotTask`/`HotspotsMojo` pass their project directory. Existing tests unaffected (no default value change breaks old callers); new tests cover direct rename merge, transitive chains, same-name-but-unrelated-file non-merging, and existence filtering with/without `projectDir`. Verified live against this repo's own real git history (via a throwaway `git worktree`, removed after): previously-deleted files (`LayerChecker.kt`, `LayerCheckConfig.kt`, `ExtractPropertiesTest.kt`) no longer appear even at `--top=1000`; this repo has no actual renames in its history to exercise the merge path live, but the parser/builder unit tests cover it directly.

## ~~Cycle actionability — fix suggestions, edge ranking, and direction clarity~~ — DONE (v0.1.103)

**Value: high** | **Effort: high**

Implemented:
1. **Edge direction + counts**: Per-edge ref counts shown in both TEXT and LLM formats.
2. **Edge ranking — "which edge to break first"**: `CycleBreakAnalyzer` computes break-score (edge removal splits/shrinks SCC) and ranks by weight. Top 3 weakest links shown.
3. **Fix suggestions**: Weakest links section tells user which edges to target.

Remaining (deferred to future iteration):
- Ring degeneration guidance (identify easiest-to-extract package in giant cycles)
- Test-only edge flagging in cycle output

## ~~Test-source separation — exclude test edges from structural analysis~~ — DONE (already implemented)

**Value: high** | **Effort: low**

All structural tasks (cnavDsm, cnavCycles, cnavBalance, cnavRings) already support `--scope=prod` which filters class directories by source set. Verified on greitt: 90→0 ring violations, 1→0 cycles when excluding test sources. No code change needed.

## ~~DSM what-if simulation (`cnavSimulateMove`)~~ — DONE (v0.1.103)

**Value: medium** | **Effort: medium**

Implemented. Predicts cycle impact of moving a class to a different package without modifying code:
- `cnavSimulateMove --type=Cache --to-package=no.example.ra --scope=prod`
- Mutates dependency graph in memory, re-runs cycle detection, diffs before/after
- Shows removed/added cycles. Validated on bass-ra-backend.

## ~~cnavExecutePlan — execute a plan file~~ — DONE

**Value: high** | **Effort: medium**

Dedicated task (`cnavExecutePlan --plan-file=plan.json`) that reads a plan JSON and applies each move step sequentially using `MoveClassRewriter`. Class names are resolved to FQCNs upfront via the compiled class index (no fuzzy matching in rewriting logic). Supports `--preview` to dry-run.

Plan format: `[{"action":"move","type":"com.example.api.Dto","to":"com.example.service"}]`

## ~~Class-level ring detection for `cnavRings`~~ — DONE (v0.1.103-SNAPSHOT)

**Value: high** | **Effort: high**

Implemented `--mode=emergent` which classifies each class into a ring by import shape (framework imports = adapter, SCC collapse for cycles, longest-path for layering). Shows mixed-ring package summaries. Ring detection is by dependency shape only, never naming conventions.

**Remaining follow-ups** (not blocking, separate items):
- Structural mode improvement (detect ring subpackages by shape)
- Intra-package ring violations (domain class depending on adapter within same package)
- Actionable guidance (suggest port extraction)

## ~~cnavMovePackage — batch move all classes in a package~~ — DONE (v0.1.105-SNAPSHOT)

**Value: high** | **Effort: low**

Implemented `cnavMovePackage --from-package=<pkg> --to-package=<pkg>` (Gradle + Maven). Scans project classes, filters by source package, then iterates `MoveClassWorkAction` for each class. Supports `--preview`. Reuses `ExecutePlanFormatter` for consistent batch output.

**Known limitation**: Shares the same OpenRewrite worker metaspace issue as `cnavExecutePlan` — packages with 5+ classes may hit `OutOfMemoryError: Metaspace` with default JVM settings. Workaround: increase `org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=1g` in `gradle.properties`.

## ~~`cnavDead` baseline diff — confirm cleanup was complete~~ — DONE (already implemented)

**Value: low** | **Effort: low**

Stale — already fully built. `--baseline=<path>` parameter exists on `cnavDead` (Gradle: `DeadCodeTask`), reads a previous `cnavDead` JSON output via `DeadCodeBaselineDiff.parseBaseline`/`.compare`, and renders removed/remaining/new dead code via `DeadCodeBaselineDiffFormatter` for all three output formats. Covered by `DeadCodeBaselineDiffTest`.

## ~~Dead code: flag methods called only from test scope~~ — DONE (already implemented)

**Value: medium** | **Effort: low**

Already fully implemented — this plan item was stale. `DeadCodeReason.TEST_ONLY` (vs `NO_REFERENCES`), `ConfidenceScorer` downgrading test-only items to `MEDIUM` confidence, and `--scope=prod`/`--scope=test`/`--scope=all` filtering are all live and covered by `DeadCodeFinderTest` (`scope PROD filters out TEST_ONLY items`, `scope TEST filters to only TEST_ONLY items`, etc.). Landed back in the `testOnly` filter work (see `bcd6f42`, CHANGELOG "dead code reason tagging").

## ~~`cnavClassMetrics` — per-class cohesion + CK metrics~~ — DONE

**Value: high** | **Effort: medium**

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

Implemented in `no.f12.codenavigator.navigation.classmetrics`: `FieldAccessAnalyzer` (per-method field-access via bytecode, excludes `<init>`/`<clinit>`/synthetic/property-accessor methods using the existing `KotlinMethodFilter`), `CohesionGraphBuilder` (pure TCC/LCC via union-find, no bytecode), `MethodComplexityAnalyzer` (WMC via conditional-jump/switch-case/catch-block counting), `TypeCouplingAnalyzer` (CBO via ASM `Type` on field/method/local descriptors, excluding self/JDK/`javax`/Kotlin-stdlib), and `ClassMetricsAnalyzer` (orchestrates all four plus a project-wide superclass map for DIT — one light metadata pass to build the map, one full bytecode pass per eligible class; interfaces and `$`-generated classes excluded from output, matching the existing `ClassComplexityAnalyzer` convention). CBO deliberately covers only field/method/local **signatures**, not call targets — kept distinct from the existing fan-out metrics in `cnavComplexity`/`cnavDsm`. New task wired end-to-end: `ClassMetricsConfig`/`ClassMetricsOrchestrator`/`ClassMetricsFormatter` (core), `ClassMetricsTask`/`ClassMetricsMojo` (thin adapters, same shared-orchestrator shape as the other analysis tasks), registered in `TaskRegistry` (new `ParamType.DOUBLE` for `--min-tcc`) and `HelpText`. Verified live via an ephemeral Gradle composite build: TEXT/JSON/LLM all correct, `--min-tcc` filtering works. New tests: `CohesionGraphBuilderTest`, `FieldAccessAnalyzerTest`, `MethodComplexityAnalyzerTest`, `TypeCouplingAnalyzerTest`, `ClassMetricsAnalyzerTest`, `ClassMetricsFormatterTest`, `ClassMetricsOrchestratorTest`, plus `JsonFormatterTest`/`LlmFormatterTest` additions — all using hand-crafted ASM fixtures (`ClassWriter`) for exact bytecode-shape control, following the `DsmDependencyExtractorTest`/`TestClassWriter` precedent rather than relying on real-kotlinc-output guessing.

## ~~Per-task help with usage on error~~ — DONE

**Value: medium** | **Effort: low**

All refactoring tasks (Gradle + Maven) now catch `IllegalArgumentException` on config parse and show `usageHint()` + `renderExamples()` instead of a raw stack trace.

## ~~Refactoring result LLM hints for follow-up actions~~ — DONE (v0.1.105-SNAPSHOT)

**Value: medium** | **Effort: low**

Implemented `RefactoringHints` helper in core. Each formatter's LLM output now includes task-specific follow-up suggestions after applied operations (non-preview). Move/execute-plan suggest structural verification (`cnavPackageDeps`, `cnavRings`, `cnavCycles`). Rename/delete/change-signature suggest `cnavFindUsages` for verification.

## ~~Refactoring task discoverability~~ — DONE

**Value: low** | **Effort: low**

Covered: `generateCompact()` has "When Refactoring" block (line 150) + "Common Refactoring Tasks" section + explicit `"I'm about to rename/move/delete → move-class/rename-method --preview"` hint in the Exploring section.

## ~~`cnavFindCallees`: hide library internals by default~~ — DONE (already implemented)

**Value: medium** | **Effort: medium**

Stale — already resolved. `PROJECTONLY` (`--project-only`) defaults to `"true"` (`ParamDef` default value, description literally says "Hide JDK/stdlib/library classes (default: on)") and is included in both `FIND_CALLEES` and `FIND_CALLERS`' param lists with no override, so `CallGraphConfig.projectOnly` is `true` unless a caller explicitly passes `--project-only=false`. Verified by reading `CallGraphConfig.parse` → `TaskRegistry.PROJECTONLY.parseFrom(properties)` and the filter application in `buildFilter()`. No code change needed.

## ~~`cnavFindCallees` callee explosion~~ — DONE

**Value: medium** | **Effort: medium**

`CallTreeBuilder` expanded ALL polymorphic implementors per interface call site — a call to a widely-implemented interface (e.g. `Repository.save()` with 20 implementations) produced 20+ sibling nodes at one depth, drowning out everything else. Went with the "collapse dispatch groups into 'N implementors' node" option from the three listed here (skipped the other two — a global max-children cap or lower default depth would've thrown away real information non-selectively).

Implementation: `resolveInterfaceDispatchByCallee` (in `CallTreeBuilder`, CALLEES direction only) now groups implementor MethodRefs **per interface call site** instead of flattening them all into one set — this matters because a single method can call multiple different interfaces (e.g. both `Repository.save()` and `Validator.validate()`), and collapsing needs to stay scoped to each one independently rather than producing one ambiguous count across all of them. Each group is capped independently at `maxImplementors` (sorted by `qualifiedName` for determinism), and the interface method's own `CallTreeNode` — already present in the tree from the direct call edge — gets a new `collapsedImplementorCount: Int = 0` field set to the overflow count. CALLERS direction is untouched (that expansion is bounded by distinct-interfaces-implemented, not implementor count, so it was never the reported problem).

New `--max-implementors` param (default 5, matching `CallTreeBuilder.DEFAULT_MAX_IMPLEMENTORS`) added to `cnavFindCallees` only (a no-op for `cnavFindCallers`, so not exposed there to avoid a misleading CLI surface). All three formatters render a `(+N more implementors, use --max-implementors to see all)` suffix (singular/plural aware) via a shared `CallTreeFormatter.collapsedImplementorsTag` helper; JSON adds an additive `collapsedImplementorCount` field (omitted when zero, so existing consumers are unaffected). `cnavContext` (which also builds call trees via `CallTreeBuilder.build` through `ContextOrchestrator`) gets the same collapsing automatically via the default, with no new CLI flag — out of scope for this pass, but the fix isn't silently absent there.

Verified live: an 8-implementor interface correctly shows 5 expanded + `Repository.save() (+3 more implementors...)` by default; `--max-implementors=2`/`--max-implementors=100` both work as expected; JSON includes `"collapsedImplementorCount":3` on the interface node only. New tests: `CallTreeBuilderTest` (per-callsite capping, deterministic selection, CALLERS-direction no-op, default-matches-constant), `CalleeTreeFormatterTest`/`JsonFormatterTest`/`LlmFormatterTest` additions for the rendered suffix/field.

## ~~`cnavMoveSuggest` + `--plan-file` support~~ — DONE

**Value: medium** | **Effort: low**

Implemented for both Gradle and Maven. `MoveSuggestTask`/`MoveSuggestMojo` now extract via `PackageHealthExtractor`, mutate the dependency list and project class set through `PlanMutator`, then feed the mutated `PackageHealthExtraction` into `MoveSuggestOrchestrator.fromExtraction`.

Important nuance found during implementation: `PlanMutator.apply()` used to unconditionally drop edges that land in the same package after a simulated move — correct for cycle/DSM/ring analysis, but wrong for move-suggest (extracted with `includeSamePackage=true`), since those intra-package edges are exactly what scores gravity at the destination. Added a `dropSamePackageEdges` parameter (default `true`, preserving existing callers) and pass `false` from move-suggest.

Also found: `cnavCycles`/`cnavRings`/`cnavDsm`/`cnavMetrics` Maven mojos accept `--plan-file` as a CLI property but never actually apply the mutation to the dependency graph — silent no-op on the Maven side only (Gradle tasks apply it correctly via `CodeNavigatorTask.applyPlan`). See new item below.

Still open: `cnavSuggestStructure` and `cnavCohesion` — both consume the same dependency list and could benefit from the same plan-simulation wiring, using the same `dropSamePackageEdges=false` approach.

## ~~Fix Maven `--plan-file` no-op on cnavCycles/cnavRings/cnavDsm/cnavMetrics~~ — DONE

**Value: medium** | **Effort: low**

Fixed. Added shared `loadPlanSteps()`/`applyPlanFile()` helpers to `MavenSupport.kt` (mirroring Gradle's `CodeNavigatorTask.applyPlan`, since Maven mojos have no shared base task) and wired them into `CyclesMojo`, `DsmMojo`, `MetricsMojo` (simple `applyPlanFile(dependencies, planFile, log)` before matrix/cycle building) and `RingsMojo` (both `--mode=package` via `applyPlanFile`, and `--mode=emergent` via explicit `PlanMutator.apply`/`applyToClassSet` on both the project and external dependency extractions, since emergent mode needs the mutated class set for its project/external split). `MoveSuggestMojo` refactored to reuse `loadPlanSteps()` instead of parsing inline.

## ~~`cnavMoveSuggest`: structural supertype gravity~~ — DONE (v0.1.106-SNAPSHOT)

**Value: high** | **Effort: low**

Detects `implements`/`extends` relationships as additional dependency gravity in `MoveSuggester`. These edges bypass the ubiquitous-type filter, so fakes (which primarily depend on interfaces that many classes use) are now correctly suggested for co-location with their interface's package.

Implementation: `DsmDependencyExtractor` extracts structural supertypes via a dedicated ASM pass. `MoveSuggester.suggest()` accepts them as a separate parameter and applies weight 3 per structural edge, immune to `maxFanIn` filtering.

## ~~Maven: `--jar` support for Mojos~~ — DONE

**Value: medium** | **Effort: low**

~~Add `@Parameter(property = "jar")` to `ListClassesMojo`, `FindClassMojo`, `ClassDetailMojo`, `FindSymbolMojo`.~~

Already implemented: all four mojos have `@Parameter(property = "jar")` and full jar branch logic.

## ~~CI fail-on-violation mode~~ — DONE

**Value: high** | **Effort: low**

Implemented for `cnavCycles` (`--fail-on-violation=true --max-cycles=0`) and `cnavRings` (`--fail-on-violation=true --max-violations=0`, both `--mode=emergent` and `--mode=package`). `cnavLayerCheck` was removed in v0.1.97 (superseded by `cnavRings`), so it's not part of this. `cnavCohesion` excluded — it produces a ranked score list, not a violation count, so "fail on violation" doesn't map cleanly onto it.

Gradle throws `GradleException`, Maven throws `MojoFailureException`, after printing the normal output.

## ~~Restore formatting-layer boundary — outer layers pass result objects, formatters emit output~~ — DONE (named offenders fixed)

**Value: high** | **Effort: medium**

Fixed both concrete offenders named below (v0.1.112). `CyclesOrchestrator`/`RingsOrchestrator` now carry raw `TestInvolvement.Counts?` (not pre-rendered text) on their output; `CyclesFormatter`/`JsonFormatter.formatCycles`/`LlmFormatter.formatCycles` and `EmergentRingFormatter.format` each accept `testInvolvement` and render the notice themselves, per format. `RingFormatter.format` already had an unused `configNotice: String?` hook — wired `RingsTask`/`RingsMojo` to pass `RingFormatter.PACKAGE_MODE_NOTICE` through it instead of prefixing the string themselves. `CyclesTask`/`CyclesMojo`/`RingsTask`/`RingsMojo` no longer concatenate onto formatter output or branch per `OutputFormat` outside the formatter — Rings' pointless `when (format) { TEXT,DIFF -> output; JSON -> output; LLM -> output }` echo (three identical branches) was replaced with a direct `OutputWrapper.wrap(output, format)`. `JsonFormatter.formatCycles` now emits a real structured `"testInvolvement":{"testInvolved":N,"total":M}` field — the JSON-hints item's first concrete instance. Verified live (Gradle composite build): TEXT/LLM notice text unchanged, JSON field present and correctly shaped, `PACKAGE_MODE_NOTICE` correctly positioned after the section header. New tests: `CyclesFormatterTest`, `JsonFormatterTest`, `LlmFormatterTest`.

**Found but deliberately not fixed — a bigger, separate gap**: `cnavRings --format=json` isn't real JSON at all, for either mode. `RingFormatter.format()` and `EmergentRingFormatter.format()` are the *only* renderers for rings, and both always produce prose-shaped text regardless of `format` (the `format` param only toggles whether LLM-only action hints are appended) — there's no `JsonFormatter.formatRings`/`formatEmergentRings` equivalent at all. So today, `cnavRings --format=json` returns the same prose text as TEXT, just wrapped in `---CNAV_BEGIN---`/`---CNAV_END---` markers with no structural change. This is bigger than a boundary violation — it's a missing capability — so it's logged as a new item below rather than folded into this one.

## ~~`cnavRings` has no real JSON output format~~ — DONE

**Value: medium** | **Effort: medium**

Implemented `JsonFormatter.formatRings(assignment, ringNames, configNotice)` (package mode) and `JsonFormatter.formatEmergentRings(result, ringNames, hasHints, testInvolvement)` (emergent mode). Package mode emits `rings` (package/ring/ringName/isCompositionRoot), `violations` (filtered to drop composition-root edges, matching TEXT), and `configNotice`. Emergent mode emits `classRings`, `mixedRingPackages` (only packages with `isMixedRing`, matching TEXT's scope), `violations`, `hintsApplied`, and `testInvolvement`. `RingsTask`/`RingsMojo` branch on `OutputFormat.JSON` to call these instead of `RingFormatter`/`EmergentRingFormatter`; TEXT/DIFF/LLM unchanged (LLM already varies via the existing `format` param on those formatters). New tests in `JsonFormatterTest`. Verified live via an ephemeral Gradle composite build (temporary scratch project, not committed): both `--mode=package --format=json` and `--mode=emergent --format=json` produce correctly-shaped JSON; `--format` omitted still produces the original prose output unchanged.

## ~~Maven empty-result paths bypass OutputWrapper~~ — DONE (v0.1.112)

**Value: high** | **Effort: low**

Found systemically while auditing for other pattern deviations: 13 Maven mojos used a raw `println("...")` on their no-results path instead of `OutputWrapper.emptyResult(config.format, ...)` — confirmed every Gradle counterpart did it correctly. Silently broke `--format=json`/`--format=llm` (no `CNAV_BEGIN`/`CNAV_END` markers, non-JSON body) whenever the result set was empty, Maven-only. Fixed in: `AuthorAnalysisMojo`, `ChangedSinceMojo` (2 spots), `ClassDetailMojo`, `CodeAgeMojo`, `ChurnMojo`, `ContextMojo`, `ComplexityMojo`, `DeadCodeMojo`, `FindInterfaceImplsMojo`, `HotspotsMojo`, `RankMojo`, `PackageDepsMojo`, `StringConstantMojo`, `TypeHierarchyMojo`. Verified live via a local `mvn install` + scratch project: `find-interfaces`/`complexity` with no matches now correctly emit `{"results":[],"hints":[]}` wrapped in markers under `--format=json`.

## ~~`CallTreeTaskSupport`/`CallTreeMojoSupport` duplicate the find-callers/find-callees pipeline~~ — DONE (v0.1.112)

**Value: medium** | **Effort: medium**

Extracted `CallTreeOrchestrator` (core, `navigation.relations.callgraph`) — call graph build, skipped-file reporting, method matching, interface-registry lookup, annotation scan, tree building, and `classHint` computation all now live once. `CallTreeTaskSupport` (Gradle) and `CallTreeMojoSupport` (Maven) are now thin adapters: each keeps its own config-parse error wrapping (`GradleException` vs `MojoFailureException`), its own `enhanceProperties`/`applyConfigDefaults` call site (unchanged — the asymmetry there was cosmetic, not a bug, so left as-is rather than force identical call-site ordering), and its own logger vs `println`, but the pipeline itself is one function both call. Also gave `CallTreeMojoSupport` the `taskDef.deprecations(properties)` warning it was missing (Gradle had it, Maven silently didn't warn on deprecated `--method` usage).

Verified live for both build tools (Gradle composite build + local `mvn install` scratch project): a real caller/callee match and a no-match (`--format=json`) both produce identical, correct output through the shared orchestrator. New test: `CallTreeOrchestratorTest`.

## ~~`ContextTask`/`ContextMojo` duplicate their pipeline — no shared orchestrator~~ — DONE (v0.1.112)

**Value: medium** | **Effort: medium**

Extracted `ContextOrchestrator` (core, `navigation.context`) — class detail scan, call-graph build, interface registry, annotation scan, and the per-class caller/callee/implementor assembly all now live once. Both skipped-file reports (class scan and call-graph build report independently, as before) surface as a `List<String>` on the output rather than being logged inline mid-pipeline, so the Task/Mojo just iterates and warns. `ContextTask`/`ContextMojo` are now thin adapters: config-parse error wrapping (`GradleException` vs `MojoFailureException`) and the Maven-only `taggedDirs.isEmpty()` pre-check stay build-tool-specific, everything else is one shared function.

Verified live for both build tools (Gradle composite build + local `mvn install` scratch project): a real match (class detail + caller/callee tree) and a `--format=json` no-match both produce correct, matching output. New test: `ContextOrchestratorTest`.

## ~~Reduce Gradle/Maven duplication via orchestrator extraction~~ — DONE (analysis tasks)

**Value: medium** | **Effort: high**

Every analysis-task Gradle/Maven pair duplicated orchestration. Extracted `CyclesOrchestrator`, `DsmOrchestrator`, `RingsOrchestrator`, `MetricsOrchestrator`, `VolatilityOrchestrator`, `CouplingOrchestrator`, and `TypeAffinityOrchestrator` (all in core) — every Task/Mojo pair for cycles, dsm, rings, metrics, volatility, coupling, and type-affinity now calls the same function instead of hand-rolling the extraction→(plan-mutation→)analysis pipeline twice. This is what prevented the `--plan-file` no-op bug (see above) from being possible in the first place — there's only one code path to get right. Balance, Strength (IntegrationStrength), Distance (PackageDistance), and SuggestStructure already had orchestrators before this work started.

Two more real divergences found and fixed while doing this: Maven's `TypeAffinityMojo` never called `SkippedFileReporter.report(...)` (skipped-file warnings silently dropped, Maven-only); Maven's `ChangeCouplingMojo` used a raw `println("No coupling found.")` on the empty-result path instead of `OutputWrapper.emptyResult(config.format, ...)` (ignored `--format`, Maven-only).

Notable finding while unifying `RingsOrchestrator`'s emergent mode: Gradle did one combined extraction (`includeExternal=true`) and split project/external deps by class-set membership; Maven did two separate extractions. Proved these produce identical results (same underlying bytecode scan, just filtered differently), so unified on Gradle's single-scan approach — also removes a redundant bytecode walk that Maven was doing.

**Deliberately out of scope**: the refactoring-operation tasks (Rename*, Move*, ChangeSignature, SafeDelete, ExecutePlan) are a different shape. Their actual rewrite logic (`RenameMethodRewriter`, `RenameLocationFinder`, etc.) is *already* shared in core and called identically from both sides — Gradle just wraps it in `WorkerExecutor` classloader isolation (required because `kotlin-compiler-embeddable` conflicts with Gradle's own Kotlin runtime on the same classloader), while Maven calls it directly since it doesn't need that isolation. There's no duplicated pipeline to unify there, just an unavoidable execution-shell difference — extracting a shared "orchestrator" wouldn't add the same divergence-proofing value it did for the analysis tasks above.

## ~~Break `formatting` ↔ `navigation.relations` cycle~~ — DONE (already implemented)

**Value: medium** | **Effort: low**

Stale — already resolved. `UsageFormatterTest` lives at `src/test/kotlin/no/f12/codenavigator/formatting/UsageFormatterTest.kt`; only one copy exists in the whole repo.

## ~~Move misplaced root-package test classes~~ — DONE (already implemented)

**Value: low** | **Effort: low**

Stale — already resolved. `ClassFileStalenessTest` and `TaskRegistryTest` now live under `no.f12.codenavigator.registry` (matching their production classes); `TaskDefTest` no longer exists as a separate file. Only 3 files remain in the root `no.f12.codenavigator` test package — `AgentHelpTextTest`, `ConfigHelpTextTest`, `HelpTextTest` — and those are legitimately root-level (they test the plugin's own root-level help generators, not misplaced).

## ~~Embedded Kotlin Compiler Frontend~~ — DONE (v0.1.90)

Two-phase architecture: ASM location finding → PSI editing in isolated classloader. `kotlin-compiler-embeddable:2.0.21`. Remaining: BindingContext not yet used.

---

## ~~cnavAnnotations: methods=true as default for common annotations~~ — DONE

**Value: low** | **Effort: low**

Original ask was narrow ("auto-enable `--methods=true` when results are empty"), but investigation found a bigger issue: the `--methods` flag added zero value — `AnnotationExtractor.extract()` already scanned method-level annotations unconditionally in the same bytecode pass regardless of the flag, so it was purely a display filter on data already extracted, not a performance knob. The output already visually distinguished class vs. method matches (indentation). So instead of patching the confusing default, replaced it: added FIELD-level annotation scanning (new `visitField` extraction, previously absent entirely — no `@Inject`/`@Autowired`-on-field support existed), and replaced the boolean `--methods` flag with `--target=class,method,field` (comma-separated, `AnnotationTarget` enum), defaulting to **all three targets searched by default**. `--methods` kept as a deprecated no-op (warns, points to `--target`) rather than removed outright, per existing deprecation convention (`INCLUDETEST`/`ROOT_PACKAGE`). Output now labels each match explicitly (`method foo [@Test]` / `field bar [@Inject]`) in TEXT/LLM; JSON adds a `fields` array alongside `methods`. `noResultsHints` now hints toward broadening `--target` instead of the old "pass --methods=true" message. New tests across `AnnotationExtractorTest`, `AnnotationQueryBuilderTest`, `AnnotationQueryFormatterTest`, `AnnotationQueryConfigTest`, `JsonFormatterTest`/`LlmFormatterTest`. Verified live: `--pattern=Test` (a method-only annotation) now matches by default with no flag; `--target=class` correctly excludes it with a hint; deprecated `--methods=true` still works with a warning; JSON includes the new `fields` array.

---

## ~~cnavSafeDelete crashes with JSON parse error~~ — DONE

**Value: high** | **Effort: low**

Was parked as "not reproducible" — reopened and root-caused. Live-testing surfaced two real bugs in the shared `OutputWrapper.emptyResult()` (used by ~30 tasks' "no results" path, not just SafeDelete):

1. **Silent information loss**: the JSON/LLM branch discarded `textMessage` entirely, emitting only `{"results":[],"hints":[...]}`. For tasks with a static "No X found." message this is harmless, but for tasks whose message carries dynamic, task-specific information — `SafeDeleteTask`'s `result.reason` (e.g. "Cannot delete: 1 usage(s) found"), `SimulateMoveTask`'s "Class 'X' not found", `RenamePropertyMojo`'s exception message — a JSON/LLM consumer got zero indication of *why* there were no results. For SafeDelete specifically, the entire rich `SafeDeleteResult` (including the `usages` list naming every caller) was computed successfully and then thrown away in favor of this generic empty wrapper.
2. **The actual "JSON parse error" root cause**: the hint-escaping used a naive `.replace("\"", "\\\"")` — quotes only, no backslash escaping. Any message or hint containing a backslash (a Windows path, or — very plausibly what the original bug hit — a regex pattern like `Foo\.Bar` echoed back into a "No X matching 'pattern' found." message) produced syntactically invalid JSON. Verified live: `cnavFindSymbol --pattern='Foo\.Bar'` with the old code would have embedded a bare `\.` (not a valid JSON escape) in the output.

Fixed by adding a `"message"` field (using the already-shared `escapeJson`/`jsonStringArray` from `formatting/JsonBuilder.kt`, same package) instead of the ad hoc quote-only escape. 4 existing `OutputWrapperTest` tests updated for the new shape (results/message/hints), plus a new regression test asserting backslashes and quotes both escape correctly. Live-verified: SafeDelete's JSON output now shows `"message":"Cannot delete: 1 usage(s) found"`; a backslash-containing pattern now round-trips through valid JSON (`\\.` not `\.`).

---

## ~~Review implementations for spread logic + shared lookup extraction~~ — DONE

**Value: medium** | **Effort: high**

**Spread logic**: Several tasks span multiple concerns. Principle: orchestrator calls single-purpose tasks.
- ~~`RenameMethodRewriter`/`RenameMethodEditor`: location finding + PSI editing separation~~ — **already done, verified by reading the code.** `RenameMethodRewriter.rename()` is a thin orchestrator: calls `RenameLocationFinder` (bytecode-based call-site/implementor finding) then delegates to `RenameMethodEditor` (PSI editing dispatch to Kotlin/Java rewriters). No further split needed.
- ~~`MoveClassRewriter`: are import updating, content extraction, file writing reusable?~~ — **already done, verified by reading the code.** `MoveFileRewriter.move()` already delegates to `MoveClassRewriter.move(..., allowMultiClass = true)` rather than duplicating its logic — the reuse the bullet was asking about already exists.
- ~~Formatter classes: some contain query logic belonging in builders~~ — **partially done.** Audited the 50 `*Formatter.kt` files in `src/core/kotlin` for `.filter`/`.sortedBy` combined with actual domain decisions (not presentation ordering). Found and fixed the two clearest cases, both genuine same-predicate duplication across multiple format functions:
  - **`RingFormatter.format()` and `.formatJson()`** both independently recomputed `violations.filter { sourcePackage !in compositionRoots && targetPackage !in compositionRoots }` (byte-identical). Moved to `RingAssignment.reportableViolations` — a computed property next to the `violations`/`compositionRoots` fields it depends on. Turned out `RingDetector.detect()` already excludes composition-root-touching edges from `violations` at the source (line 91's `if (source in compositionRoots || target in compositionRoots) continue`), so this wasn't a live bug — `reportableViolations` and raw `violations` are always equal today. Still worth consolidating: it documents the invariant in one place instead of two copy-pasted predicates, and protects against a future `RingDetector` change silently breaking it. Also updated `RingsTask`/`RingsMojo`'s `--fail-on-violations` count to use the same property (was already consistent, now explicitly and verifiably so). New regression test proves the property's own filtering logic in isolation (constructs a `RingAssignment` directly with a violation touching a composition root) rather than relying only on `RingDetector`'s current behavior.
  - **`TestCouplingFormatter`**'s three format functions (`formatText`/`formatDetailText`/`formatLlm`) each independently recomputed `violations.filter { verdictFor(it.testClass) != ADAPTER_TEST }` (byte-identical, 3x). Moved to `TestCouplingResult.actionableViolations`, next to the existing `verdictFor`/`confidenceFor` methods it composes with.
  - Considered but left as-is (weaker case — selecting an already-precomputed flag for a specific display section, not deriving a new domain judgment): `EmergentRingFormatter`'s `isMixedRing` filter, `CyclesFormatter`'s `breaksycle` filter, `ExecutePlanFormatter`'s empty-step filter.

**Shared lookup: done.** Class resolution and method finding were duplicated across ChangeSignatureRewriter, PsiRenamePropertyRewriter, SafeDeleteRewriter, PsiRenameParamRewriter, KotlinRenameMethodRewriter, RenameMethodEditor. Extracted to two new files in `navigation.refactor`:
- `PsiRefactorSupport.kt` — `createDisposableKotlinEnvironment`/`withKotlinPsiFactory` (the identical Disposer+CompilerConfiguration+KotlinCoreEnvironment+KtPsiFactory boilerplate, previously copy-pasted verbatim in 6 files) and `applyEdits` (identical text-edit application, previously duplicated in 4 files).
- `KotlinFqnSupport.kt` — `buildClassFqn`/`matchesFqn`/`fileReferencesClass`, previously duplicated near-identically in `PsiRenamePropertyRewriter` and `KotlinRenameMethodRewriter` (one redundant wrapper — `KotlinRenameMethodRewriter.fileReferencesClass` — was eliminated entirely since it just re-did what `isImportedOrSamePackage` already covered).

`withKotlinPsiFactory` is `inline` so callers keep their existing early-`return` style (non-local return) without restructuring control flow. The two cached-lifecycle rewriters (`KotlinRenameMethodRewriter`, `JavaRenameMethodRewriter`, which persist their environment across a whole batch of files and dispose explicitly) use the raw `createDisposableKotlinEnvironment` instead of the scoped helper. `JavaRenameMethodRewriter`'s own FQN/import-matching logic operates on IntelliJ Java PSI types (`PsiClass`/`PsiJavaFile`), not Kotlin PSI, so it wasn't unified — left as a follow-up if Java PSI support grows. All ~30 pre-existing refactor tests passed unmodified (behavior-preserving); live-verified via `cnavRenameProperty`/`cnavRenameMethod --preview` in a scratch project.

---

## ~~Split JsonFormatter and LlmFormatter per-feature~~ — DONE (split + dispatcher removed)

**Value: medium** | **Effort: high** | Source: internal(v0.1.83)

`JsonFormatter` (364 outgoing, 77 types, fanIn=261) and `LlmFormatter` were the highest-complexity classes. Split into per-feature `formatJson`/`formatLlm` methods living next to each feature's existing TEXT `format`, across 5 passes (DSM/Cycles/Rings pilot → usages → classinfo/relations → dsm-adjacent → analysis-group → the last 9 functions incl. call-tree rendering). Every pass used the same delegate pattern: signatures on `JsonFormatter`/`LlmFormatter` stayed unchanged and just called the new per-feature method, so none of the ~40 existing Task/Mojo/test call sites needed to change during the split, and every pass was verified behavior-preserving by the full existing test suite passing unmodified. Shared JSON-building primitives (`formatting/JsonBuilder.kt`) and the LLM `withInterpretation` helper (`formatting/LlmFormatting.kt`) let per-feature formatters reuse them without duplicating string-escaping logic. Along the way: deleted a handful of exact-duplicate private helpers found only because the code was being moved anyway (`LlmFormatter`'s own copy of `appendDisambiguationHint`, already in `UsageFormatter`).

Once every function was a one-liner, the dispatcher layer itself was removed: `JsonFormatter.kt`/`LlmFormatter.kt` deleted entirely, and all ~65 call sites (30 Gradle Tasks, 29 Maven Mojos, 2 core files, 5 test files) repointed at the per-feature formatters' `formatJson`/`formatLlm` directly via a mechanical regex-based rewrite script. Verified with `compileKotlin`, `compileTestKotlin`, the full test suite, and `mvnw compile`, all passing unmodified.

---

## ~~Test suite health~~ — DONE (all 3)

**Value: medium** | **Effort: medium**

- ~~Cache KotlinParser in rewriter tests~~ — **superseded by a real fix: batch `cnavMovePackage` into one parse.** Caching the built `KotlinParser` object gave zero measured benefit (6.5s → 6.7-6.9s, within noise) — the real cost is inside `.parse()` itself, not parser construction. The actual root cause: `cnavMovePackage`/`cnavExecutePlan` submitted one `MoveClassWorkAction` (and one full-project `parseKotlinSources` re-parse) per class being moved, sequentially, with no explicit disposal between calls — same root shape as the documented metaspace `OutOfMemoryError` on 5+ class batches. Fixed via `MoveClassRewriter.moveBatch()`: parses once, runs the "simple" moves (single class per file, standard filename) through one `CompositeRecipe` instead of N independent `ChangeType` runs. Wired into `cnavMovePackage` (`MovePackageTask`/`MovePackageMojo`, plus a new `MoveBatchWorkAction` on the Gradle side) — one `WorkAction` submission for the whole package move instead of one per class. `cnavExecutePlan` intentionally left unbatched (arbitrary/possibly cross-package moves without the "all independent siblings" guarantee a uniform package move has).

  Two real correctness issues surfaced along the way, both fixed before shipping:
  - **`CompositeRecipe` corrupts a file's package declaration when two of its constituent `ChangeType` recipes both touch that file** — verified with a standalone prototype (two classes in one file, both renamed: package line came out as `package foo.<error>`) before writing any production code. So genuine multi-class files (2+ *requested* classes declared in one file) are detected and routed through the existing unbatched per-class path instead of the composite recipe; Kt facades too. Only the common case (each class in its own file) goes through `CompositeRecipe`.
  - **Sibling-import bug, caught live in a scratch project, not by unit tests**: when class A implicitly references co-located classes B and C (same package, no import needed) and A/B/C all move together in one batch, the existing `addMissingImportsForSiblings` logic (unchanged, reused as-is) added `import oldPackage.B` — pointing at a package that no longer contains B once B's own move applied. The unbatched sequential code never hit this because each move re-parsed from disk, so a later step's consumer-import-rewrite pass would "catch" and fix an earlier step's stale import; batching removed that safety net. Fixed by having `moveBatch` check, per sibling, whether it's *also* moving in the same batch — same target package → skip the import entirely (stays implicit); different target package → import from *its own* new package, not the old one. Two regression tests (`MoveClassRewriterBatchTest.kt`) lock this in, plus 9 other batch tests (independent classes, cross-referencing classes, multi-class-file fallback, Kt-facade fallback, not-found handling, disk writes, preview mode). Live-verified end-to-end with a real scratch Gradle project — moved 3 cross-referencing classes via `cnavMovePackage`, confirmed the *destination* project actually compiles afterward, not just that the tool ran without error.
- ~~Add `FieldExtractor` tests~~ — **done.** 6 tests added (`FieldExtractorTest.kt`) covering field extraction, `INSTANCE` field exclusion, empty classes, multi-class/multi-directory scans, and missing directories. Coverage 0% → 99% instructions (112/113; only an anonymous ASM `ClassVisitor` still shows a jacoco-lambda-attribution artifact at 0%, despite 100% line coverage).
- ~~Cover `LlmFormatter`/`JsonFormatter` uncovered branches~~ — **done.** jacoco HTML report showed 9 fully-untested functions in `JsonFormatter` (`formatTypeHierarchy`, `formatDuplicates`, `formatVolatility`, `formatAge`, `formatAuthors`, `formatChangedSince`, `formatBalance`, `formatCohesion`, `formatMoveSuggestions`) and the same 5 in `LlmFormatter` minus the analysis-only ones. Added ~17 new tests across both files. `JsonFormatter`: 78%→99% instruction coverage, 100% line coverage. `LlmFormatter`: 78%→91% instruction coverage, 96% line coverage. Remaining 0%-covered entries in the jacoco CSV (`formatInterfaces`, `formatPackageDeps`, `formatAnnotations` nested lambdas) are a known jacoco/Kotlin lambda-attribution quirk — the same lines show 100% line coverage and are exercised by existing tests with real assertion data, so not further pursued.

---

## ~~Fix DANGER balance: root package → callgraph/implementors~~ — DONE

**Value: medium** | **Effort: low** | Source: internal(v0.1.83)

Confirmed via a live `cnavBalance` self-check: published the plugin to `mavenLocal`, pointed a throwaway Gradle project's `sourceSets.main.output.classesDirs` directly at this repo's own `build/classes/kotlin/main` (skipping compilation entirely — no need for a second copy of the source), and ran it from a subdirectory inside the repo so `git log` resolved against code-navigator's own history. Neither of the originally reported DANGER edges (`no.f12.codenavigator → navigation.relations.callgraph`, `→ navigation.relations.implementors`) appears in current output — resolved as a side effect of moving misplaced root-package test classes and shrinking the root package down to 3 help-text generators. One *new* DANGER edge surfaced instead (`no.f12.codenavigator → navigation.types`, caused by `AgentHelpText.kt` importing `FrameworkPresets`) — tracked separately in `plan.md` as its own low-priority item rather than folded into this one, since it's a different edge with a different (lower-severity) cause.

---

## ~~`cnavConverge` — composite architectural signal (intersect + risk scoring)~~ — DONE

**Value: high** | **Effort: high** | Source: field-test(bass-ra)

New composite task, `cnavConverge`/`cnav:converge`, with two modes.

**Intersect mode** (default): cross-references three independent signals per package pair — cycle membership (`CycleDetector`), ring/layer violations (`RingDetector.reportableViolations`), and git change-coupling (`ChangeCouplingBuilder`) — and classifies pairs where at least one signal fires:
- **ACT NOW**: structural problem (cycle *or* ring violation) *and* real change coupling — both an independent-source and a semantically distinct signal agree.
- **LATENT**: structural problem with no supporting coupling signal yet.
- **MISSING ABSTRACTION**: no structural problem detected, but the packages change together in git history — often a missing shared interface/contract.

Resolved the "cycles/rings are package-based, coupling is path-based" scope-alignment gap flagged in the original plan item: `SourcePathIndex` (`navigation/converge/SourcePathIndex.kt`) resolves a git-relative file path to the project class it contains via each class's bytecode-derived `ClassInfo.reconstructedSourcePath` (package-dir/filename) — a git path resolves if it *ends with* that suffix, since the git path additionally carries whatever source root (`src/main/kotlin`, `src/main/java`, ...) the reconstructed path doesn't know about. Indexed by filename first so lookup doesn't scan every class per coupling pair. Cycle detection deliberately bypasses `CyclesOrchestrator`'s default (`dsm-depth=2`, truncated) package grouping — calls `DsmMatrixBuilder.build(deps, PackageName(""), Int.MAX_VALUE)` directly for full, untruncated package names, matching `RingDetector`'s own untruncated granularity exactly (both derived straight from `PackageDependency.sourcePackage/targetPackage`). Unresolvable coupling pairs (non-source files, resources, docs) are counted and reported, not silently dropped.

**Risk mode** (`--mode=risk`): ranks classes by `risk = change_frequency × complexity × coupling_degree`, combining `HotspotBuilder` (revisions), `ClassComplexityAnalyzer` (fanOut+fanIn), and `ChangeCouplingBuilder` (max degree involving the file) joined via the same `ClassInfo.reconstructedSourcePath`-suffix matching against git paths. Missing coupling data defaults the multiplier to 1 (neutral) rather than 0, so a class can still rank on frequency×complexity alone with no coupling history.

`ConvergeOrchestrator.run` takes already-fetched `List<GitCommit>` rather than calling `GitLogRunner` itself (Task/Mojo fetch commits and pass them in) — decouples the cycle/ring/coupling join logic from git subprocess I/O, so the composite algorithm is unit-testable with synthetic commits and `TestClassWriter`-built classes instead of a real git repo. 26 new tests across `ConvergeConfigTest`, `SourcePathIndexTest`, `ConvergeOrchestratorTest`, `ConvergeFormatterTest`; full existing suite (2,721 tests) passes, including two pre-existing tests fixed along the way (`TaskRegistryTest`'s hardcoded task-count assertions, `HelpTextTest`'s over-broad "strength section" text-boundary check that happened to span into the new task's section).

Live-verified against this repo itself (same throwaway-scratch self-check pattern as the DANGER-balance item above): intersect mode ran end-to-end and produced real LATENT edges (this repo's own package cycles/ring violations) plus a correct `unresolvedCouplingPairs` count for git-tracked non-source files; JSON output validated well-formed. Risk mode's empty result in that same scratch run was traced to the scratch harness's nested `projectDir` not matching git's root (breaks `HotspotBuilder`'s on-disk existence check, which is relative to `projectDir`) — a throwaway-verification-environment artifact, not a product bug; risk-mode logic itself is covered by orchestrator unit tests with a correctly-aligned `projectDir`.

**Field-validated on kotlin-htmx** (a real consumer project, not the throwaway scratch harness above): both modes ran clean and produced correct, actionable results. Intersect found a genuine LATENT cycle (`no.mikill.kotlin_htmx` ↔ `.context`, no coupling yet) and three real MISSING ABSTRACTION pairs (55-71% change coupling with no structural dependency, e.g. `htmx` and `selection.pages` changing together with no declared contract). Risk correctly ranked `HtmxCheckboxDemoPage` top (high churn + high complexity + 71% coupling with `selection.pages`), followed by the route-wiring class and a high-churn shared utility — exactly the kind of class a risk ranking should surface. One design gap noted from this run: intersect and risk are fully separate pipelines with no shared output, so there's no single view combining "high risk" with "in a cyclically-coupled package pair" — tracked as its own follow-up, see [[cnavConverge: unify intersect and risk mode output]].

**Field-validated on ra-backend** (`--scope=prod`), a third project, alongside kotlin-htmx: intersect correctly reported zero ACT NOW/LATENT (no cycles or ring violations in prod code) and 12 clean MISSING ABSTRACTION findings — `ktor.routes` ↔ `ktor.routes.v1` (85% coupling, routes and versioned subpackages always move together with no declared contract), `di` ↔ `ktor.routes.v1` (53%, composition root entangled with route evolution), `audit` ↔ `jwt` (47%, a non-obvious correlation with no structural link). Risk correctly surfaced `EventSenderImpl` top (34 changes, complexity=89, 47% coupling — high complexity plus persistent churn), `AppDependencies` second (40 changes, complexity=37, 80% coupling — the composition root, expected as the highest-churn coordination point), and `RAClient` fourth (74 changes, the highest raw change count in the project, low complexity — the external-system adapter absorbing every protocol change). Three real consumer projects now validated (bass-ra, kotlin-htmx, ra-backend) with correct, actionable output on both modes and no reported issues.

**Follow-up from ra-backend's `--scope=all` run**: the default scope produced dramatically noisier output than `--scope=prod` (55 findings vs. 12) — 6 ACT NOW + 21 LATENT that were almost entirely test-introduced (`SystemTestContext`/e2e wiring pulling every feature package through one shared hub, which `RingDetector`'s own composition-root auto-detection didn't fully catch in that all-scope run). Addressed with a **guide-don't-hide** design rather than a silent default change:

1. **`cnavConverge` keeps the conventional `--scope=all` default** (like every other scoped task — an early attempt to special-case it to `--scope=prod` was reverted after discussion: only `cnavDead` overrides the scope default, and silently pre-filtering hides the test-coupling signal some users do want). Instead, when the intersect result is large (≥ `NOISE_ADVISORY_THRESHOLD` = 20 pairs), the orchestrator emits a **constructive advisory** appended to the output (all three formats — text `⚠` line, JSON `advisory` field, LLM `advisory:` line) that names the likely cause (manually-wired DI / shared test infrastructure) and points at the two levers, adapting to what the user hasn't already tried: suggests `--scope=prod` only when still including test sources, `--exclude-packages` only when no exclusion is set, and stays silent when both are already pulled (the count is then genuinely high, not obviously noise). The persistence hint gives a matching `cnav-config.json` `defaults` example so an agent knows it can make the choice sticky. This directs the LLM toward the fix without deciding for it.

2. **Reuses the shared `--exclude=<regex>` param** as a manual backstop for hub packages the composition-root heuristic doesn't catch (or that aren't composition roots at all, like test-only wiring). This param went through two revisions: first shipped as a distinct `--exclude-packages`, then folded back into the shared `EXCLUDE` once it was confirmed that `EXCLUDE`'s two existing consumers (`cnavDead`, `cnavTestCoupling`) already share identical **substring** semantics — both use `containsMatchIn` on the fully-qualified class name (`cnavDead`'s `item.className.matches(exclude)` calls the project's `ClassName.matches` helper, which is `containsMatchIn`, not the stdlib full-string `Regex.matches`; an intermediate version of this note wrongly claimed a full-string-vs-substring split). With semantics uniform, the distinct name added surface area for no real benefit; consolidating means one `--exclude` regex behaves the same everywhere and a single `cnav-config.json` `defaults.exclude` can serve any of the three tasks. `--exclude` filters converge's dependency graph *before* cycle/ring detection runs (not just the final edge list) — a hub package's edges need to be gone before SCC computation for the fix to actually work, not just hidden from display afterward. Applies uniformly in both modes: intersect mode drops any `PackageDependency`/resolved coupling pair touching an excluded package (matching on the package name), risk mode drops any matching class from the ranking (matching on the class FQN).

New tests: `TestClassWriter`-based orchestrator tests for the exclude-drops-hub-edges behavior, direct `advisoryFor` unit tests for the threshold and adaptive wording (below-threshold null, all-scope suggests both levers, prod-scope suggests only exclude, both-pulled stays null), formatter tests for advisory rendering in all three formats, plus `ConvergeConfigTest` scope-default cases. Full suite (2,725 tests) and Maven compile both green. Live-verified against this repo (`--scope=all` fires the 57-findings advisory suggesting both levers; `--scope=prod` correctly drops the `--scope=prod` suggestion and shows the `exclude-packages` config example instead).

Alongside this, also completed [[cnavTestCoupling: remaining improvements]] — see below.

---

## ~~`cnavTestCoupling`: remaining improvements~~ — DONE

**Value: medium** | **Effort: low** | Source: field-test(greitt+terms-and-conditions)

- ~~Concise "all clear" output~~ — **done** (earlier session). `TestCouplingFormatter` returns a one-line "No TTTD violations found..." message instead of ~15 lines of guidance when there are no violations.
- ~~DAO test threshold~~ — **done**. `TestCouplingBuilder`'s adapter-test detection (`isAdapterTest`'s majority-of-calls check) was diluted by assertion-library call noise (`kotlin.test.*`, `org.assertj.*`, `org.junit.*Assertions`, `org.hamcrest.*`, `io.kotest.*`, `strikt.*`), so a DAO/adapter test chaining several `assertThat(...)` calls per method could fall below the 50% port-call threshold and get misclassified as MIXED instead of ADAPTER_TEST. Fixed by excluding known assertion-library call targets from `TestCouplingBuilder`'s call-target tracking entirely, at the same point `<init>`/`<clinit>` calls are already excluded — affects `isAdapterTest`, `verdictFor`, and `confidenceFor` uniformly since they all read from the same tracked call-target/non-port-call data. New regression test reproduces the diluted-ratio scenario and confirms it still classifies as ADAPTER_TEST.

---

## ~~`cnavReport` has no real JSON output format~~ — DONE

**Value: low** | **Effort: medium** | Source: internal

`ReportTask`/`ReportMojo` echoed the same rendered markdown string across all three `when (format)` branches (`JSON -> output` same as `TEXT -> output`), so `--format=json` returned a markdown blob, not structured JSON. Fixed by the structural-aggregation approach the parked item called for: extracted a shared **`ReportOrchestrator`** (`navigation/report/`) that runs every sub-analysis and returns a typed **`ReportData`** holding each one's *domain object* (`MetricsResult`, `List<CycleDetail>`, `RingAssignment`, `MoveSuggestionResult?`, `CohesionResult?`, `List<DeadCode>`), plus a **`ReportFormatter`** that renders that data three ways: TEXT reproduces the original sectioned markdown byte-for-byte, JSON composes each sub-feature's own `formatJson` under a named key (`{"metrics":{...},"cycles":{...},"rings":{...},"moveSuggestions":{...},"cohesion":{...},"deadCode":[...]}` — `moveSuggestions`/`cohesion` omitted when absent), and LLM sections each sub-feature's `formatLlm` under a heading.

Also resolved the duplication the plan flagged: `ReportTask` and `ReportMojo` were ~200-line near-verbatim copies of the same pipeline (the one composite task with no shared orchestrator). Both now just resolve build-tool-specific inputs (test class dirs — Gradle `SourceSetContainer` vs Maven `testOutputDirectory` — and git commits) and delegate to `ReportOrchestrator`/`ReportFormatter`, dropping to ~95 lines each. `ReportOrchestrator.run` takes pre-fetched `List<GitCommit>` rather than calling `GitLogRunner` itself (same pattern as `ConvergeOrchestrator`), keeping the aggregation build-tool-neutral and unit-testable.

New `ReportOrchestratorTest` (5 tests, `TestClassWriter`-based) covers end-to-end pipeline population, the markdown TEXT sections, structured JSON (parsed with `parseJsonObject` and asserted to contain the per-section keys and *not* the markdown headers), the absent-section omission, and the LLM headings. Full suite (2,730 tests) and Maven compile green. Live-verified against this repo: `--format=json` now parses as a real object with six structured keys; TEXT output unchanged (6 markdown sections); LLM sectioned.

---

## ~~Migrate MoveClassRewriter from OpenRewrite to PSI~~ — DONE (OpenRewrite removed entirely)

**Value: medium** | **Effort: high** | Source: internal

`MoveClassRewriter`/`MoveFileRewriter` were the last code using OpenRewrite (`org.openrewrite.java.ChangeType` + the `KotlinParser` parse layer in `RewriterSupport`). Migrated the whole thing to the `kotlin-compiler-embeddable`/PSI approach the other rewriters use and **removed OpenRewrite from the project entirely** (build.gradle.kts, pom.xml, and the Gradle plugin's classpath injection).

The plan's stated blocker — "needs `BindingContext` for type-inferred references with no literal token" — turned out to be theoretical: nothing `MoveClassRewriter` actually does (or any of its 46 tests exercise) needs type attribution. Its real work is textual/structural — package/import/FQN rewriting keyed off literal `import old.pkg.Name` strings. Investigation confirmed both `EXCLUDE` consumers and all move behavior are literal-token based (the batch post-processing greps `before.contains(oldImport)`).

**What replaced `ChangeType`:** a new `KotlinTypeReferenceRewriter.retargetAcrossSources(sources, oldFqcn, newFqcn)` that parses each candidate file with `KtPsiFactory` and emits offset-based `TextEdit`s for (a) import directives (`import old.pkg.Foo` / `... as Bar`, alias preserved), (b) fully-qualified references in code (`val x: old.pkg.Foo`, `old.pkg.Foo()`), and — when the simple name also changes (rename) — (c) the class/interface/object *declaration* and every unqualified simple-name reference, gated to files that actually reference the type (`fileReferencesClass`, same package or importing it) so an unrelated same-named type is left alone. Overlapping edits are deduped (a simple-name edit contained in an already-retargeted FQN is dropped) so the two passes never collide. Reused the existing `PsiRefactorSupport` (`withKotlinPsiFactory`/`applyEdits`) and `KotlinFqnSupport` helpers.

**Two capabilities `ChangeType` did implicitly that the PSI retarget had to add back explicitly** (found by the 4 rename tests failing, then fixed):
1. **Declaring file's package line** — the retarget rewrites references, not the package declaration, so each path now applies the textual `package old` → `package new` to the moved file (the multi-class/facade paths already did this via `replacePackageImports`).
2. **Import into former same-package consumers** — when a type moves out of its package, files that *stayed* and referenced it unqualified now need an import of its new location added. New `importMovedTypeInFormerSamePackage` reuses `addMissingImportsForSiblings` (treating the moved type as a sibling now living in the new package).

The parse layer (`RewriterSupport`) now reads `.kt` files straight from disk into `SourceFileContent(path, content)` — no compiler frontend needed for the file set as a whole (only the targeted per-file retarget parses, on demand). `printAll()`/`resolveOriginalPath` kept as thin compat shims so the ~40 surrounding textual call sites didn't change.

Batching (`cnavMovePackage`'s `moveBatch`) previously composed all moves into one `CompositeRecipe`; now it threads a mutable working-copy map through the moves in order so a file touched by two moves sees both — same result, and it sidesteps the documented `CompositeRecipe` package-line-corruption hazard entirely (offset-based `TextEdit`s can't corrupt a shared package line the way two composed `ChangeType`s could). The `KotlinIsoVisitor` nested-lambda traversal limit is gone for free (`collectDescendantsOfType` reaches any depth).

**Dependency cleanup:** removed `rewrite-core`/`rewrite-java`/`rewrite-java-21`/`rewrite-kotlin` from both build files. Discovered the six refactor tasks that injected `openRewriteClasspath` were all actually PSI-based — they'd been getting `kotlin-compiler-embeddable` *transitively* via `rewrite-kotlin`'s dependency. Repointed all of them (`MoveClass`, `MovePackage`, `MoveFile`, `ExecutePlan`, `RenameParam`, `RenameProperty`) to the existing `psiClasspath`/`psiConfig` injection, so every refactor task now uses one uniform Kotlin-compiler-frontend classpath. Deleted the obsolete `OpenRewriteApiExplorationTest`.

**Verification:** all 46 move/rename/batch tests green, full suite (2,730+) green, `mvnw compile` green, `openrewrite` occurrences in compileClasspath = 0. Live end-to-end against a real scratch Gradle project (published locally): `cnavMoveClass` across packages (moved file + same-package consumer gets import added + cross-package consumer import rewritten), `cnavMovePackage` batch, and a rename (declaration + all references incl. constructor call) — the destination project **compiled** after each operation, not just "the tool ran without error."

---

## ~~Make the move/rename rewriter type-safe (semantic resolution)~~ — DONE (K1)

**Value: medium** | **Effort: high** | Source: follow-up to the OpenRewrite→PSI migration

After the PSI migration, the rename pass matched references heuristically (same-package-or-imports gating), which can false-positive on a shadowing local or a same-named type/member. Added **K1 `BindingContext` resolution** to confirm each unqualified simple-name reference actually resolves to the moved type before renaming it.

**Why K1, not K2:** the K2 Analysis API is the modern, ergonomic path, but JetBrains does **not** publish its standalone artifacts for external consumption — confirmed empirically (`analysis-api-standalone-base` 404s at every version in Maven Central, intellij-dependencies, kotlin-ide-plugin-dependencies, and the Kotlin bootstrap Space repo; the `-for-ide` POM pins implementation artifacts that aren't published) and confirmed by JetBrains directly in [[KT-56203]] ("The API is not yet ready to be used externally as we do not have any compatibility guarantees there. Thus it's not published."). K1 is the legacy frontend but it ships in the `kotlin-compiler-embeddable` we already depend on — **zero new dependencies** — and gives identical resolution correctness for this task. K2 can be revisited if/when KT-61419 ships a public standalone release.

**How it works:** `KotlinReferenceResolver.tryBuild(sourceRoots, classpath)` builds one `KotlinCoreEnvironment` over the whole source set + classpath, runs `TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration` for a `BindingContext`, and resolves a reference via `bindingContext[REFERENCE_TARGET, ref]` (unwrapping a constructor to its class). It returns null on any failure, so `KotlinTypeReferenceRewriter` **falls back to the heuristic** when no classpath is available or analysis fails — resolution is a best-effort precision layer, not a hard requirement. Applied only on the rename pass (imports/FQN refs are exact and never need it) and only in the single-move paths (their source text still matches disk); the in-memory batch stays heuristic, which is sound since a package move rarely renames.

Two dedicated tests lock in the value: one proves resolution correctly leaves a same-named enum entry (`Kind.Widget`) alone while renaming the class; the paired test proves the heuristic (empty classpath) *would* wrongly rename it — so resolution is demonstrably load-bearing, not decorative. A **declaration-matching bug the live e2e caught** (a `KtEnumEntry` is a `KtClassOrObject`, so matching declarations by simple name renamed the enum entry declaration too, dangling the reference) was fixed by matching the exact declared FQN (`decl.fqName == oldFqcn`).

**Non-Kotlin warning:** since the rewriter only edits `.kt` files, a `.java`/`.groovy`/`.scala` consumer of a moved Kotlin class is left with a stale reference. `MoveClassResult` now carries a warning naming those files ("N non-Kotlin source file(s) may reference 'X' but were NOT updated … review manually"), so an agent knows to fix them independently instead of silently shipping broken cross-language code.

**Verification:** full suite (2,744) green, `mvnw compile` green, and live end-to-end against a mixed Kotlin+Java scratch project — a rename correctly renamed the type + constructor, left the same-named enum entry (declaration *and* reference) untouched, the destination Kotlin **compiled**, and the Java consumer triggered the non-Kotlin warning.

---

## ~~`cnavRenameMethod` misses interface declaration when targeting an `Impl` class~~ — DONE

**Value: high** | **Effort: medium** | Source: field-test(ra-backend, v0.1.113)

Renaming a method on an `Impl` (e.g. `RAClientImpl.getInfo`) renamed the impl + call sites but left the interface (`RAClient`) and sibling implementors (`RAClientFake`) with the old name, so the impl then `overrides nothing` — a compile error. `findImplementors(className)` only walks *down* (classes implementing the target), which finds nothing for a leaf impl.

Fixed with a bytecode `RenameLocationFinder.findOverrideFamily(classesRoots, className, methodName)`: scans all class files once to build supertype/subtype maps + which classes declare the method, walks *up* from the target to the declaring interface(s)/superclass(es), then *down* from those roots to every implementor/subclass that declares it — the complete override family. Those FQNs are added to `implementorFqns`, and the editor already renames declarations for any class in that set. Chose bytecode (consistent with the existing `findImplementors`/call-site scanning) over the plan's suggested PSI `overriddenFunctions`, since the finder is already a bytecode pass.

**Gradle/Maven duplication caught by live e2e:** the fix in `RenameMethodRewriter.rename` made the unit test pass, but the live run still failed — the Gradle `RenameMethodTask` computes `implementorFqns` itself (bytecode scan runs main-side, PSI edit in the worker) and bypasses `RenameMethodRewriter.rename`. Had to add `findOverrideFamily` to the task's computation too. Maven's mojo calls `RenameMethodRewriter.rename` directly, so it was already covered.

New test (`renaming a method on an Impl also renames the interface and sibling implementors`, over a new interface+impl+fake fixture) plus live e2e: renaming via the impl renamed the interface, impl, sibling fake, and caller, and the destination project **compiled** (no "overrides nothing"). Full suite green.
