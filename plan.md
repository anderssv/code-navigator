# Plan

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

## 6. Architecture violation detection in cnavDeps (High value, ambitious)

Allow defining allowed/forbidden dependency rules (e.g., "services must not depend on ra") and flag violations. This would turn cnavDeps into an architecture fitness function. Could be configured via a simple DSL:

```kotlin
codeNavigator {
    rules {
        "services" mustNotDependOn "ra"
        "domain" mustNotDependOn "ktor"
    }
}
```

## ~~7. JSON/machine-readable output format (Medium value)~~ DONE

All tasks now support `-Pformat=json` for structured JSON output. Hand-rolled JSON formatter (`JsonFormatter.kt`) with no external dependencies — uses `jsonArray`, `jsonObject`, `jsonValue` helpers and a `JsonRaw` value class for pre-rendered content. Covers all 8 data tasks: cnavListClasses, cnavFindClass, cnavFindSymbol, cnavClass, cnavCallers, cnavCallees, cnavInterfaces, cnavDeps. Also added `cnavAgentHelp` task with workflow guidance, task reference, and performance tips for AI coding agents.

## ~~8. cnavClass show interfaces implemented (Low effort)~~ DONE

Already implemented. `ClassDetailExtractor` extracts interfaces from bytecode and `ClassDetailFormatter` outputs "Implements: ..." when interfaces are present.

## 9. Write JSON output to file instead of stdout (Medium value)

When `-Pformat=json` is used, write the JSON to a file (e.g., under `build/cnav/`) and print the file path to stdout. Agents and scripts can then read the file directly instead of parsing Gradle's stdout, which mixes task output with Gradle lifecycle noise (e.g., `> Task :cnavCallers`, configuration cache messages). This makes the JSON output reliable regardless of Gradle's verbosity settings.

## 10. Maven plugin support (High value)

Create a Maven plugin equivalent in the same repo. The core bytecode analysis logic (scanning, call graph building, formatting) is build-tool-agnostic — it operates on `.class` file directories. Factor the core into a shared module and wire it into both a Gradle plugin and a Maven plugin (Mojo). This would make code-navigator available to the large Maven user base without duplicating the analysis code.

## 11. Git log infrastructure — foundation for behavioral analysis (High value)

Inspired by Code Maat / CodeScene. All git-history-based analyses share a common parsing layer:

- `GitLogRunner` — executes `git log --all --numstat --date=short --pretty=format:'--%h--%ad--%aN' --no-renames --after=<date>` via `ProcessBuilder`. Uses the Code Maat `git2` format for tolerant, fast parsing.
- `GitLogParser` — parses raw git log output into `List<GitCommit>` data class: hash, date, author, list of `FileChange(added: Int, deleted: Int, path: String)`.
- Shared parameter: `-Pafter=YYYY-MM-DD` to limit the temporal window (default: 1 year). Matches Code Maat's recommendation to avoid old data confounding analysis.
- No new dependencies — just `ProcessBuilder` + string parsing.
- These tasks do NOT depend on `classes` — they read git history, not bytecode, so they work even before compilation.

Architecture follows the existing three-layer pattern:
1. **Parsing**: `GitLogParser` (shared) produces `List<GitCommit>`
2. **Resolution**: One builder per analysis produces its result data structure
3. **Formatting**: Text/JSON/LLM formatters per analysis, using existing `OutputFormat` and `OutputWrapper`

Each builder is independently testable with synthetic `GitCommit` lists (no git repo needed in tests), mirroring how existing tests use synthetic `CallGraph` instances.

## 12. `cnavHotspots` — change frequency analysis (High value)

Inspired by Code Maat's `revisions` analysis. Ranks files by how often they change. The most-changed files are where development effort concentrates — if they also have structural problems (visible via `cnavDeps`, `cnavCallers`), they're priority refactoring targets.

- `HotspotBuilder.build(commits) -> List<Hotspot(file, revisions, totalChurn)>`
- Parameters: `-Pafter=YYYY-MM-DD`, `-Pmin-revs=N` (default 1), `-Ptop=N` (default 50)
- Text table (File | Revisions | Churn), JSON, LLM formatters
- Sorted by revision count descending

## 13. `cnavCoupling` — change coupling / logical coupling (High value)

Inspired by Code Maat's `coupling` analysis. Finds files that change together in the same commits — implicit dependencies invisible in call graphs. Complements structural `cnavDeps` with behavioral coupling data.

- `ChangeCouplingBuilder.build(commits, minSharedRevs, minCoupling, maxChangesetSize) -> List<CoupledPair(entity, coupled, degree, sharedRevs, avgRevs)>`
- Degree = (shared commits / avg individual commits) × 100
- Large changeset filtering (default: skip commits touching >30 files) — these are usually automated refactorings/renames and create misleading coupling signals. This is a Code Maat best practice.
- Parameters: `-Pafter`, `-Pmin-shared-revs=N` (default 5), `-Pmin-coupling=N` (default 30%), `-Pmax-changeset-size=N` (default 30), `-Ppattern=<regex>` to filter to specific files
- Text table (Entity | Coupled | Degree% | Shared Revs), JSON, LLM formatters

## 14. `cnavAge` — code age analysis (Medium value)

