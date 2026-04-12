# Changelog

## 0.1.61

- **New:** `-Pjar=<path-or-artifact>` parameter for `cnavListClasses`, `cnavFindClass`, `cnavClassDetail`, and `cnavFindSymbol`. Scans classes from a JAR file instead of project classes. Supports file paths (`-Pjar=/path/to/lib.jar`) and artifact coordinates (`-Pjar=com.example:library`) resolved from the project's `runtimeClasspath`. When `-Pjar` is set, `prod-only`/`test-only` filters are ignored.
- **New:** `-Ppattern` filter added to `cnavListClasses` — previously the only bytecode inspection task without a pattern filter.
- **New:** `ByteArray` overloads for `ClassInfoExtractor.extract()`, `ClassDetailExtractor.extract()`, and `SymbolExtractor.extract()` — enables extraction from in-memory class bytes (e.g. JAR entries) without writing to disk.
- **New:** `JarClassScanner` — reads `.class` entries from JAR files, returning entry name, raw bytes, and a label for error messages.
- **New:** `GradleSupport.resolveJar()` — resolves `-Pjar` values as either file paths or artifact coordinates via Gradle's `runtimeClasspath.resolvedConfiguration.resolvedArtifacts`.

## 0.1.60

- **Fixed:** Dead code detection no longer reports inherited/framework methods as dead. Methods like Exposed ORM's `Column.nullable()` were falsely attributed to the calling table class because the JVM bytecode dispatches on the subclass type. `CallGraphBuilder` now tracks which methods are actually declared in each class, and `DeadCodeFinder` uses this to filter out inherited methods. Fixes false positives on projects using Exposed, Spring Data, and similar frameworks with fluent builder APIs.
- **Fixed:** `cnavFindUsages` now detects usages through INVOKEDYNAMIC instructions (lambdas and method references). Previously, lambda-based usages like `{ rowToPoll(it) }` and Java-style method references like `Service::process` were invisible to the usage scanner.
- **Fixed:** `cnavDsm` / `cnavCycles` / `cnavPackageDeps` now detect dependencies through INVOKEDYNAMIC instructions. Lambda and method reference edges were previously missing from package dependency analysis.
- **Refactoring:** Rewriter tests (`MoveClassRewriterTest`, `RenameMethodRewriterTest`, `RenamePropertyRewriterTest`, `RenameParamRewriterTest`) now cache `ParsedSources` across test methods for faster execution. Extracted shared `ParsedSources` data class and `parseKotlinSources()` into `RewriterSupport`.

## 0.1.59

- **New:** `cnavRenameProperty` task / `cnav:rename-property` goal — renames a Kotlin property (including `val`/`var` constructor parameters) and updates all access sites: constructor named arguments, `copy()` named arguments, direct property access, and getter/setter calls. Parameters: `-Ptarget-class=<FQN>` (required), `-Pproperty=<name>` (required), `-PnewName=<name>` (required), `-Ppreview` (dry-run mode). Both Gradle and Maven support. TEXT, JSON, and LLM output formats.
- **Improved:** `cnavRenameParam` constructor `val`/`var` warning now points users to `cnavRenameProperty` instead of suggesting manual update or IDE refactoring.
- **New:** `cnavMoveClass` now supports class renaming via optional `-Pnew-name=<name>` parameter. Can move, rename, or both in a single operation. `-Pnew-package` is now optional when `-Pnew-name` is specified.
- **Breaking:** `cnavMoveClass` API simplified — replaced `-Ptarget-class`, `-Pnew-package`, and `-Pnew-name` with `-Pfrom=<FQN>` and `-Pto=<FQN>`. The `to` parameter is a fully qualified class name; package and simple name are derived from it. JSON output keys changed: `className` → `from`, `newPackage` → `to`, `newName` removed. Other rename tasks (`cnavRenameParam`, `cnavRenameMethod`, `cnavRenameProperty`) are unchanged.

## 0.1.58

