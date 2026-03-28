# Plan

## Bytecode analysis improvements

### 73. `cnavDead` framework entry point hints — reduce false positives (High value, medium effort)

From external feedback: 60% of cnavDead candidates were false positives, mainly framework callbacks (Ktor route handlers, Jackson `@JsonCreator`, Exposed DSL). These are called reflectively or by the framework, not from project code directly.

- **Approach**: Support a `.cnav-entry-points` configuration file (or Gradle/Maven DSL) that declares framework entry point patterns:
  ```
  # Annotations that mark entry points (simple names)
  @JsonCreator
  @Route
  @Scheduled
  @EventListener
  @Bean

  # Class patterns that are always entry points
  class:**Routes
  class:**Module
  ```
- **Behavior**: Classes/methods matching these patterns are excluded from dead code results (or forced to LOW confidence).
- **Why**: Cuts false positives significantly for framework-heavy projects without requiring `-Pexclude-annotated` on every invocation.
- **Alternative**: Ship a built-in set of common framework annotations (Spring, Ktor, Jackson, JUnit) that are always treated as LOW confidence, with opt-out.

### 74. `cnavDead` diff-friendly output — confirm cleanup was complete (Medium value, low effort)

From external feedback: after triaging dead code and removing items, it would be useful to re-run `cnavDead` and see "these 10 are now gone."

- **Approach**: `-Pbaseline=<path>` parameter pointing to a saved JSON output from a previous run. On re-run, show a diff: items removed since baseline, items still present, new items.
- **Alternative**: Simpler approach — just saving JSON and using `jq` to diff. But built-in support would be more ergonomic.

### 38. Full classpath scanning option (High value, medium effort)

Most tasks only scan the project's compiled output directories. When checking what is available on the classpath — e.g., verifying a library class's method signatures or finding all implementations of a framework interface — scanning only project code is insufficient.

- **Parameter**: `-Pclasspath=true` — scan the full runtime classpath (project classes + all dependency JARs)
- **Needs**: Gradle's `configurations.runtimeClasspath.resolve()` / Maven's `project.runtimeClasspathElements`
- **Applies to**: `cnavListClasses`, `cnavFindClass`, `cnavFindSymbol`, `cnavClass`, `cnavInterfaces`, `cnavUsages`, potentially `cnavCallers`/`cnavCallees`
- **Considerations**:
  - Classpath scanning is significantly slower (thousands of classes). Consider caching scanned JARs by checksum.
  - Output can be very large. Combine with existing `-Ppattern` / `-Powner` filters to narrow scope.
  - `cnavClass -Pclasspath=true -Ppattern="kotlinx.datetime.LocalDate"` would show the full signature of a library class without needing to look up docs.
- **Why high value**: AI agents frequently need to check library API signatures to write correct code. Classpath scanning gives ground-truth answers from the actual dependency versions in the project.
- **Note**: Item 40 (`cnavFindSymbol` external type references) is largely covered by combining classpath scanning with `cnavUsages -Ptype=<class>` for finding project references to external types. May only need documentation pointing users to `cnavUsages` for this use case.

### 68. `cnavJar` — print methods and signatures from JAR files (High value, medium effort)

Inspect the methods and signatures of classes inside a JAR file, whether or not the JAR is on the project classpath.

```bash
# Scan a JAR on the runtime classpath by artifact coordinates
./gradlew cnavJar -Partifact=com.fasterxml.jackson.core:jackson-databind -Ppattern=ObjectMapper

# Scan an arbitrary JAR file by path
./gradlew cnavJar -Pjar=/path/to/some.jar -Ppattern=SomeClass
```

- **Two modes**:
  1. `-Partifact=<group:name>` — resolve the JAR from the project's runtime classpath by matching group and artifact name. No version needed (uses whatever version the project declares).
  2. `-Pjar=<path>` — scan an arbitrary JAR file by absolute path, regardless of classpath.
