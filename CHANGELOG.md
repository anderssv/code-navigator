# Changelog

## 0.1.45

- **Improved:** Filter Kotlin compiler-internal annotations (e.g. `@kotlin.Metadata`, `@kotlin.jvm.internal.*`, `@org.jetbrains.annotations.*`) from `cnavClassDetail` and `cnavAnnotations` output. These synthetic annotations cluttered results without providing useful information.
- **Fixed:** `cnavDead` now ensures test classes are compiled before analysis. Previously, prod-only dead code detection could miss test usages when test compilation hadn't run yet. Gradle tasks depend on `testClasses`; Maven mojo runs at `TEST_COMPILE` phase.
- **Improved:** `cnavAnnotations` gained `include-test` parameter to include test source set annotations in results.
- **Improved:** `cnavFindSymbol` gained `include-test` parameter to include test source set symbols in results.
- **Improved:** Error messages now auto-generated from `TaskDef` via `usageHint(BuildTool)` instead of hardcoded strings. Fixes 5 tasks that still referenced deprecated pre-rename aliases in their error output.

## 0.1.44

- **Improved:** Runtime validation of property keys in `enhanceProperties()` — throws `IllegalArgumentException` if a Maven mojo's `buildPropertyMap()` contains keys not matching `TaskDef.params`. Catches drift between Maven mojos and TaskDef at runtime.
- **Improved:** Required parameter validation centralized via `ParamDef.parseRequiredFrom()`. Applied to 9 Config classes — consistent error messages with task name and expected parameters.
- **Improved:** HelpText parameter descriptions now default to `ParamDef.description`, eliminating ~58 duplicated description strings. Task-specific context retained where it adds value.
- **Improved:** `ParamDef` gained `deprecated` and `deprecatedMessage` fields for structured deprecation tracking.
- **Improved:** All `Config.parse()` methods standardized to use `ParamDef.parseFrom(properties)` instead of raw map lookups, ensuring param name, type parsing, and default values come from a single definition.
- **Improved:** Replaced `root-package` with `package-filter` for DSM/cycles/metrics. Default now auto-detects project classes — no configuration needed. `-Proot-package` still works but emits deprecation warning.
- **Improved:** Better error messages for wrong parameter names in callers/callees tasks.
- **Refactoring:** Gradle task names derived from `TaskDef.goal` — legacy names registered as deprecated aliases with warnings.
- **Refactoring:** `TaskCategory` enum drives `ConfigHelpText` grouping instead of hardcoded task lists.
- **Refactoring:** Gradle plugin registration auto-generated from `TaskRegistry.ALL_TASKS` loop.

## 0.1.43

- **New:** `cnavContext` task / `cnav:context` goal — smart context gathering for AI agents. Given a class pattern, gathers class detail (signature, fields, methods, annotations), callers tree, callees tree, interface implementations, and implemented interfaces in a single invocation. Parameters: `-Ppattern=<class>` (required), `-PmaxDepth=<n>` (default 2), plus standard filtering (`project-only`, `prod-only`, `test-only`) and output format (`format=text|json|llm`). Pure composition of existing features — reduces agent round-trips from 4-5 to 1.

## 0.1.42

- **New:** Prod/test source set separation — all bytecode tasks now tag each caller, callee, and usage reference with `[test]` or `[prod]` based on which source set the class came from. Adds `-Pprod-only=true` / `-Ptest-only=true` filtering parameters to `cnavCallers`, `cnavCallees`, `cnavUsages`, `cnavComplexity`, and `cnavRank`.
- **New:** `SourceSet` enum (`MAIN`/`TEST`) in `DomainTypes.kt` with full propagation through `CallGraph`, `CallTreeNode`, `UsageSite`, and all formatters (TEXT, LLM, JSON).
- **New:** `CallGraphBuilder.buildTagged()` accepts tagged directories (`List<Pair<File, SourceSet>>`) for source-set-aware call graph construction.
- **New:** `CallGraphCache` persists source sets in a backward-compatible `[SOURCE_SETS]` section.
- **New:** `UsageScanner.scanTagged()` tags each `UsageSite` with the directory's source set.
- **New:** Gradle `Project.taggedClassDirectories()` and Maven `MavenProject.taggedClassDirectories()` helpers for resolving tagged source set directories.