Inspired by Code Maat's `age` analysis. Measures time since last modification per file. Stable old code is good; frequently-changing old code with structural problems is a hotspot. One way to measure the stability of a software architecture.

- `CodeAgeBuilder.build(commits) -> List<FileAge(file, ageMonths, lastChangeDate)>`
- Parameters: `-Pafter` (for filtering which files to consider), `-Ptop=N`
- Sorted by age descending (oldest/most stable first)
- Text table (File | Age (months) | Last Changed), JSON, LLM formatters

## 15. `cnavAuthors` — authors per module (Medium value)

Inspired by Code Maat's `authors` analysis. Number of distinct contributors per file — the more developers working on a module, the larger the communication challenges. High author counts correlate with defects and quality issues.

- `AuthorAnalysisBuilder.build(commits) -> List<ModuleAuthors(file, authors: Int, revisions: Int)>`
- Parameters: `-Pafter`, `-Pmin-revs=N`, `-Ptop=N`
- Sorted by author count descending
- Text table (File | Authors | Revisions), JSON, LLM formatters

## 16. `cnavChurn` — code churn analysis (Medium value)

Inspired by Code Maat's `abs-churn` and `entity-churn` analyses. Pre-release churn of a module is a good predictor of post-release defects. Measures lines added/deleted per file.

- `ChurnBuilder.build(commits) -> List<FileChurn(file, added: Int, deleted: Int, commits: Int)>`
- Parameters: `-Pafter`, `-Ptop=N`, optional `-Pby-date=true` for daily aggregation
- Sorted by total churn (added + deleted) descending
- Text table (File | Added | Deleted | Net | Commits), JSON, LLM formatters

## 17. Refactor Gradle tasks to use Config data classes (Medium value, low effort)

Gradle tasks currently duplicate parameter parsing logic that Config data classes already have. Each task should build a `Map<String, String?>` from `project.findProperty()` calls and delegate to `XxxConfig.parse()`. This removes duplication and ensures Gradle and Maven use identical parsing/validation.

## 18. Extract and test task-specific side effects (Low effort, high polish)

- `FindInterfaceImplsTask` cache-file naming logic (choosing `interface-registry-all.cache` vs `interface-registry.cache` based on `includetest`) should be extracted to a pure function.
- `DsmTask` HTML file writing should be extracted so the HTML generation is testable without file I/O.

## 19. Gradle TestKit integration test for CodeNavigatorPlugin (Medium value)

Verify all 15 tasks are registered with correct groups and dependencies using Gradle TestKit. Currently there are no tests that verify the plugin wiring itself.

## 20. Create remaining Maven Mojos (High value)

Only `ListClassesMojo` exists. All other task equivalents need Maven Mojos:
- Navigation: `FindClass`, `FindSymbol`, `ClassDetail`, `FindCallers`, `FindCallees`, `FindInterfaceImpls`, `PackageDeps`, `Dsm`
- Analysis: `Hotspots`, `Churn`, `CodeAge`, `AuthorAnalysis`, `ChangeCoupling`
- Help: `Help`, `AgentHelp`, `ConfigHelp`

## 21. Maven release process (Medium value)

Define and document the Maven plugin release process, separate from the Gradle release. The Maven plugin has its own version (`0.1.0-SNAPSHOT`) and will be released independently.

## 22. Gradle/Maven parity testing (High value)

Ensure both plugins support the same commands, parameters, and produce equivalent output. Approaches to consider:
- **Shared task registry**: A single source-of-truth list of all supported tasks/goals with their parameters, used by both plugins. If a task is missing from either plugin, the build (or a test) fails.
- **Approval tests**: Run the same operation via both `./gradlew cnavXxx` and `./mvnw cnav:xxx` against the test project and compare outputs. Differences indicate parity gaps.
- **Config parse coverage**: Since both plugins delegate to the same `XxxConfig.parse()` functions, parity at the config layer is already guaranteed. The risk is in the Mojo/Task wiring — forgetting to wire a parameter or misnaming a goal.
- **Generated documentation**: Auto-generate the parameter table (like `ConfigHelpText`) from the config data classes so docs can't drift from implementation.

## 23. `cnavXray` — function-level hotspots (Very high value)

Inspired by CodeScene's "X-Ray" feature. File-level hotspots are too coarse — a large file may have one or two methods that account for most churn. X-Ray drills into git diff hunks and maps them to methods (using bytecode line number tables) to produce method-level hotspot rankings.

- **Question**: "Which specific methods within a file are causing the most churn?"
- **Needs**: Git history + bytecode (line number tables from class files to map hunks to methods)
- **Builder**: `XRayBuilder.build(commits, classDir, targetFile) -> List<MethodHotspot(className, methodName, revisions, churn)>`
- **Parameters**: `-Pfile=<path>` (required — X-Ray a specific file), `-Pafter=YYYY-MM-DD`, `-Ptop=N`
- **Why high value**: Far more actionable than file-level hotspots for AI agents suggesting refactoring. Since we already have call graph infrastructure and git log parsing, this reuses both.

## 24. `cnavCycles` — dependency cycle detection (High value, low effort)

Detect and report circular dependencies between packages (or classes). We already compute the DSM which can visualize cycles, but an explicit cycle detection task reports them directly — much more useful for automation and AI agents than reading a matrix.