- **Parameters**: `-Ppattern=<class-name-filter>` (optional, narrows which classes to show), `-Pformat=text|json|llm`
- **Output**: For each matching class: fully qualified name, declared methods with full signatures (parameter types, return type, visibility), constructors, and static methods.
- **Implementation**: Reuse `ClassDetailExtractor` / `ClassDetailScanner` but feed it entries from a `JarFile` instead of a class directory. For `-Partifact`, resolve the JAR path via Gradle's `configurations.runtimeClasspath.resolvedConfiguration` / Maven's `project.runtimeClasspathElements`.
- **Relationship to item 38**: Item 38 adds `-Pclasspath=true` to existing tasks, scanning all dependency classes into the existing index. This task is different — it's a focused inspection tool for quickly checking "what methods does class X in library Y have?" without indexing the entire classpath.
- **Why high value**: AI agents frequently need to check library API signatures. Looking up docs is slow and sometimes wrong (version mismatch). Bytecode gives ground-truth for the exact version in the project.

### 56. `cnavContext` — smart context gathering for AI agents (High value, medium effort)

AI agents typically need 4-5 sequential tool calls to understand a class. Given a class or method, automatically gather "everything an agent needs": class signature, callers (depth 2), callees (depth 2), interface implementations, and source file path.

```bash
./gradlew cnavContext -Ppattern=ResetPasswordService -Pformat=json
```

- **Builder**: Orchestrates existing `ClassDetailScanner`, `CallTreeBuilder` (callers + callees), and `InterfaceRegistry` into a single result
- **Parameters**: `-Ppattern=<class>` (required), `-Pmaxdepth=N` (default 2), `-Pformat=json|text|llm`
- **Why high value**: Reduces agent round-trips from 4-5 to 1, saving significant wall-clock time
- **Note**: This covers the most common composite query case. A general-purpose `cnavBatch` task (former item 60) may be over-engineering — evaluate after `cnavContext` ships.

### 62. Separate prod/test in output (High value, medium effort)

All bytecode tasks mix production and test callers in a single list. For a class with 167 incoming references, it's hard to tell which are prod dependencies and which are test code.

- **Approach**: Tag each caller/reference with `[test]` or `[prod]` based on which source set the class came from. The `ClassScanner` already receives separate class directories for main vs test source sets — propagate this metadata through to the call graph and formatters.
- **Parameters**: `-Pprod-only=true`, `-Ptest-only=true`. Without either flag, show all with tags.
- **Applies to**: `cnavCallers`, `cnavCallees`, `cnavUsages`, `cnavComplexity`, `cnavDead`, `cnavRank`
- **Implementation**: Add `sourceSet: SourceSet` enum field to `ClassInfo` during scanning. Propagate through call graph to formatters.

### 39+59. `cnavDiff` — structural diff between builds (Medium value, medium effort)

Compare two compiled states and show structural changes. Covers two use cases:

**API signature changes from dependency upgrades (former item 39):** After changing a dependency version, cascading type changes ripple through the codebase. A tool that diffs "before" and "after" API surfaces maps the blast radius instantly.

**General structural diff (former item 59):** Compare before/after a refactoring to show added/removed/changed classes, methods, and dependency edges. Useful for verifying a refactoring was purely structural.

- **Builder**: `StructuralDiff.diff(baselineClassDir, currentClassDir) -> List<Change(className, memberName, kind: ADDED|REMOVED|SIGNATURE_CHANGED, oldSignature?, newSignature?)>`
- **Parameters**: `-Pbaseline=<path>` (path to baseline class directory), `-Paffected=true` (also list affected call sites via `cnavUsages`/`cnavCallers`)
- **Why useful**: Dependency upgrades are a common source of subtle breakage. Bytecode diffing gives the full migration checklist before running tests.

### 46. `cnavTestHealth` — verify all test methods actually ran (High value, medium effort)

From user feedback: a project had 19 silently skipped tests because test methods had non-`Unit` return types. The robust approach is **count-and-verify**: count `@Test`-annotated methods from bytecode, compare against JUnit XML results from the actual test run, flag the delta.

1. **Bytecode scan**: Find all methods annotated with `@Test` (JUnit 4/5, Kotlin Test). This is the "expected" set.
2. **JUnit XML scan**: Parse test result XML files (`build/test-results/test/TEST-*.xml` or `target/surefire-reports/TEST-*.xml`). This is the "actual" set.
3. **Diff**: Report methods present in bytecode but absent from XML results — the silently skipped tests.