## 0.1.41

- **Breaking:** Renamed `framework` parameter to `exclude-framework` with inverted semantics. All framework presets are now active by default — no configuration needed for Spring, Quarkus, or other frameworks. Use `exclude-framework=<preset>` to disable a specific preset, or `exclude-framework=ALL` to disable all.
- **New:** gRPC framework preset. Entry-point annotations: `@GrpcService`, `@GrpcClient`, `@GlobalInterceptor`, `@GrpcGenerated` (Quarkus, Spring, and standard gRPC). Modifier annotations: `@Blocking`, `@NonBlocking`, `@RunOnVirtualThread`, `@RegisterInterceptor` (SmallRye/Quarkus). Available as standalone `grpc` preset and included in `spring` and `quarkus` composites.
- **Improved:** Dead code output now includes a note about using `exclude=<regex>` to filter out known false positives (e.g. generated packages).
- **Improved:** Help texts document using `exclude` for package-level filtering of dead code results (e.g. generated protobuf code).

## 0.1.40

- **Improved:** `cnavCallers`/`cnavCallees` — annotation parameters now appear in call tree output. Annotations show their parameters (e.g. `@GetMapping(value="/users") [spring]`) in all three output formats (TEXT, LLM, JSON). Previously only the annotation name and framework were shown.
- **Improved:** Introduced `AggregatedAnnotations` as the return type from `AnnotationExtractor.scanAll()`, carrying parameter maps alongside annotation name sets.
- **Refactoring:** Extracted shared `annotationParameterVisitor()` utility from duplicated ASM visitor logic in `AnnotationExtractor` and `ClassDetailExtractor`.

## 0.1.39

- **Improved:** Split `navigation/` package into 12 feature sub-packages (`annotation/`, `callgraph/`, `classinfo/`, `complexity/`, `deadcode/`, `dsm/`, `hierarchy/`, `interfaces/`, `metrics/`, `rank/`, `stringconstant/`, `symbol/`) for better code organization. Shared utilities remain at the `navigation/` root.
- **Improved:** Maven plugin now routes all mojos through `TaskRegistry.enhanceProperties()` for consistency with Gradle tasks and forward-compatibility with pattern enhancement.
- **Improved:** Maven plugin now uses `FileCache` for call graph, interface registry, class index, and symbol index — matching Gradle's caching behavior for faster repeated runs.
- **Improved:** `OutputWrapper.formatAndWrap()` — new convenience method combining format dispatch and output wrapping, used by all Gradle tasks and Maven mojos.
- **Improved:** Added Jakarta Validation and JPA/Jackson framework annotation presets for dead code detection.
- **Improved:** Annotation handling now uses fully-qualified names internally (`AnnotationExtractor`, `FrameworkPresets`, `ClassDetailExtractor`), with `AnnotationName` inline value class for type safety.
- **Improved:** Pattern matching documentation added to `cnavHelp` and `cnavAgentHelp` output.
- **Improved:** README split into `doc/` files (`tasks.md`, `agent-setup.md`, `how-it-works.md`) with task summary table.
- **Refactoring:** Kebab-case parameter consistency — renamed `projectonly` → `project-only`, `includetest` → `include-test`, `ownerClass` → `owner-class`.
- **Refactoring:** All Gradle tasks migrated from raw `buildPropertyMap()` to `buildPropertyMap(TaskDef)` for centralized property enhancement.
- **Refactoring:** Complexity task parameter renamed from `classname` to `pattern` (reuses shared `PATTERN` ParamDef).
- **Refactoring:** Split `METHOD` ParamDef into `CALL_PATTERN` (callers/callees, with pattern enhancement) and `METHOD` (find-usages only).

## 0.1.38