- **New:** Companion object support in `cnavRenameMethod` and `cnavRenameParam` — both rename rewriters now match companion object methods when the user specifies the outer class FQN. Shared `matchesClassOrCompanion()` helper handles both `Foo.Companion` and `Foo$Companion` FQN forms.
- **New:** Constructor `val`/`var` parameter warning in `cnavRenameParam` — detects when a renamed parameter is a constructor property and emits a warning explaining that property access sites need manual updating (full property rename deferred to future `cnavRenameProperty` task).
- **New:** `RenameResult` now carries `warnings: List<String>` field, serialized in JSON output and displayed in all three formats (TEXT, JSON, LLM).
- **Improved:** Refactoring recommendation messages across all three formatters (`cnavRenameParam`, `cnavRenameMethod`, `cnavMoveClass`) now warn that "refactorings are not always fully accurate" and recommend compiling to verify.
- **Improved:** `cnavMoveClass` added to the README Refactoring task table.

## 0.1.57

- **Fixed:** `cnavMoveClass` now correctly moves interfaces, objects, enums, and sealed classes — not just classes. The file detection used content-based matching (`class $name`) which missed all non-class types. Replaced with deterministic path-based detection.
- **Fixed:** File move operation now uses `Files.move()` instead of `File.renameTo()`, which could silently fail (e.g. cross-device moves). Failures now throw an exception.

## 0.1.56

- **New:** `cnavMoveClass` task / `cnav:move-class` goal — moves a Kotlin class to a different package and updates all references project-wide using OpenRewrite's `ChangeType` recipe with classpath-based type resolution. Handles package declaration, import updates, and type references (fields, parameters, return types, generics). File is relocated to the new package directory. Parameters: `-Ptarget-class=<FQN>` (required), `-Pnew-package=<pkg>` (required), `-Ppreview` (dry-run mode). Requires compilation (`requiresCompilation=true`). Both Gradle and Maven support. TEXT, JSON, and LLM output formats.
- **Improved:** `cnavAgentHelp` install and compact sections now emphasize cnav over grep for code navigation.
- **Improved:** `cnavAgentHelp` recommendations section added with guidance on coverage, TDD, commit cadence, and architecture conformance.

## 0.1.55

- **New:** `cnavRenameMethod` task / `cnav:rename-method` goal — renames a method/function across a Kotlin codebase using OpenRewrite. Finds and updates the method declaration, all call sites, and interface/superclass implementor declarations (interface dispatch). Parameters: `-Ptarget-class=<FQN>` (required), `-Pmethod=<name>` (required), `-PnewName=<name>` (required), `-Ppreview` (dry-run mode). Both Gradle and Maven support. TEXT, JSON, and LLM output formats.
- **Refactoring:** Extracted shared rewriter infrastructure from `cnavRenameParam` and `cnavRenameMethod` — `DiffSupport.kt` (unified diff computation), `RewriterSupport.kt` (source file collection and path resolution), `JsonSupport.kt` (change serialization). Both rewriters now share common code.
- **Refactoring:** Renamed `apply` parameter to `preview` (inverted semantics) across both rewriter tasks — default is now apply mode, `-Ppreview` opts into dry-run.
- **Refactoring:** Extracted shared test fixtures (`FormatterTestFixtures.kt`) with 14 object mother functions, reducing ~224 lines of duplicated test data construction across `JsonFormatterTest`, `LlmFormatterTest`, and `ContextFormatterTest`.
- **Refactoring:** Extracted `deadClassNames()`/`deadMethodNames()`/`deadClasses()`/`deadMethods()` helper functions in `DeadCodeFinderTest`, replacing ~40 repeated filter/map expressions.
- **Improved:** Rewriter test suite runs 87% faster by eliminating Gradle subprocess calls and using preview mode assertions.

## 0.1.54

