# Plan — Completed

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
./gradlew cnavRenameParam -Ptarget-class=com.example.UserService -Pmethod=findUsers -Pparam=limit -PnewName=maxResults -Papply=true
```

**Implementation**:
- `RenameParamConfig` — config data class with `parse()` companion, validates all 4 required params (target-class, method, param, newName) plus optional `apply` flag. 8 tests.
- `RenameParamRewriter` — OpenRewrite-based rewriter using `KotlinParser` and `RenameParamVisitor` (`KotlinIsoVisitor<ExecutionContext>`). Handles three rename dimensions: parameter declarations (`visitMethodDeclaration`), body references including string templates (`visitIdentifier`), and named arguments at call sites (`visitMethodInvocation`). Scoped by `inTargetClass`/`inTargetMethod` flags. Returns `RenameResult` with `List<RenameChange>` diffs. `apply` parameter controls whether changes are written to disk. 5 tests (all with compile-before/compile-after verification).
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