- **New:** `cnavAnnotations` task / `cnav:annotations` goal — query classes and methods by annotation pattern. Parameters: `-Ppattern=<annotation-name-regex>` (required), `-Pmethods=true` (show method-level matches). Finds all classes/methods bearing matching annotations with source file locations. Supports TEXT, JSON, and LLM output formats. Useful for endpoint discovery (`@GetMapping`), transaction boundary analysis (`@Transactional`), async method inventory (`@Async`), and more.
- **Improved:** `cnavCallers`/`cnavCallees` — interface dispatch resolution. When tracing callers of `Impl.method()`, also finds callers of `Interface.method()` where `Impl` implements `Interface`. When tracing callees from a call to `Interface.method()`, shows concrete implementor methods. Always on — no flag needed. Fixes a major gap in Spring/DI-heavy codebases where calls go through interfaces.
- **Improved:** `cnavDead` — framework annotation presets. New `-Pframework=spring|jpa|jackson` parameter auto-excludes known framework annotations from dead code results. Multiple presets can be combined (`-Pframework=spring,jackson`). The Spring preset includes Controller, Service, Component, Repository, Configuration, Bean, Entity, and 15+ more annotations. Reduces false positives significantly in framework-heavy projects.
- **Improved:** `cnavDead` — `package-info` classes are now automatically filtered from dead code results (they are metadata-only and never referenced by other classes).
- **Improved:** `cnavDead` — dead code reason tagging. Added `reason` field (`NO_REFERENCES` or `TEST_ONLY`) and `-Pprod-only=true` filter to distinguish "never referenced anywhere" from "only used in tests."

## 0.1.37

- **Improved:** Dead code detection — external interface confidence flagging. Dead methods on classes that implement interfaces from outside the project scope (e.g. `javax.xml.bind.XmlAdapter`, `com.sksamuel.hoplite.Decoder`, `javax.net.ssl.HostnameVerifier`) are now flagged with LOW confidence instead of HIGH, since they are likely invoked by frameworks via reflection. Dead classes are not affected — if no one constructs the class, the external interface doesn't help.

## 0.1.36

- **Improved:** Dead code detection — Kotlin inline function filtering. Inline functions leave no call edges in bytecode (the compiler inlines the body at each call site), causing them to be falsely flagged as dead. Now parses `@kotlin.Metadata` annotations using `kotlin-metadata-jvm` to identify inline functions and filters them from dead method results. New dependency: `org.jetbrains.kotlin:kotlin-metadata-jvm`.

## 0.1.35

- **Improved:** Dead code detection — intra-class call tracking. Methods called within the same class by an externally-alive method are no longer flagged as dead. Uses transitive BFS propagation so `A→B→C` within a class marks all three alive when `A` is called from outside. Previously the #1 source of false positives.
- **Improved:** Dead code detection — interface dispatch resolution. When `Interface.method()` is called, all implementing classes' `method()` are now marked as alive. Uses `InterfaceRegistry` data already available in the plugin.
- **Improved:** Dead code detection — Kotlin property accessor filtering. Generated `getName()`/`setName()` methods matching declared fields are no longer reported as dead. Uses existing `KotlinMethodFilter.isAccessorForField()` with a new `FieldExtractor` that scans class files for field names.

## 0.1.34

- **Changed:** `cnavAgentHelp -Psection=install` output slimmed down to a minimal blurb — announces the tool and points to `cnavAgentHelp` for details. Task lists, parameter docs, and permission setup removed from install section.
- **New:** `cnavAgentHelp -Psection=setup` — new section with Claude Code permission rule instructions (moved from install).

## 0.1.33

- **New:** `cnavTypeHierarchy` task / `cnav:type-hierarchy` goal — show the full type hierarchy for classes matching a pattern. Walks supertypes recursively upward (superclass chain + interfaces) and shows implementors downward via `InterfaceRegistry`. Parameters: `-Ppattern=<regex>` (required), `-Pprojectonly=true|false` (optional, default false). Supports TEXT, JSON, and LLM output formats. Filters `java.lang.Object` from the supertype chain.
- **New:** `ParamType` refactored to sealed class with generics — `ParamType<T>` variants carry their own parse lambdas, enabling type-safe `ParamDef<T>.parse()` returning the correct type directly.