- **Lifecycle**: `dependsOn("test")` — runs after tests complete
- **Additional checks** (bytecode-only): test methods missing `@Test` annotation but named `test*`, test classes with no `@Test` methods, `@Disabled`/`@Ignore` inventory
- Both Gradle and Maven write the same JUnit XML format, so one parser handles both.

### 49. `cnavWhyDepends` — dependency edge explanation (High value, medium effort)

The DSM tells you package A depends on package B, but not *why*. To break a cycle you need to know the specific fields, method parameters, return types, and local variable types that create the dependency.

- **Builder**: `DependencyExplainer.explain(callGraph, from, to) -> List<DependencyEdge(sourceClass, targetClass, kind: FIELD|PARAMETER|RETURN_TYPE|LOCAL_VAR|METHOD_CALL, detail: String)>`
- **Parameters**: `-Pfrom=<class-or-package>` (required), `-Pto=<class-or-package>` (required), `-Pprojectonly=true`
- **Why useful**: The missing link between "the DSM says there's a dependency" and "here's what to move/extract to break it."

### 55. `cnavChangedSince` — impact analysis for a branch/commit (Very high value, medium effort)

The most common agent question is "what could this PR break?" Given a git ref, show which classes changed and run `cnavCallers` on all changed methods to show the blast radius.

```bash
./gradlew cnavChangedSince -Pref=main
```

1. `git diff --name-only <ref>..HEAD` to find changed source files
2. Map source files to class names via `ClassScanner` source file metadata
3. For each changed class, find changed methods
4. Run `cnavCallers` on each changed method to find the blast radius

- **Output**: Changed classes with their affected callers, grouped by change type (added/modified/removed)
- **Why high value**: Combines git + bytecode analysis to directly answer the most common code review question.

### 58. `cnavUnused` — unused build dependencies (Medium value, medium effort)

Analyze which declared Gradle/Maven dependencies have zero references in bytecode. Different from dead code — this finds entire libraries that could be removed.

- For each declared dependency JAR, extract the package list. Scan project bytecode for references. Dependencies with zero references are candidates for removal.
- **Caveats**: Runtime-only dependencies (JDBC drivers, logging backends) will show as "unused." Need an exclusion mechanism.
- **Related**: Reuses classpath enumeration infrastructure from item 38.

### 67. DI-aware `cnavInjectors` — find where a type is injected (Medium value, high effort)

Tracing "what injects `BaseAccountRestAdapter`?" requires manually reading constructors. A DI-aware task uses constructor parameter types + framework annotations to answer this.

- Scan all constructors for parameters matching the target type. For Spring, also check `@Autowired` fields and `@Bean` methods.
- Start with constructor injection (framework-agnostic) and `@Autowired`/`@Inject` field injection.
- **Alternative**: May already be partially solvable with `cnavUsages -Ptype=AccountService` which finds all references including constructor parameters.

### 61. Stable JSON schemas — machine-fetchable schema documentation (Low value, low effort)

- Add a `cnavSchema -Ptask=cnavDead` command that outputs the JSON schema for a given task's output
- **Why lower priority**: The JSON output is already self-describing, and agents can infer the schema from one example.

## Architecture / cycle tooling

### 6. `cnavLayerCheck` — architecture conformance (High value, ambitious)

Allow declaring layer rules and validate them against the actual call graph. Like ArchUnit but without writing test code.

```kotlin
codeNavigator {
    layers {
        "domain" dependsOn nothing
        "services" dependsOn "domain"
        "ktor.routes" dependsOn "services", "domain"
    }
}
```

Also support simpler forbidden-dependency rules:

```kotlin
codeNavigator {
    rules {
        "services" mustNotDependOn "ra"
        "domain" mustNotDependOn "ktor"
    }
}
```

Output: list of violations with the specific class-level edges that break the rule.

### 43. DSM "what-if" mode and cycle fix suggestions (High value, medium effort)

The DSM tells you which cycles exist, but not how to fix them.

**Cycle fix suggestions (lower effort, do first):** When `-Pcycles=true`, also show which specific class-level edges would need to move to break the cycle, and suggest which direction the dependency should flow.