- **Question**: "Are there circular dependencies that should be broken?"
- **Needs**: Bytecode only (reuses existing `DsmDependencyExtractor` and `PackageDependencyBuilder`)
- **Builder**: `CycleDetector.findCycles(packageDeps) -> List<Cycle(packages: List<String>)>` — uses Tarjan's algorithm or DFS
- **Parameters**: `-Proot-package=<prefix>`, `-Pincludetest=true`
- **Output**: Each cycle listed with the packages involved, sorted by cycle length
- **Why high value**: Cycle detection is one of the most requested architecture checks (ArchUnit, NDepend, Structure101 all feature it). Low effort since we already have the dependency graph.

## 25. `cnavCohesion` — class cohesion / LCOM (High value)

Measures Lack of Cohesion of Methods (LCOM) per class. Analyzes which methods access which instance fields from bytecode. A class where methods cluster into distinct groups that don't share fields has low cohesion — it's doing too many things and should be split.

- **Question**: "Which classes are doing too many things and should be split?"
- **Needs**: Bytecode only (field access patterns from ASM visitor)
- **Builder**: `CohesionAnalyzer.analyze(classDir) -> List<ClassCohesion(className, lcom: Int, methodCount: Int, fieldCount: Int, clusters: Int)>`
- **Parameters**: `-Pfilter=<regex>`, `-Pmin-methods=N` (skip trivial classes, default 3), `-Ptop=N`
- **Output**: Classes ranked by LCOM descending. Optionally show the method/field clusters.
- **Why high value**: Strong signal for refactoring. Computable from bytecode we already parse. One of CodeScene's "code health" factors.

## 26. `cnavRank` — type/method importance ranking via PageRank (High value, low effort)

Apply PageRank to the existing call graph to identify the most "load-bearing" types and methods. Types with high rank are depended upon transitively by many others — a bug there is most catastrophic.

- **Question**: "Which types/methods are the most critical in the codebase?"
- **Needs**: Bytecode only (reuses existing call graph)
- **Builder**: `TypeRanker.rank(callGraph) -> List<RankedType(className, rank: Double, inDegree: Int, outDegree: Int)>`
- **Parameters**: `-Ptop=N` (default 30), `-Pprojectonly=true`, `-Pmethods=true` (rank methods instead of types)
- **Why high value**: Useful for AI agents to understand which classes are central vs peripheral. Also useful for prioritizing test coverage. Easy to implement since we already build the call graph.

## 27. `cnavDead` — dead code detection (High value, low effort)

Identify classes, methods, or fields that are never referenced by any other code in the project. Reports "potential dead code" since reflection-based usage can't be detected from bytecode alone.

- **Question**: "What code can safely be removed?"
- **Needs**: Bytecode only (reuses existing call graph and symbol index)
- **Builder**: `DeadCodeFinder.find(callGraph, symbolIndex, classIndex) -> List<DeadCode(className, memberName?, kind: CLASS|METHOD|FIELD)>`
- **Parameters**: `-Pfilter=<regex>`, `-Pinclude-fields=true`, `-Pexclude=<regex>` (exclude known entry points like `main`, `@Test`, Mojo/Task classes)
- **Caveats**: Must document that reflection, serialization, and framework magic (Spring beans, etc.) may cause false positives.
- **Why high value**: Very actionable for cleanup. AI agents can suggest removal with confidence when bytecode analysis shows zero references.

## 28. Complexity trends over time (High value, medium effort)

Track the complexity of hotspot files across git history. Instead of a snapshot, show whether a file is *deteriorating*, *stable*, or *improving*. Uses indentation-based complexity (proxy for cyclomatic complexity, works without full parsing) measured at each historical version.

- **Question**: "Is this hotspot getting harder to maintain over time, or are we keeping it under control?"
- **Needs**: Git history (fetches file content at historical versions via `git show`)
- **Builder**: `ComplexityTrendBuilder.build(file, commits) -> List<ComplexityPoint(date, complexity, revisionHash)>`
- **Parameters**: `-Pfile=<path>` (required), `-Pafter=YYYY-MM-DD`, `-Psamples=N` (number of historical points)
- **Could extend**: `cnavHotspots` with a `-Ptrend=true` flag, or be a standalone `cnavTrend` task
- **Why high value**: The direction of complexity change is more informative than an absolute value. A rising-complexity hotspot is the #1 refactoring target.

## 29. Coupling comparison — temporal vs structural (High value, medium effort)

Compare temporal coupling (files that change together, from git) with structural coupling (files that depend on each other, from bytecode). The mismatches are where the most interesting architectural insights hide:

- **Temporal coupling WITHOUT structural coupling** → hidden dependencies (shared config, copy-paste, implicit contracts)
- **Structural coupling WITHOUT temporal coupling** → potentially unused/dead dependencies

- **Question**: "Are there hidden dependencies not visible in the code structure?"
- **Needs**: Both git history and bytecode
- **Builder**: `CouplingComparisonBuilder.build(temporalCoupling, structuralDeps) -> List<CouplingMismatch(fileA, fileB, temporalDegree, structurallyLinked: Boolean, kind: HIDDEN_DEP|UNUSED_DEP)>`
- **Parameters**: Same as `cnavCoupling` + bytecode class dirs
- **Why high value**: Genuinely novel analysis from Tornhill's "Software Design X-Rays" that no other build plugin offers. Extremely useful for AI agents reasoning about refactoring.