## 0.1.32

- **New:** `cnavFindStringConstant` task / `cnav:find-string-constant` goal — search string literals embedded in bytecode via ASM's `visitLdcInsn()`. Parameters: `-Ppattern=<regex>` (required). Finds URL paths, HTTP headers, config keys, SQL fragments, and other compile-time string constants. Supports TEXT, JSON, and LLM output formats.
- **New:** `cnavDead` confidence scoring — dead code results now include a confidence level: **high** (unreferenced everywhere), **medium** (only referenced in test code), **low** (class/method has framework annotations suggesting reflection/DI usage). Confidence shown in all three output formats.
- **New:** `cnavDead -Pexclude-annotated=<annotations>` — exclude classes and methods with specific annotations from dead code results. Accepts comma-separated annotation simple names (e.g., `-Pexclude-annotated=Scheduled,EventListener`). More precise than regex-based `-Pexclude` for framework entry points.
- **New:** Annotation parameter completeness in `cnavClass` — enum parameters (e.g., `@Retention(RUNTIME)`), array parameters (e.g., `@RequestMapping(value=[/api, /v2])`), and nested annotation parameters are now captured and displayed. Previously only simple values (String, int, boolean) were shown.
- **New:** `ParamType` enum (`STRING`, `LIST_STRING`) on `ParamDef` — centralizes comma-separated list parsing for parameters that accept multiple values.

## 0.1.31

- **New:** Show annotations in `cnavClass` output — class-level, method-level, and field-level annotations are now extracted from bytecode and displayed in all three output formats (TEXT, LLM, JSON). Annotation parameters with simple values (String, int, boolean, etc.) are included. Spring annotations like `@Service`, `@Transactional`, `@CircuitBreaker` are now visible without reading source files.
- **New:** Centralized fuzzy/short-name matching — added `enhancePattern` flag to `ParamDef` so pattern enhancement (camelCase-aware, short-name matching) is applied automatically for marked parameters. `cnavUsages -Ptype` and `-PownerClass` now support fuzzy matching, consistent with other tasks. Eliminates the need to first run `cnavFindClass` to resolve FQNs.
- **Refactoring:** `PatternEnhancer.enhance()` calls removed from 5 individual `Config.parse()` methods — enhancement is now handled centrally in `TaskDef.enhanceProperties()` and `GradleSupport.buildPropertyMap(TaskDef)`.

## 0.1.30

- **New:** Claude Code permission rule guidance added to README and `cnavAgentHelp` install section — explains how to auto-approve cnav Bash commands with wildcard permission rules for both Gradle and Maven.

## 0.1.29

- **New:** Progressive section loading for `cnavAgentHelp` — split monolithic output (~330 lines) into on-demand sections via `-Psection=<name>`. Default output is now a compact task-selection guide (~150 lines). Available sections: `install` (AGENTS.md snippet), `workflow` (step-by-step analysis), `interpretation` (result heuristics), `schemas` (JSON output schemas), `extraction` (output extraction, jq examples).
- **New:** `cnavHelp` output now includes a hint for AI coding agents to run `cnavAgentHelp`.
- **Fix:** Simplified `sed` extraction examples — removed `2>/dev/null` and arcane sed patterns that triggered agent approval prompts.

## 0.1.28