**What-if simulation (higher effort):** `-Pwhat-if=<class>:<target-package>` — simulate moving a class to a different package and re-evaluate cycles without actually making the change.

## Git history analysis

All git-history-based analyses share common infrastructure (items 11-16). Items 11-16 are already implemented (see changelog 0.1.7). The following are extensions:

### 51. `cnavMetrics` / `cnavReport` — summary dashboard and full analysis (Medium value, low effort)

`cnavMetrics` is already implemented. `cnavReport` remains:

Run all analysis tasks (both bytecode and git history) and produce a consolidated report. Instead of running tasks individually, `cnavReport` runs them all and outputs a consolidated summary.

- **Parameters**: Inherits from constituent tasks. `-Pformat=json` produces a single JSON object with sections per analysis.
- **Why useful**: Agents often want the full picture. A single task is faster (shared caching, one compilation) and produces a coherent snapshot.

## Internal improvements

### S6. Split root package to clarify dependency direction (Medium value, medium effort)

The root `codenavigator` package serves as both "shared infrastructure" and "library API." Splitting into `codenavigator.format` (formatters + OutputFormat) and `codenavigator.registry` (TaskRegistry, BuildTool) would make the dependency direction explicit.

## Framework awareness

### 76. Meta-annotation traversal for dead code filtering (High value, medium effort)

`@RestController` is meta-annotated with `@Controller` which is meta-annotated with `@Component`. Currently, excluding `Component` does NOT exclude `@RestController` — the tool checks simple annotation names literally, not the annotation hierarchy.

- **Approach**: When building the annotation map in `AnnotationExtractor`, also scan the annotations themselves (from classpath JARs) and resolve meta-annotations transitively. If `@Component` is excluded, anything bearing an annotation that is itself (directly or transitively) annotated with `@Component` is also excluded.
- **Scope**: Only needed for dead code filtering. The `AnnotationExtractor` already scans class files — extend it to also scan annotation class files for their annotations.
- **Requires**: Reading annotation `.class` files from the classpath (not just project classes). Could reuse infrastructure from item 38 (classpath scanning), or scan only `java.lang.annotation`-retained annotations which are a small set.
- **Why high value**: Covers custom stereotype annotations automatically. A project defining `@DomainService` (meta-annotated with `@Component`) would be handled without any configuration.

### 78. Spring Data repository awareness in dead code (Medium value, low effort)

Spring Data repositories (e.g., `OwnerRepository extends JpaRepository`) are interfaces with no implementing class in project bytecode — Spring generates proxy implementations at runtime. These are always flagged as dead.

- **Approach**: In `DeadCodeFinder`, if a class is an interface that extends an external interface matching known Spring Data base types (`JpaRepository`, `CrudRepository`, `PagingAndSortingRepository`, `ReactiveCrudRepository`, `MongoRepository`, etc.), treat it as alive (or LOW confidence).
- **Alternative**: Subsumable by item 76 (meta-annotation traversal) if `@Repository` is on the interface. But many Spring Data repos don't have `@Repository` — the `extends JpaRepository` is sufficient for Spring to pick them up.
- **Relationship to item 75**: Framework presets would handle `@Repository`-annotated repos, but this item catches the common pattern where `@Repository` is omitted.

## Future ideas (not yet planned)

- **Consider removing the cnav disk cache**: Benchmarking on a ~20k LOC / 488-class project showed zero measurable difference between warm and cold cache. The cache adds complexity. Needs testing on larger projects.
- **Consider just failing on first file with wrong bytecode**: The current `ScanResult<T>` partial-fail approach adds complexity. A simpler alternative: fail fast with a clear error message.
- **Cross-referencing hotspots with bytecode data**: Combine `cnavHotspots` with `cnavCallers`/`cnavDeps` to answer "hotspot files and their structural dependencies."
- **Entity ownership / main developer**: Who "owns" each file by contribution weight. Could be a mode on `cnavAuthors`.
- **Architectural-level grouping**: Aggregate file-level results by logical component/layer.
- **Source-level structural analysis**: Analyze imports and type references from source files without requiring compilation — useful when mid-refactoring with compile errors.
