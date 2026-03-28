# Plan — Completed

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

Added `-Dframework=spring` (also: `jpa`, `jackson`) parameter to `cnavDead` that auto-excludes known framework annotations from dead code results. Eliminates most false positives in framework-heavy projects without requiring manual `-Dexclude-annotated` lists.

**Spring preset** includes: `Controller`, `RestController`, `Service`, `Component`, `Repository`, `Configuration`, `Bean`, `Scheduled`, `EventListener`, `PostConstruct`, `PreDestroy`, `ExceptionHandler`, `ControllerAdvice`, `Endpoint`, `SpringBootApplication`, `EnableAutoConfiguration`, `ComponentScan`, plus JPA annotations (`Entity`, `MappedSuperclass`, `Embeddable`, `Converter`, `EntityListeners`).

**Multiple presets** can be combined: `-Dframework=spring,jackson`. Framework annotations are merged with any explicit `-Dexclude-annotated` values.

**Tested on Spring Petclinic**: reduced dead code results from 22 items (18 false positives) to 8 items (5 `package-info` files + 3 legitimate edge cases). Implementation: `FrameworkPresets.kt` lookup object, wired through `DeadCodeConfig.parse()`, `TaskRegistry.FRAMEWORK` param, Gradle `DeadCodeTask`, and Maven `DeadCodeMojo`.

## ~~77. Interface dispatch resolution in `cnavCallers`/`cnavCallees`~~ DONE

Added interface dispatch resolution to `CallTreeBuilder` so that `cnavCallers` and `cnavCallees` follow calls through interfaces. When tracing callers of `Impl.method()`, also finds callers of `Interface.method()` where `Impl` implements `Interface`. When tracing callees from a call to `Interface.method()`, also shows concrete implementor methods.

**Implementation**: `CallTreeBuilder.resolveInterfaceDispatch()` uses two maps from `InterfaceRegistry`: `implementorMap()` (interface → set of implementor class names) and `classToInterfacesMap()` (class → set of interfaces it implements). Always on — no flag needed since results are strictly better with dispatch resolution.

**Wired into**: Gradle `FindCallersTask`, `FindCalleesTask` (via `InterfaceRegistryCache`), Maven `FindCallersMojo`, `FindCalleesMojo` (via `InterfaceRegistry.build()`). Added `implementorMap()` and `classToInterfacesMap()` convenience methods to `InterfaceRegistry`.

**Tested on Spring Petclinic**: `find-callers` for `OwnerRepository.findById` now correctly shows callers from `OwnerController`, `PetController`, and `VisitController`. 5 new tests (3 in `CallTreeBuilderTest`, 2 in `InterfaceRegistryTest`).