- **Lower JDK requirement from 21 to 17** — the plugin now targets JDK 17 bytecode, making it usable on projects that require JDK 17. Still analyzes bytecode up to Java 24 (ASM 9.9.1). Gradle 9.x (which requires JDK 17+) is still required.
- **Refactoring:** Move `OutputFormat` to new `config` package — breaks cyclic package dependency between root, `navigation`, and `analysis` packages (S1)
- **Refactoring:** Remove resolution logic from `JsonFormatter.formatCallTree` — eliminates mixing of `CallTreeBuilder.build()` resolution with formatting, enforcing the parsing/resolution/formatting separation principle (S3)
- **Refactoring:** Consolidate cache classes into generic `FileCache<T>` — unified `ClassIndexCache`, `SymbolIndexCache`, `InterfaceRegistryCache`, `CallGraphCache` under a shared abstract base with `isFresh()`, `getOrBuild()`, and `FIELD_SEPARATOR` (S4)
- **Refactoring:** Consolidate duplicated methods across extractors — moved `isAccessorForField`, `isExcludedMethod`, `KOTLIN_ACCESSOR`, `EXCLUDED_FIELDS` into `KotlinMethodFilter` (S5)
- **Refactoring:** Delete dead code — removed unused `CalleeTreeFormatter` and `CallerTreeFormatter` wrapper classes (S2)
- **New:** Result Interpretation section in `cnavAgentHelp` output — heuristics for fan-in, fan-out, dead code, change coupling, and hotspots
- **Tests:** Added bytecode version test for Java 24 to verify reading newest class files

## 0.1.27