## 30. Knowledge distribution / bus factor (Medium-high value, low effort)

Extends `cnavAuthors` with proportional ownership. Instead of just counting distinct contributors, compute each developer's share of contributions per file. Identifies "knowledge islands" where a single developer wrote >80% of the code.

- **Question**: "If developer X leaves, what parts of the codebase are at risk?"
- **Needs**: Git history only
- **Builder**: `KnowledgeDistributionBuilder.build(commits) -> List<FileOwnership(file, mainAuthor, mainAuthorShare: Double, totalAuthors: Int, busFactor: Int)>`
- **Parameters**: `-Pafter`, `-Ptop=N`, `-Prisk-threshold=N` (percentage, default 80)
- **Could extend**: `cnavAuthors` with a `-Pownership=true` flag
- **Why useful**: For AI agents, knowing who the expert is for a given file is immediately actionable. For teams, bus factor risks are critical planning info.

## 31. Stability/instability metrics — Robert C. Martin (Medium-high value, low effort)

For each package, compute Afferent Coupling (Ca = who depends on me), Efferent Coupling (Ce = who I depend on), Instability I = Ce/(Ca+Ce), and Abstractness A = abstract types / total types. The "distance from main sequence" D = |A + I - 1| measures how well a package balances stability and abstractness.

- **Question**: "Are our package dependencies well-structured?"
- **Needs**: Bytecode only (extends `package-deps` data)
- **Builder**: `StabilityAnalyzer.analyze(packageDeps, classIndex) -> List<PackageMetrics(pkg, ca, ce, instability, abstractness, distance)>`
- **Parameters**: `-Proot-package=<prefix>`
- **Why useful**: Well-established Clean Architecture metrics. Packages in the "zone of pain" (stable + concrete) or "zone of uselessness" (unstable + abstract) are worth flagging.

## 32. Shotgun surgery detection (Medium-high value, medium effort)

Identify commits where a single logical change touches many files across many packages — a code smell indicating poor encapsulation. Aggregates git history to find recurring patterns of widespread changes.

- **Question**: "Which kinds of changes cause the most widespread ripple effects?"
- **Needs**: Git history (optionally enriched with bytecode package structure)
- **Builder**: `ShotgunSurgeryDetector.detect(commits, minFiles, minPackages) -> List<ShotgunCommit(hash, date, author, filesChanged, packagesChanged, files: List<String>)>`
- **Parameters**: `-Pafter`, `-Pmin-files=N` (default 8), `-Pmin-packages=N` (default 3), `-Ptop=N`
- **Why useful**: Identifies poor encapsulation patterns. An AI agent could flag "this change pattern suggests concept X is spread across too many packages."

## 33. Refactoring targets — composite risk score (Medium value, medium effort)

Combine hotspot data (change frequency), code complexity (from bytecode or indentation), coupling degree, and author count into a single prioritized "refactoring score" per file. Answers the question every team lead asks: "where should we invest refactoring effort for maximum payoff?"

- **Question**: "What are the top refactoring targets in the codebase?"
- **Needs**: Both git history and bytecode
- **Builder**: `RefactoringTargetBuilder.build(hotspots, complexity, coupling, authors) -> List<RefactoringTarget(file, score, components: Map<String, Double>)>`
- **Parameters**: `-Pafter`, `-Ptop=N`, weights for each factor
- **Depends on**: Multiple other analyses (hotspots, cohesion, coupling, authors)
- **Why useful**: The "killer feature" of CodeScene — combining signals into an actionable priority list.

## 34. Layer violation detection (Medium value, medium effort)

Given user-defined architectural layers (e.g., controller → service → persistence), detect violations where a lower layer depends on a higher one. Provides a declarative way to check layer rules without writing ArchUnit test code.

- **Question**: "Does the code respect our intended architectural boundaries?"
- **Needs**: Bytecode only
- **Configuration**: Layer definitions in build config (e.g., `cnav.layers = ["controller", "service", "domain", "persistence"]`) or a properties file
- **Builder**: `LayerViolationDetector.detect(packageDeps, layerConfig) -> List<Violation(from, to, fromLayer, toLayer)>`
- **Why useful**: Architecture enforcement without requiring ArchUnit dependency or test code. More of a reporting/checking concern than analysis.

## 35. Knowledge loss / former contributor risk (Medium value, low effort)