- **New:** `cnavRenameParam` task / `cnav:rename-param` goal — renames a method/function parameter across a Kotlin codebase using OpenRewrite. Detects cascade candidates where a renamed parameter is forwarded to another method with a same-named parameter, suggesting the user consider renaming there too. Supports preview mode (`-Ppreview`). Gradle task uses classloader isolation for OpenRewrite. TEXT, JSON, and LLM output formats.

## 0.1.53

- **New:** `cnavLayerCheck` task / `cnav:layer-check` goal — architecture conformance checking based on hexagonal architecture principles. Layers are defined by class naming patterns (globs like `*Controller`, `*Service`, `*Repository`, `*`) in a `.cnav-layers.json` config file, not by listing packages. First matching pattern wins, enabling enforcement in projects organized by feature. Detects two violation types: OUTWARD (class depends on a higher/outer layer) and PEER (class exceeds `peerLimit` for same-layer dependencies). Supports `peerLimit` per layer (`-1` = unlimited, default `0` = no peer deps), `testInfrastructure` flag (allows test classes to depend on wiring/context layers without OUTWARD violations), and `-Pinit=true` to generate a starter config. TEXT, JSON, and LLM output formats with non-zero exit code on violations.
- **New:** `testInfrastructure` attribute on layer config — when `true`, test classes (names ending in `Test` or `TestKt`) are allowed to depend on that layer without generating OUTWARD violations. Production code depending on a testInfrastructure layer is still a violation.
- **New:** `cnavSize` task / `cnav:size` goal — lists source files (Kotlin/Java) by line count, largest first. First source-level task: scans source files directly, no compilation needed. Parameters: `-Pover=N` (minimum line count), `-Ptop=N` (default 50). Includes "Consider splitting" recommendation when largest file exceeds 3× median. TEXT, JSON, and LLM output formats.
- **New:** `SOURCE` TaskCategory for tasks that analyze source files without compilation.
- **Improved:** `ClassName` now hosts `candidateNames()`, `isTest()`, and `STRIPPABLE_SUFFIXES` — moved from `LayerConfig.companion` for reuse. `Kt` and `Test` suffixes are stripped recursively before pattern matching.
- **Improved:** `SimpleJson` parser supports boolean values (`true`/`false` literals) for config parsing.
- **Improved:** AgentHelpText troubleshooting section and import/dependency question added.

## 0.1.52

- **New:** Terse recommendations in analysis formatters — short, actionable one-liners appended to output when thresholds are exceeded.
  - **Cycles**: "Extract shared types into a new package or invert one dependency direction."
  - **Complexity**: Flags high fan-out (>10 distinct outgoing classes) and high fan-in (>20 distinct incoming classes).
  - **Change coupling**: Flags degree >=70% with merge/extract suggestion. Suppresses test+main pairs (expected to co-change).
  - **Hotspots**: Flags files with revisions >=2× median (minimum 5 files in dataset).
- **Improved:** README revised — streamlined Getting Started with copy-paste agent prompt, added 6 missing tasks to task table, reorganized into 5 groups.

## 0.1.51

- **New:** `cnavVolatility` task / `cnav:volatility` goal — aggregates file-level git history to the package level. Shows which packages change most often, a key dimension of Balanced Coupling analysis. Parameters: `-Pafter=YYYY-MM-DD`, `-Pmin-revs=N`, `-Ptop=N`. TEXT, JSON, and LLM output formats.
- **New:** `cnavBalance` task / `cnav:balance` goal — composite balanced coupling analysis combining strength × distance × volatility into a single verdict per package pair. Verdicts: BALANCED, TOLERABLE, OVER_ENGINEERED, DANGER. Requires both compiled code and git history. Parameters: `-Ppackage-filter=<regex>`, `-Ptop=N`, `-Pafter=YYYY-MM-DD`, `-Pmin-revs=N`.
- **New:** `COMPOSITE` TaskCategory — distinguishes aggregated/composite analysis tasks (e.g. `balance`) from base analyses. `balance` reclassified from HYBRID to COMPOSITE.
- **Improved:** Help text and agent help text now include a "Useful Combinations" section suggesting task combos for refactoring targets, architecture overview, change risk, and code review context.
- **Improved:** Composite goals (e.g. `balance`) now list their base goals in parentheses in help text.
- **Improved:** ConfigHelpText includes a "Composite Analysis" section for COMPOSITE category tasks.

