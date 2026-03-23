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

## Future ideas (not yet planned)

- **Cross-referencing hotspots with bytecode data**: Combine `cnavHotspots` with `cnavCallers`/`cnavDeps` to answer "hotspot files and their structural dependencies". Would require mapping git file paths to bytecode class names via the source file metadata already extracted.
- **Entity ownership / main developer**: Who "owns" each file by contribution weight (`-a entity-ownership` and `-a main-dev` in Code Maat). Useful for "who should I ask about this code?" Could be added as a mode on `cnavAuthors`.
- **Architectural-level grouping**: Code Maat's `-g` flag to aggregate file-level results by logical component/layer. Would allow running hotspots, coupling, etc. at the sub-system level instead of individual files.
- **Temporal coupling with structural coupling overlay**: Visualize where temporal coupling (from git) aligns or conflicts with structural coupling (from bytecode). Files that change together but have no call-graph relationship may indicate a missing abstraction.
- **Fragmentation analysis**: Code Maat's `fragmentation` metric — measures how scattered contributions are across a module. High fragmentation = many authors each contributing small pieces = higher defect risk than concentrated ownership.