Identify code primarily written by developers who are no longer active (haven't committed in N months). Flags modules where the dominant contributor has gone silent.

- **Question**: "Which parts of the codebase are maintained by people who may have left?"
- **Needs**: Git history only
- **Builder**: `KnowledgeLossDetector.detect(commits, inactiveMonths) -> List<AtRiskFile(file, mainAuthor, lastAuthorCommit, authorShare, isActive: Boolean)>`
- **Parameters**: `-Pinactive-months=N` (default 6), `-Ptop=N`
- **Why useful**: Risk assessment for team planning. Less actionable for AI agents day-to-day.

## 36. Developer coordination / fragmentation (Low-medium value, low effort)

Measures how scattered contributions are across a file. High fragmentation (many authors each contributing small pieces) correlates with higher defect risk compared to concentrated ownership. Extends Code Maat's `fragmentation` metric.

- **Question**: "Where are coordination bottlenecks in the codebase?"
- **Needs**: Git history only
- **Builder**: `FragmentationAnalyzer.analyze(commits) -> List<FileFragmentation(file, fragmentation: Double, authors: Int, revisions: Int)>`
- **Parameters**: `-Pafter`, `-Ptop=N`
- **Why useful**: More relevant for large teams. Can be combined with other metrics (hotspots, authors) for richer analysis.

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

## 38. Full classpath scanning option for searches and identification (High value, medium effort)

Most searches and identifications should have an option to include the full classpath, not just the project code. Currently `cnavListClasses`, `cnavFindClass`, `cnavFindSymbol`, `cnavClass`, `cnavCallers`, `cnavCallees`, `cnavInterfaces`, and `cnavUsages` only scan the project's compiled output directories. But when checking what is available on the classpath — e.g., verifying a library class's method signatures, finding all implementations of a framework interface, or understanding what types are available — scanning only project code is insufficient.

- **Question**: "What does this library class look like? What methods does it have? Who on my full classpath implements this interface?"
- **Parameter**: `-Pclasspath=true` — scan the full runtime classpath (project classes + all dependency JARs) instead of only project output directories
- **Needs**: Gradle's `configurations.runtimeClasspath.resolve()` / Maven's `project.runtimeClasspathElements` to enumerate all JAR files and class directories
- **Applies to**: `cnavListClasses`, `cnavFindClass`, `cnavFindSymbol`, `cnavClass`, `cnavInterfaces`, `cnavUsages`, potentially `cnavCallers`/`cnavCallees`
- **Considerations**:
  - Classpath scanning is significantly slower (thousands of classes in typical dependency trees). Consider caching scanned JARs by checksum.
  - Output can be very large. May want to combine with existing `-Ppattern` / `-Powner` filters to narrow scope.
  - `cnavFindSymbol -Pclasspath=true -Ppattern="LocalDate"` would show all `LocalDate` symbols across every JAR — useful for finding the right FQN.
  - `cnavClass -Pclasspath=true -Ppattern="kotlinx.datetime.LocalDate"` would show the full signature of a library class without needing to look up docs.
- **Why high value**: AI agents frequently need to check library API signatures to write correct code. Currently they must rely on training data or web lookups. Classpath scanning gives ground-truth answers from the actual dependency versions in the project.

## 39. `cnavTypeChanges` — detect API signature changes between compilations (High value, medium effort)

From migration feedback: after changing a dependency version (e.g., `kotlinx-datetime` → `kotlin.time`), cascading type changes ripple through the codebase. A `xTimestamp()` column returning `Column<kotlinx.datetime.Instant>` silently becomes `Column<kotlin.time.Instant>`, breaking every domain model, repository, and service that consumed it. A tool that diffs "before" and "after" API surfaces would map the blast radius instantly.

- **Question**: "After recompiling with an updated dependency, which method/field signatures changed and what project code is affected?"
- **Needs**: Bytecode from two compilation passes (before and after the dependency change)
- **Approach**: Scan class directories twice — once from a cached/saved baseline, once from the current build — and diff the extracted signatures. For each changed signature, use `cnavCallers`/`cnavUsages` to find all affected call sites.
- **Builder**: `TypeChangeDetector.diff(baselineClassDir, currentClassDir) -> List<SignatureChange(className, memberName, oldSignature, newSignature, kind: METHOD|FIELD|SUPERTYPE)>`
- **Parameters**:
  - `-Pbaseline=<path>` — path to baseline class directory (or auto-save on first run)
  - `-Paffected=true` — also list all project call sites affected by each change (combines with `cnavUsages`/`cnavCallers`)
- **Real-world example**: `LocalDate.month` changed return type from `Int` to `kotlinx.datetime.Month` — this caused 48 test failures. Bytecode analysis would have flagged the signature change and listed every caller expecting `Int`.
- **Why high value**: Dependency upgrades are one of the most common sources of subtle breakage. The blast radius is currently discovered only at test time (or worse, runtime). Bytecode diffing gives you the full migration checklist before you even run tests.

## 40. `cnavFindSymbol` should support external type references (Medium value, low effort)

From migration feedback: running `cnavFindSymbol -Ppattern=Instant` only finds project-defined symbols (methods/fields named "Instant"), not import sites or references to external types like `kotlinx.datetime.Instant`. For migrations, you need to search for *references to* external types, not just *definitions of* symbols.

This is partially addressed by item 38 (full classpath scanning) which would let `cnavFindSymbol -Pclasspath=true` find symbols defined in external JARs. But the migration use case is different — you want to find *project code that references* an external type, not the external type's own definition. `cnavUsages` (item 37) addresses this for method calls and field accesses, but `cnavFindSymbol` could also be enhanced to show project methods/fields whose *signatures* reference a given external type (parameters, return types, field types).

- **Question**: "Which project methods have `Instant` in their signature?"
- **Approach**: When scanning project bytecode for symbols, also extract types from method descriptors and field types. Allow `-Ppattern` to match against referenced types, not just symbol names.
- **Overlap**: This overlaps with `cnavUsages -Ptype=<class>` which already finds type references in signatures. Consider whether this should just be better documentation pointing users to `cnavUsages` for this use case, rather than duplicating functionality in `cnavFindSymbol`.

## ~~41. `cnavUsages` — smarter "no results" guidance and `-Ptype` should also find method call owners (Medium value, low effort)~~ DONE

From real-world migration feedback: `cnavUsages -Ptype=ContextKt` returned "No usages found" because `-Ptype` only searched for type references (NEW, CHECKCAST, INSTANCEOF, descriptor types). Now `-Ptype` is comprehensive: it also matches method call and field instruction owners, so `-Ptype=ContextKt` finds calls to `ContextKt.locateResourceFile()`. Additionally, empty results now show guidance suggesting FQN checks and alternative parameters.

## 42. ~~`cnavCycleDetail`~~ `-Pcycles=true` on `cnavDsm` — dedicated cycle detail view ✅ DONE

Implemented as a `-Pcycles=true` parameter on the existing `cnavDsm` task (rather than a separate task). When `cycles=true`, skips the full DSM matrix and outputs only cycle details with class-level edges in both directions. Supports all three output formats (TEXT, JSON, LLM). Note: source file locations are not tracked in the DSM data model, so edges show class names only (not file:line).

From real-world feedback: the DSM task crams cycle edge details into dense one-liners that are hard to read. A dedicated task that shows each cycle as clear A→B / B→A pairs with file locations would be more ergonomic.

- **Question**: "Show me exactly which classes create each package cycle, with source file locations"
- **Needs**: Bytecode only (reuses existing DSM cycle detection from `DsmBuilder`)
- **Output**: Each cycle as a section with the A→B edges listed per class, including source file. Example:
  ```
  CYCLE: pkg.a <-> pkg.b
    pkg.a → pkg.b:
      com.example.a.Foo (Foo.kt:12) → com.example.b.Bar.doStuff()
      com.example.a.Baz (Baz.kt:5) → com.example.b.Qux.<init>
    pkg.b → pkg.a:
      com.example.b.Bar (Bar.kt:30) → com.example.a.Foo.getName()
  ```
- **Overlap with item 24 (`cnavCycles`)**: Item 24 planned cycle detection as a list of involved packages. This item extends that with class-level edge detail. Could be a `-Pdetail=true` flag on `cnavCycles` rather than a separate task.
- **Why**: The DSM is great for visualization but poor for actionability. When fixing a cycle, you need to know the specific import edges to break. This view gives that directly.

## 43. DSM "what-if" mode for cycle breaking (High value, medium effort)

From real-world feedback: the DSM tells you which cycles exist, but you still have to reason about the fix yourself. A "what-if" mode would let you say "if I move class X to package Y, would the cycle break?" and get an answer without actually making the change.

- **Question**: "Would moving `locateResourceFile` from `root` to `util` break the cycle between `root` and `web.plugins`?"
- **Parameter**: `-Pwhat-if=<class>:<target-package>` — simulate moving a class to a different package and re-evaluate cycles
- **Approach**: Take the existing dependency graph, rewrite the package assignment for the specified class, and re-run cycle detection. Report which cycles would be resolved and which would remain.
- **Builder**: `WhatIfSimulator.simulate(dsmResult, move: Pair<String, String>) -> WhatIfResult(resolvedCycles, remainingCycles, newCycles)`
- **Why**: Turns the DSM from a diagnostic tool into a planning tool. The user reported "you still have to reason about the fix yourself" — this automates that reasoning.

## 44. Deduplicate `cnavUsages` output (Low effort, high polish)

From user feedback: when the same method is called multiple times from the same caller method, `cnavUsages` (especially with `-PownerClass`) produces duplicate lines. The `UsageScanner` collects into a `mutableListOf<UsageSite>()` and every matching bytecode instruction appends a new entry — no deduplication at any level. All formatters (text, JSON, LLM) pass through the raw list.

- **Fix**: Deduplicate at the scanner level by switching to `mutableSetOf<UsageSite>()` (since `UsageSite` is a data class, set equality works automatically). Alternatively, deduplicate at the formatter level with `.distinct()` to preserve raw data for other consumers.
- **Contrast**: The `DsmDependencyExtractor` already uses `mutableSetOf<PackageDependency>()` and has a test `produces unique dependencies per class pair` — `UsageScanner` should follow the same pattern.
- **Key file**: `UsageScanner.kt` line 34

## 45. Fix `cnavDsm` HTML path resolution (Low effort, bug fix)

From user feedback: when using `-Pdsm-html=dsm.html` with a relative path, the HTML file is written to (and the logged path shows) the Gradle daemon's working directory instead of the project directory. This is because `DsmTask.kt` uses `File(htmlPath)` which resolves against the JVM's current working directory — which for the Gradle daemon is typically `~/.gradle/daemon/<version>/`.

- **Fix**: Change `File(htmlPath)` to `project.file(htmlPath)` in `DsmTask.kt` line 56. Gradle's `project.file()` resolves relative paths against the project directory. The Maven `DsmMojo.kt` has the same pattern but Maven mojos typically run with cwd set to the project root, so the bug is less likely there — but should be fixed to `File(project.basedir, dsmHtml)` for correctness.
- **Key files**: `DsmTask.kt` lines 55-59, `DsmMojo.kt` lines 74-79

## 46. `cnavTestHealth` — verify all test methods actually ran (High value, medium effort)

From user feedback: a project had 19 silently skipped tests because test methods had non-`Unit` return types. The test framework silently ignored them — the suite looked green while tests weren't running.

Rather than trying to heuristically detect "suspicious" signatures (which is fragile and framework-specific), the robust approach is **count-and-verify**: count `@Test`-annotated methods from bytecode, compare against JUnit XML results from the actual test run, and flag the delta.

### Approach: bytecode expected count vs. JUnit XML actual count

1. **Bytecode scan** (pre-existing infrastructure): Use ASM `visitAnnotation()` on test class directories to find all methods annotated with `@Test` (JUnit 4: `org.junit.Test`, JUnit 5: `org.junit.jupiter.api.Test`, Kotlin Test: `kotlin.test.Test`). This is the "expected" set.
2. **JUnit XML scan** (new): Parse the test result XML files written after `test` runs. Both Gradle (`build/test-results/test/TEST-*.xml`) and Maven (`target/surefire-reports/TEST-*.xml`) use the same JUnit XML format. Each `<testcase>` element represents a method that the framework actually attempted. This is the "actual" set.
3. **Diff**: For each test class, report methods present in bytecode but absent from XML results. These are the silently skipped tests — regardless of *why* they were skipped.

### Why this is more robust than signature heuristics

- Catches **any** reason for silent skipping: wrong return type, wrong parameters, framework version incompatibilities, visibility issues, misconfigured test engines, etc.
- No need to maintain a list of "known bad patterns" per framework version
- Works for JUnit 4, JUnit 5, Kotlin Test, and any framework that writes JUnit XML results
- Zero false positives: if a method ran, it appears in the XML

### Task design

- **Question**: "Did all `@Test`-annotated methods actually execute?"
- **Needs**: Bytecode (test class dirs) + JUnit XML results (post-test-run)
- **Lifecycle**: `dependsOn("test")` in Gradle / `@Execute(phase = LifecyclePhase.TEST)` in Maven — runs *after* tests complete
- **Builder**: `TestHealthAnalyzer.analyze(testClassDirs, junitXmlDir) -> TestHealthReport(expected: Int, actual: Int, missing: List<MissingTest(className, methodName)>, extras: List<...>)`
- **Parameters**:
  - `-Pfilter=<regex>` to scope to specific test classes
  - `-Presults-dir=<path>` to override the JUnit XML location (defaults to convention)
- **Output**:
  ```
  Test Health: 142 expected, 123 ran, 19 missing

  Missing tests (found in bytecode but not in test results):
    com.example.UserServiceTest.shouldValidateEmail
    com.example.UserServiceTest.shouldHashPassword
    com.example.OrderTest.shouldCalculateTotal
    ...
  ```
- **Additional checks** (secondary, from bytecode only — no XML needed):
  - Test methods missing `@Test` annotation but named `test*` (possible forgotten annotation)
  - Test classes with no `@Test` methods (empty test class)
  - `@Disabled`/`@Ignore` inventory for awareness (expected to be absent from XML, but worth reporting)

### Implementation notes

- The `ClassDetailExtractor` already extracts method signatures and return types. It needs extension with `visitAnnotation()` to detect test annotations.
- JUnit XML parsing is straightforward — the `<testsuite>` element has `tests`/`skipped`/`failures`/`errors` counts, and each `<testcase>` has `name` + `classname`.
- Test source set directories are already accessed by `FindInterfaceImplsTask` when `includetest=true` — same pattern applies.
- Both Gradle and Maven write the same XML format, so one parser handles both.

## 47. `cnavComplexity` — method-level fan-in/fan-out for a class (Medium value, low effort)

From user feedback: the DSM shows package-level dependencies, but a "method-level fan-in/fan-out" view for a specific class would help prioritize which classes are most entangled and need extraction first. Example: `cnavComplexity -Pclass=AdminRoutes` showing "47 outgoing calls to 12 distinct classes."

- **Question**: "How entangled is this class? How many distinct classes does it depend on, and how many depend on it?"
- **Needs**: Bytecode only (reuses existing `CallGraph`)
- **Builder**: `ClassComplexityAnalyzer.analyze(callGraph, className) -> ClassComplexity(className, fanOut: Int, fanIn: Int, distinctOutgoingClasses: Int, distinctIncomingClasses: Int, outgoingCalls: List<CallDetail>, incomingCalls: List<CallDetail>)`
- **Parameters**: `-Pclass=<pattern>` (required), `-Pprojectonly=true`, `-Pdetail=true` (show individual calls)
- **Output example**:
  ```
  AdminRoutes
    Fan-out: 47 calls to 12 distinct classes
    Fan-in:  8 calls from 3 distinct classes
    Top outgoing: UserRepository (14), SessionService (9), AdminService (8), ...
    Top incoming: Application (4), TestSetup (3), HealthCheck (1)
  ```
- **Overlap with item #26 (`cnavRank`)**: `cnavRank` computes PageRank + inDegree/outDegree across the whole graph. This task is focused on a single class deep-dive — complementary, not redundant. `cnavRank` answers "what's most important globally" while `cnavComplexity` answers "how tangled is this specific class."
- **Why useful**: When planning an extraction refactoring, knowing the fan-out count and which classes are called tells you exactly what interfaces you'll need. This is the structural counterpart to the behavioral data from `cnavHotspots`.

## 48. Targeted cycle filter for DSM (Medium value, low effort)

From user feedback during a DSM-driven cycle-breaking session: when the DSM shows multiple cycles, you typically work on one at a time. Currently the DSM highlights all cycles, making it hard to focus. A `-Pcycle=pkgA,pkgB` parameter would filter the DSM output to show only edges participating in the cycle between two specific packages.

- **Question**: "What are the specific dependency edges between these two packages that form a cycle?"
- **Needs**: Bytecode only (reuses existing DSM + cycle detection)
- **Parameters**: `-Pcycle=<pkg1>,<pkg2>` — show only the edges between the two named packages that participate in a cycle
- **Output**: Same DSM format, but with non-relevant cycle highlights suppressed. Only the selected cycle's edges are marked.
- **Why useful**: During iterative cycle-breaking, you fix one cycle at a time. Seeing all cycles at once creates noise. This lets you confirm "did I break this specific cycle?" without recompiling and re-running the full DSM.

## 49. `cnavWhyDepends` — dependency edge explanation (High value, medium effort)

From user feedback: the DSM tells you package A depends on package B, but not *why*. To break a cycle you need to know the specific fields, method parameters, return types, and local variable types that create the dependency. Currently this requires manual grepping.

- **Question**: "Why does class/package A depend on class/package B? What are the specific code-level references?"
- **Needs**: Bytecode only (reuses existing `CallGraph` + `ClassDetailExtractor`)
- **Builder**: `DependencyExplainer.explain(callGraph, from, to) -> List<DependencyEdge(sourceClass, targetClass, kind: FIELD|PARAMETER|RETURN_TYPE|LOCAL_VAR|METHOD_CALL, detail: String)>`
- **Parameters**: `-Pfrom=<class-or-package>` (required), `-Pto=<class-or-package>` (required), `-Pprojectonly=true`
- **Output example**:
  ```
  Why services.interfaces.RAClient depends on ra.SignatureContext:

    FIELD        RAClient.signatureCtx: SignatureContext
    PARAMETER    RAClient.sign(context: SignatureContext): SignedRequest
    RETURN_TYPE  RAClient.getContext(): SignatureContext
    METHOD_CALL  RAClient.init() -> SignatureContext.<init>()
  ```
- **Why useful**: This is the missing link between "the DSM says there's a dependency" and "here's what to move/extract to break it." Eliminates manual code searches during cycle-breaking.

## 50. Cross-package usage filtering for `cnavUsages` (Medium value, low effort)

From user feedback: when breaking cycles, you need to find usages of a type or field *from outside a specific package*. Usages within the same package are expected and irrelevant. Example: "who outside `ra/` accesses `RAClientResult.signedRequest`?"

- **Question**: "Who uses this symbol from outside its home package?"
- **Needs**: Bytecode only (reuses existing `UsageScanner`)
- **Parameters**: `-Poutside-package=<pkg>` — exclude usages where the caller is inside the given package
- **Output**: Same as `cnavUsages`, but filtered to only show callers outside the specified package boundary.
- **Why useful**: When extracting a type to break a cycle, you need to know who the external consumers are. Internal usages within the same package will naturally follow the extraction. This filter removes the noise and shows only the cross-boundary references that matter for the refactoring.

## Future ideas (not yet planned)

- **Consider just failing on first file with wrong bytecode**: The current `ScanResult<T>` partial-fail approach adds complexity across scanners, caches, tasks, and mojos. A simpler alternative: fail fast on the first unsupported bytecode file with a clear error message. Less graceful but dramatically less code.
- **Expand support for older JDKs**: The plugin currently requires JDK 21+ (Gradle 9.x minimum). Explore lowering the bytecode target or supporting Gradle 8.x to reach projects still on JDK 17 or JDK 11.
- **Cross-referencing hotspots with bytecode data**: Combine `cnavHotspots` with `cnavCallers`/`cnavDeps` to answer "hotspot files and their structural dependencies". Would require mapping git file paths to bytecode class names via the source file metadata already extracted.
- **Entity ownership / main developer**: Who "owns" each file by contribution weight (`-a entity-ownership` and `-a main-dev` in Code Maat). Useful for "who should I ask about this code?" Could be added as a mode on `cnavAuthors`.
- **Architectural-level grouping**: Code Maat's `-g` flag to aggregate file-level results by logical component/layer. Would allow running hotspots, coupling, etc. at the sub-system level instead of individual files.
- **Source-level structural analysis**: For faster iteration during cycle-breaking, analyze imports and type references directly from source files without requiring compilation. This would allow running dependency analysis before a successful build — useful when mid-refactoring with compile errors. Fundamentally different approach from bytecode analysis, so this is a future exploration rather than an incremental feature.