## 0.1.50

- **Refactoring:** Extracted `registry` package from root — `TaskRegistry`, `BuildTool`, `CacheFreshness` moved to `no.f12.codenavigator.registry`.
- **Refactoring:** Extracted `formatting` package from root — `LlmFormatter`, `JsonFormatter`, `TableFormatter`, `OutputWrapper` moved to `no.f12.codenavigator.formatting`.
- **Refactoring:** Extracted `navigation.core` package — `DomainTypes`, `BytecodeReader`, `FileCache`, `KotlinMethodFilter`, `AnnotationParameterCollector`, `LambdaCollapser`, `PatternEnhancer`, `ProjectClassScanner`, `RootPackageDetector`, `SkippedFileReporter`, `SourceSetResolver` moved to `no.f12.codenavigator.navigation.core`.
- **Improved:** Package structure — broke the 17-package mega-cycle into two smaller independent cycles (5-package and 7-package). Root package now contains only help text files.

## 0.1.49

- **New:** `cnavDistance` task / `cnav:distance` goal — measures structural distance between packages using shortest path in the dependency graph. Parameters: `-Ppackage-filter=<regex>` (scope), `-Ptop=N` (limit, default unlimited). TEXT, JSON, and LLM output formats with `displayPrefix` stripping.
- **New:** `cnavStrength` task / `cnav:strength` goal — classifies integration strength between packages using Balanced Coupling heuristics. Categories: CONTRACT (interfaces/abstractions), MODEL (data/entity classes), FUNCTIONAL (direct logic coupling), UNKNOWN (external/unclassifiable). Parameters: `-Ppackage-filter=<regex>`, `-Ptop=N` (default unlimited). TEXT, JSON, and LLM output formats.
- **New:** Framework entry point hints in `cnavFindCallers` — when a method annotated with framework annotations (e.g. `@GetMapping`, `@Test`, `@Scheduled`) shows `(no callers)`, an inline hint is appended explaining it is a framework entry point invoked at runtime. Prevents AI agents from misinterpreting framework entry points as dead code. Supported in all three output formats (TEXT, LLM, JSON).
- **Improved:** LLM formatter now shows `(no callers)` / `(no callees)` messages for root nodes with empty children, matching TEXT format for consistency.
- **Improved:** JPA-annotated classes (`@Entity`, `@Table`, `@MappedSuperclass`, etc.) are now classified as MODEL in integration strength analysis via `FrameworkPresets`.
- **Improved:** Class name stripping — all formatters (TEXT, LLM, JSON) that use `displayPrefix` now also strip class names, not just package names. Applies to DSM, cycles, and DSM cycles output.
- **Improved:** Interpretation heuristics added to `cnavAgentHelp` for DSM, cycles, distance, rank, code-age, churn, authors, and metrics tasks.
- **Improved:** Distance and Strength tasks default `top` to unlimited (show all results). Other tasks retain `top=50` default. Help texts now show task-specific defaults.
- **Improved:** Source-only extraction filtering for Distance/Strength — only project source classes are analyzed, excluding test and external dependencies.
- **Fixed:** Unclassifiable external targets in strength analysis now tracked as UNKNOWN instead of incorrectly defaulting to FUNCTIONAL.
- **Fixed:** Package-filter bugs in `cnavDistance` and `cnavStrength` — filter now correctly scopes both source and target packages.
- **Fixed:** `top=0` now rejected with a clear error message instead of silently returning empty results.
- **Fixed:** Override tasks excluded from base task list in `cnavAgentHelp` global params section.

## 0.1.48