- **Fix:** `cnavUsages` simple name matching — owner class and type references now match correctly when using simple class names (#70)
- **Refactoring:** Extract filter composition to `CallGraphConfig.buildFilter()` — eliminates duplicated filter-building logic across callers/callees tasks (Gradle + Maven)
- **Refactoring:** Consolidate all parsing and interpretation logic into `ClassName` and `PackageName` domain types — eliminates `.value` access across 27 production files. Added `startsWith(PackageName)`, `topLevelClass()`, `collapseLambda()`, `isSynthetic()`, `fromInternal()`, `isSyntheticName()`, `matches()`, `packagePath()` to `ClassName`; added `matches()`, `contains()`, `depth()`, `isChildOf()`, `splitSegments()` to `PackageName`; changed `SymbolInfo.className` from `String` to `ClassName`
- **Refactoring:** Extract shared test utilities (`TestClassWriter`, `TestCallGraphBuilder`) from 13 test files, removing ~900 lines of test duplication
- **Refactoring:** Add `MethodRef.isGenerated()` delegating to `KotlinMethodFilter` — centralizes generated-method detection
- **Fix:** DSM formatter display — package prefix no longer doubled in output
- **Tests:** Added tests for `ClassDetailScanner`, `ClassDetailExtractor`, `DependencyCollector`, `ChangeCouplingFormatter`, `CallGraphConfig.buildFilter()` — coverage improvements across multiple modules

## 0.1.26

- **New:** `-Pfield=<name>` parameter for `cnavUsages` — find all reads/writes of a field or Kotlin property. Matches direct field access (GETFIELD/PUTFIELD) and property accessor calls (`get<Field>`, `set<Field>`, `is<Field>`). Requires `ownerClass`, mutually exclusive with `method`.
- **Fix:** Gradle and Maven error messages now show the specific validation error (e.g. "Cannot specify both 'field' and 'method'") instead of a generic "Missing required property" message.

## 0.1.25

- **New:** Line numbers in `cnavCallers`/`cnavCallees` output — extracted from bytecode line number tables and shown in all three formats: TEXT `(File.kt:42)`, LLM `File.kt:42`, JSON `"lineNumber":42`. Cached in a backward-compatible `[LINES]` section.
- **Fix:** `-Pformat=llm` now correctly selects LLM output format — previously only the boolean `-Pllm=true` flag worked, while the string `format` parameter was ignored.
- **Fix:** Root node source file resolution for inner classes in `cnavCallers`/`cnavCallees` — property expansion now correctly handles inner class boundaries.

## 0.1.24

- **Fix:** Resolve `<unknown>` source file locations for Kotlin inner classes, lambdas, and companion objects in `cnavCallers`/`cnavCallees` output — progressively strips `$` suffixes to find the outer class source file
- **New:** Kotlin-aware property name resolution — `cnavCallers -Pmethod=accountNumber` automatically expands to `getAccountNumber`/`setAccountNumber`/`isAccountNumber` when no direct match is found
- **New:** Filter synthetic/generated methods from `cnavCallers`/`cnavCallees` — `-Pfilter-synthetic=true` (default) hides `equals`, `hashCode`, `copy`, `componentN`, constructors, and other compiler-generated methods
- **New:** "Common Questions → Which Task" section in `cnavAgentHelp` output — maps natural-language questions to the correct task and parameters for better discoverability

## 0.1.23

- Add `cnavMetrics` task / `cnav:metrics` goal — quick project health snapshot combining bytecode and git analysis. Shows total classes, package count, average fan-in/fan-out, cycle count (Tarjan SCC), dead code counts, and top hotspots.
- Add `cnavCycles` task / `cnav:cycles` goal — true multi-node dependency cycle detection using Tarjan's strongly connected components algorithm. Parameters: `-Proot-package=<pkg>`, `-Pdsm-depth=<N>`.
- Add `top` parameter to `cnavCoupling` and `cnavComplexity` to limit result count
- Add `PatternEnhancer` for camel-case-aware pattern matching — e.g. `Service` now matches `MyServiceImpl`
- Default `maxdepth` to 3 for `cnavCallers` and `cnavCallees` (was required)
- Make `cnavComplexity` work without `-Pclassname` — defaults to showing all project classes sorted by fan-out descending
- **Refactoring:** Introduce `ClassName` and `PackageName` value classes throughout the codebase for type-safe identifiers
- **Refactoring:** Collapse Kotlin lambda inner classes (`$`-containing) in complexity and rank output
- **Refactoring:** Generate help text (AgentHelpText, HelpText) from `TaskRegistry` to prevent parameter drift — all parameter documentation is now data-driven
- **Fix:** `cnavCycles` no longer reads ambient Gradle `depth` property — renamed to `dsm-depth`
- **Fix:** `cnavMetrics` cycle count now uses Tarjan SCC and resolves `rootPackage` from extension config
- **Fix:** Filter Kotlin `$default` and `$$forInline` synthetic methods from dead code results

## 0.1.22

- Add `cnavComplexity` task / `cnav:complexity` goal — analyzes class complexity via fan-in (incoming calls) and fan-out (outgoing calls). Parameters: `-Pclassname=<pattern>` (filter by class), `-Ptop=N` (default 50), `-Pprojectonly=true|false` (default true). TEXT, JSON, and LLM output formats.
- Add `classes-only` mode to `cnavDead` — when `-Pclasses-only=true`, reports only dead classes (no individual methods). Useful for a high-level overview.
- **Noise reduction:** `cnavDead` now filters Kotlin compiler-generated noise from results:
  - Coroutine inner classes (`$`-containing class names)
  - Data class boilerplate (`copy`, `copy$default`, `equals`, `hashCode`, `toString`, `componentN`)
  - Name-mangled copy methods for inline value class parameters (`copy-<hash>`, `copy-<hash>$default`)
  - Inline value class methods (`box-impl`, `unbox-impl`, `equals-impl`, `hashCode-impl`, `toString-impl`, `constructor-impl`)
  - Bridge/synthetic methods (`access$*`, lambda methods)
  - Constructors/initializers (`<init>`, `<clinit>`)
  - Enum boilerplate (`$values`, `valueOf`, `values`)
  - Entry points (`main`)
- **Noise reduction:** `cnavComplexity` now filters `$`-containing generated inner classes from pattern matching
- **Fix:** Rename `cnavComplexity` parameter from `-Pclass` to `-Pclassname` to avoid Gradle built-in property collision
- Refactor Gradle tasks and Maven mojos to use central parameter registry (`ParamDef`, `TaskDef`, `TaskRegistry`) — single source of truth for parameter definitions across both build tools

## 0.1.21

- Add `cnavRank` task / `cnav:rank` goal — ranks types by structural importance using PageRank on the call graph. Types called by many important types score higher. Includes inDegree and outDegree counts. Parameters: `-Ptop=N` (default 50), `-Pprojectonly=true|false` (default true). TEXT, JSON, and LLM output formats.
- **Fix:** `cnavUsages` now deduplicates results — the same usage site is no longer reported multiple times
- **Fix:** DSM HTML output path (`-Pdsm-html` / `-Ddsm-html`) now resolves relative to the project directory instead of the working directory

## 0.1.20

- **Fix:** Rename `-Powner` to `-PownerClass` (Gradle) / `-DownerClass` (Maven) in `cnavUsages` / `cnav:find-usages`. The old `-Powner` parameter collided with Gradle's built-in `owner` property, causing the value to be silently ignored.

## 0.1.19

- Add `-Pcycles=true` parameter to `cnavDsm` / `cnav:dsm` — outputs only cyclic dependencies with class-level edges in both directions, skipping the full DSM matrix. Supports TEXT, JSON, and LLM formats.
- Make `-Ptype` in `cnavUsages` comprehensive — now also matches method call and field instruction owners, so `-Ptype=ContextKt` finds calls to `ContextKt.locateResourceFile()`. Empty results now show guidance suggesting FQN checks and alternative parameters.

## 0.1.18

- Add `cnavUsages` task / `cnav:find-usages` goal — bytecode-based search for project references to external types and methods
  - Three usage kinds: METHOD_CALL (visitMethodInsn), FIELD_ACCESS (visitFieldInsn), TYPE_REFERENCE (visitTypeInsn + descriptor parsing)
  - Parameters: `-Powner=<class>` (FQN of type), `-Pmethod=<name>` (specific method), `-Ptype=<class>` (all type references)
  - Owner-aware matching — distinguishes same-named methods on different types
  - Finds Kotlin property accessors by bytecode name (e.g., `getMonthNumber` for `.monthNumber`)
  - TEXT, JSON, and LLM output formats
- Update help texts (HelpText, ConfigHelpText, AgentHelpText) with `cnavUsages` documentation, migration workflow guidance, and JSON schema

## 0.1.17

- Improve agent help text: lead with one-shot accuracy benefit, add "When to Use What" decision guide

## 0.1.16

- Fix: set JVM toolchain to 21 so the published plugin works on Java 21+ (0.1.14 was accidentally compiled targeting JVM 25)
- Maven navigation goals now auto-compile before running (`@Execute(phase=COMPILE)`), so `mvn compile` is no longer needed as a separate step

## 0.1.14

- Gracefully handle unsupported bytecode versions instead of crashing — classes compiled for a newer JVM than the plugin supports are skipped with a summary warning, and details written to `build/cnav/skipped-files.txt`
- Upgrade ASM from 9.7.1 to 9.9.1 for Java 25 class file support
- Upgrade build JDK to 25 (bytecode target remains 21, so the plugin still works on JDK 21+)
- Update dependencies: Gradle plugin-publish 2.1.1, Kotlin 2.2.0 (Maven), maven-surefire 3.5.5, maven-gpg 3.2.8

## 0.1.13

- Add `codeNavigator {}` Gradle config block for persistent project defaults (no more repeating `-P` flags)
- Add `rootPackage` config property — scopes DSM analysis to a package prefix (default: `""`, all packages)
- `-P` flags still override config block values
- Document Maven `<configuration>` block for equivalent persistent defaults
- Reorganize README Getting Started section to top

## 0.1.12

- Fix: Maven help text showed incorrect goal name `cnav:help-config` instead of `cnav:config-help`
- Add missing `cnavAgentHelp` / `cnav:agent-help` assertions to HelpTextTest
- Add backward-compatibility default-parameter tests for HelpText and ConfigHelpText
- Add `agent-help` and `config-help` sections to README task reference

## 0.1.11

- Add `BuildTool` enum for build-tool-aware help text — Gradle users see `./gradlew cnavXxx -Pparam=value`, Maven users see `mvn cnav:goal -Dparam=value`
- Make `HelpText`, `AgentHelpText`, and `ConfigHelpText` accept a `BuildTool` parameter
- Gradle tasks and Maven Mojos now pass the correct build tool for contextual help output
- Set Maven plugin `goalPrefix` to `cnav` (previously derived as `code-navigator`)
- Add `test-project-maven/` for end-to-end Maven plugin testing
- Add Maven examples alongside Gradle in README.md Tasks section

## 0.1.10

- Add Maven plugin (`code-navigator-maven-plugin`) with full feature parity — all 17 goals available via `mvn cnav:<goal>`
- Restructure source layout to separate roots: `src/core/kotlin/` (shared), `src/gradle/kotlin/`, `src/maven/kotlin/`
- Extract shared Config data classes for all tasks (used by both Gradle and Maven parameter parsing)
- Extract `ClassDetailScanner` from `FindClassDetailTask` for reuse across build tools
- Configure Maven Central publishing with GPG signing, source jars, and Dokka javadoc
- Update release process in AGENTS.md for dual Gradle + Maven publishing

## 0.1.9

- Fix: cnavDsm returning empty results — rename `-Pdepth` to `-Pdsm-depth` to avoid Gradle built-in property collision
- Fix: stale `-Pdepth` references in README for cnavCallers/cnavCallees (should be `-Pmaxdepth`)
- Add integration test for DsmDependencyExtractor against real compiled Kotlin classes

## 0.1.8

- Port DSM (Dependency Structure Matrix) from dsm-plugin into `navigation` package
  - Bytecode scanning with `DsmDependencyExtractor` (ASM-based)
  - `DsmMatrixBuilder` with cyclic dependency detection
  - Text, HTML, JSON, and LLM output formats
  - `cnavDsm` task with `-Proot-package=`, `-Pdsm-depth=`, `-Pdsm-html=` properties
- Enable git rename tracking by default (`-M` flag), opt out with `-Pno-follow`
  - `GitLogParser` handles both full-path and brace rename syntax
- Add `cnavHelpConfig` task listing all `-P` configuration parameters with defaults
- Update HelpText, AgentHelpText, README, and AGENTS.md

## 0.1.7

- Add 5 git history analysis tasks (no compilation required):
  - `cnavHotspots` — files ranked by revision count and churn
  - `cnavCoupling` — temporal coupling between files
  - `cnavAge` — time since last change per file
  - `cnavAuthors` — distinct contributors per file
  - `cnavChurn` — lines added/deleted per file
- Add shared git infrastructure: `GitLogParser`, `GitLogRunner`
- Reorganize codebase into `navigation` (bytecode) and `analysis` (git history) subpackages
- Update JSON and LLM formatters with analysis output support
- Update `cnavHelp` and `cnavAgentHelp` with git task documentation

## 0.1.6

- Update README with `cnavAgentHelp` as primary agent entry point
- Add skill mention: agentHelp output can be used as starting point for a custom agent skill
- Add Maven plugin support to plan.md

## 0.1.5

- Add compact LLM output format (`-Pllm=true`) for token-efficient output across all tasks
- Wrap output with `---CNAV_BEGIN---` / `---CNAV_END---` markers for reliable extraction from Gradle stdout

## 0.1.4

- Wrap JSON output with markers to separate it from Gradle lifecycle noise

## 0.1.3

- Update AGENTS.md release process to include Gradle Plugin Portal publishing

## 0.1.2

- Add JSON output format (`-Pformat=json`) for all tasks
- Add `cnavAgentHelp` task with workflow guidance, JSON schemas, and jq examples
- Add `-Pprojectonly=true` flag to filter stdlib/JDK noise in cnavCallers, cnavCallees, cnavDeps
- Add reverse dependency view (`-Preverse=true`) for cnavDeps
- Add test source set support (`-Pincludetest=true`) for cnavInterfaces
- Fix: rename `-Pdepth` to `-Pmaxdepth` to avoid Gradle built-in property collision
- Refactor: extract CallTreeBuilder to separate tree resolution from formatting
- Add AGENTS.md with code structure principles
- Add plan.md with feature roadmap

## 0.1.1

- Refactor cache layer: atomic writes, corruption safety, shared freshness checking
- Add disk caching for call graph, symbol index, and interface registry