- **Fixed:** `cnavMetrics` dead code count now matches `cnavDead`. Previously, `MetricsTask`/`MetricsMojo` called `DeadCodeFinder.find()` without passing delegation methods, bridge methods, interface implementors, class fields, inline methods, external interfaces, or receiver types — producing inflated dead code counts (e.g. 46 vs 7).
- **New:** DSM and package dependency tasks now show explanatory hints when there are no inter-package dependencies. Single-package projects get a clear message instead of bare "no dependencies found."
- **New:** Git-history tasks (`cnavChurn`, `cnavHotspots`, `cnavCodeAge`, `cnavAuthors`, `cnavCoupling`) now automatically exclude build output directories (`build/`, `target/`, `bin/`, `.gradle/`, `out/`, `node_modules/`) from analysis results.
- **Improved:** Deprecated `method` parameter message now specifies it applies to find-callers/find-callees and shows both Gradle and Maven syntax.

## 0.1.47

- **Fixed:** Polymorphic dispatch resolution now runs inside the BFS alongside intra-class call propagation. Previously, methods discovered via intra-class calls (e.g. abstract method dispatched from `this.singleMatch()`) were never resolved to implementor overrides, causing false positive dead code reports. This was the #1 source of false positives in field testing.
- **Fixed:** Inner class usage now propagates liveness to enclosing classes. `TokenError$ExitException` being used no longer leaves `TokenError` flagged as dead.
- **New:** `DelegationMethodDetector` filters Kotlin delegation-generated methods from dead code results. Compares bytecode methods against Kotlin metadata to identify compiler-generated forwarding methods (e.g. `clear`, `put`, `remove` on a class delegating `Map`).
- **New:** `BridgeMethodDetector` filters JVM bridge methods (`ACC_BRIDGE`) from dead code results. Bridge methods are generated for type erasure and covariant return types and are never meaningful dead code candidates.

## 0.1.46

- **Breaking:** Renamed `exclude-framework` parameter to `treat-as-dead` with clearer semantics. Framework presets now explicitly mark matching entry points as dead code candidates rather than excluding them from analysis.
- **New:** Unified source set model — all bytecode tasks now support `-Pprod-only=true` / `-Ptest-only=true` for filtering by source set. The old `-Pinclude-test` parameter is deprecated. Source set tags (`[prod]`/`[test]`) propagate through all formatters. 26 tasks updated across Gradle and Maven.
- **New:** `SourceSetResolver` maps class names to source sets from tagged directories, enabling per-class source set awareness without separate compilation passes.
- **New:** Receiver-type-based entry point detection for Ktor dead code analysis — extension functions on Ktor types (`Route`, `Application`, `Pipeline`, etc.) are recognized as framework entry points.
- **New:** `testOnly` filter for `cnavDead` — `-Ptest-only=true` shows only dead code items with `reason=TEST_ONLY` (production methods/classes called only from tests).
- **New:** Nimbus JWT interfaces (`JWSKeySelector`, `JWKSource`, `JWSVerifier`, etc.) added to `KTOR_SUPERTYPES` for entry point detection.
- **New:** Unsupported parameter validation — Gradle tasks now fail fast with a clear error when a valid cnav parameter is passed to a task that doesn't support it (e.g. `-Ptest-only=true` on a task without that parameter). Prevents silent parameter ignoring.
- **Fixed:** InterfaceRegistry superclass tracking — classes implementing interfaces via a superclass chain are now correctly discovered (Bug #13).
- **Fixed:** SymbolFilter source file matching removed — was incorrectly filtering out valid symbols when source file metadata didn't match (Bug #16).
- **Fixed:** Empty-result output consistency — all tasks now produce consistent output format when no results are found (Bug #15).
- **Fixed:** Search matching, dead code test filtering, and CamelCase stopword handling improvements.
- **Improved:** Uniform hint delivery in JSON/LLM output across all tasks.
- **Improved:** `cnavAgentHelp` updated with no-results hint suggesting `-Pmethods=true` and retention policy for annotation queries.

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
