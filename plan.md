# Plan

Items are ordered for sequential execution: each item can be done independently, top-to-bottom.
Value and effort are qualitative assessments to aid prioritization, not estimates.

---

## 1. `cnavContext` — smart context gathering for AI agents

**Value: high** | **Effort: medium**

Given a class or method, automatically gather "everything an agent needs": class signature, callers (depth 2), callees (depth 2), interface implementations, and source file path.

```bash
./gradlew cnavContext -Ppattern=ResetPasswordService -Pformat=json
```

- **Builder**: Orchestrates existing `ClassDetailScanner`, `CallTreeBuilder` (callers + callees), and `InterfaceRegistry` into a single result
- **Parameters**: `-Ppattern=<class>` (required), `-Pmaxdepth=N` (default 2), `-Pformat=json|text|llm`
- **Why high value**: Reduces agent round-trips from 4-5 to 1, saving significant wall-clock time. Pure composition of existing features — no new analysis code.

---

## 2. `cnavWhyDepends` — dependency edge explanation

**Value: high** | **Effort: medium**

The DSM tells you package A depends on package B, but not *why*. To break a cycle you need to know the specific fields, method parameters, return types, and local variable types that create the dependency.

- **Builder**: `DependencyExplainer.explain(callGraph, from, to) -> List<DependencyEdge(sourceClass, targetClass, kind: FIELD|PARAMETER|RETURN_TYPE|LOCAL_VAR|METHOD_CALL, detail: String)>`
- **Parameters**: `-Pfrom=<class-or-package>` (required), `-Pto=<class-or-package>` (required), `-Pproject-only=true`
- **Why useful**: The missing link between "the DSM says there's a dependency" and "here's what to move/extract to break it."

---

## 4. `cnavTestHealth` — verify all test methods actually ran

**Value: high** | **Effort: medium**

From user feedback: a project had 19 silently skipped tests because test methods had non-`Unit` return types. Count `@Test`-annotated methods from bytecode, compare against JUnit XML results, flag the delta.

1. **Bytecode scan**: Find all methods annotated with `@Test` (JUnit 4/5, Kotlin Test). This is the "expected" set.
2. **JUnit XML scan**: Parse test result XML files (`build/test-results/test/TEST-*.xml` or `target/surefire-reports/TEST-*.xml`). This is the "actual" set.
3. **Diff**: Report methods present in bytecode but absent from XML results — the silently skipped tests.

- **Lifecycle**: `dependsOn("test")` — runs after tests complete
- **Additional checks** (bytecode-only): test methods missing `@Test` annotation but named `test*`, test classes with no `@Test` methods, `@Disabled`/`@Ignore` inventory
- Both Gradle and Maven write the same JUnit XML format, so one parser handles both.

---

## 5. `cnavJar` — inspect library class signatures

**Value: high** | **Effort: medium**

Inspect the methods and signatures of classes inside a JAR file, whether or not the JAR is on the project classpath.

```bash
./gradlew cnavJar -Partifact=com.fasterxml.jackson.core:jackson-databind -Ppattern=ObjectMapper
./gradlew cnavJar -Pjar=/path/to/some.jar -Ppattern=SomeClass
```

- **Two modes**: `-Partifact=<group:name>` (resolve from runtime classpath) or `-Pjar=<path>` (arbitrary JAR)
- **Implementation**: Reuse `ClassDetailExtractor` / `ClassDetailScanner` but feed entries from a `JarFile`. For `-Partifact`, resolve via Gradle's `configurations.runtimeClasspath.resolvedConfiguration` / Maven's `project.runtimeClasspathElements`.
- **Why**: AI agents frequently need to check library API signatures. Bytecode gives ground-truth for the exact version in the project.
- **Note**: This builds classpath resolution infrastructure reused by items 7 and 13.

---

## 6. `cnavDead` baseline diff — confirm cleanup was complete

**Value: medium** | **Effort: low**

After triaging dead code and removing items, re-run `cnavDead` and see what changed.

- **Approach**: `-Pbaseline=<path>` parameter pointing to a saved JSON output from a previous run. On re-run, show: items removed since baseline, items still present, new items.
- **Alternative**: Just save JSON and use `jq` to diff. Built-in support is more ergonomic but the alternative is viable.

---

## 7. Cycle fix suggestions in DSM

**Value: high** | **Effort: medium**

The DSM tells you which cycles exist, but not how to fix them. When `-Pcycles=true`, also show which specific class-level edges would need to move to break the cycle, and suggest which direction the dependency should flow.

- **Prerequisite**: Benefits from item 3 (`cnavWhyDepends`) infrastructure — same edge-explanation logic.
- **Separate from DSM what-if**: What-if simulation (`-Pwhat-if=<class>:<target-package>`) is a distinct, higher-effort feature. Evaluate need after cycle fix suggestions ship.

---

## 8. Extract ConfidenceScorer from DeadCodeFinder

**Value: medium** | **Effort: low**

`DeadCodeFinder` inlines all confidence-scoring logic (annotation checks, interface checks, method name heuristics, caller count thresholds). Extract a `ConfidenceScorer` class that takes a `DeadCode` candidate and returns its `DeadCodeConfidence` + `DeadCodeReason`.

- Makes scoring rules independently testable and easier to extend (e.g., meta-annotation traversal, Spring Data awareness).
- **Prerequisite for**: Item 14 (meta-annotation traversal) benefits from clean scoring separation.

---

## 9. Split JsonFormatter and LlmFormatter per-feature

**Value: medium** | **Effort: medium**

Self-analysis found `JsonFormatter` (217 outgoing dependencies, 47 referenced types) and `LlmFormatter` (177 outgoing, 46 types) are god classes. They change together 96% of the time.

- **Approach**: Split into per-feature formatters (e.g., `CallTreeJsonFormatter`, `DeadCodeJsonFormatter`). Top-level formatters become thin dispatchers.
- **Ordering**: `LlmFormatter` first (primary agent-facing format), then `JsonFormatter`. `TableFormatter` is smaller and can follow later.
- **Benefits**: Adding a new feature means adding a new formatter file, not editing a shared god class.

---

## 10. `cnavReport` — consolidated full analysis

**Value: medium** | **Effort: low**

Run all analysis tasks and produce a single consolidated report. `cnavMetrics` already exists for a summary snapshot; `cnavReport` runs everything and outputs all results in one pass.

- **Parameters**: Inherits from constituent tasks. `-Pformat=json` produces a single JSON object with sections per analysis.
- **Why useful**: Agents often want the full picture. A single task is faster (shared caching, one compilation) and produces a coherent snapshot.

---

## 11. Full classpath scanning option

**Value: high** | **Effort: medium**

Add `-Pclasspath=true` to scan the full runtime classpath (project classes + all dependency JARs).

- **Applies to**: `cnavListClasses`, `cnavFindClass`, `cnavFindSymbol`, `cnavClass`, `cnavInterfaces`, `cnavUsages`
- **Reuses**: Classpath resolution infrastructure from item 5 (`cnavJar`).
- **Considerations**: Significantly slower (thousands of classes). Combine with existing `-Ppattern` / `-Powner` filters to narrow scope. Consider caching scanned JARs by checksum.
- **Why**: AI agents frequently need to check library API signatures to write correct code.

---

## 12. `cnavDiff` — structural diff between builds

**Value: medium** | **Effort: medium**

Compare two compiled states and show structural changes: added/removed/changed classes, methods, and dependency edges.

- **Use cases**: API signature changes from dependency upgrades; verifying a refactoring was purely structural.
- **Builder**: `StructuralDiff.diff(baselineClassDir, currentClassDir) -> List<Change(className, memberName, kind: ADDED|REMOVED|SIGNATURE_CHANGED, oldSignature?, newSignature?)>`
- **Parameters**: `-Pbaseline=<path>` (path to baseline class directory), `-Paffected=true` (also list affected call sites)

---

## 13. Meta-annotation traversal for dead code filtering

**Value: high** | **Effort: medium**

`@RestController` is meta-annotated with `@Controller` which is meta-annotated with `@Component`. Currently, excluding `Component` does NOT exclude `@RestController`.

- **Approach**: In `AnnotationExtractor`, also scan annotation `.class` files from classpath JARs and resolve meta-annotations transitively.
- **Reuses**: Classpath resolution from item 5/11.
- **Why**: Covers custom stereotype annotations automatically. A project defining `@DomainService` (meta-annotated with `@Component`) would be handled without configuration.

---

## 14. `cnavLayerCheck` — architecture conformance

**Value: high** | **Effort: ambitious**

Declare layer rules and validate them against the actual call graph. Like ArchUnit but without writing test code.

```kotlin
codeNavigator {
    rules {
        "services" mustNotDependOn "ra"
        "domain" mustNotDependOn "ktor"
    }
}
```

- Output: list of violations with the specific class-level edges that break the rule.
- **Prerequisite**: Benefits from item 3 (`cnavWhyDepends`) for edge explanation.

---

## 15. `cnavUnused` — unused build dependencies

**Value: medium** | **Effort: medium**

Find entire libraries that could be removed. For each declared dependency JAR, extract the package list. Scan project bytecode for references. Dependencies with zero references are candidates for removal.

- **Caveats**: Runtime-only dependencies (JDBC drivers, logging backends) will show as "unused." Needs an exclusion mechanism.
- **Reuses**: Classpath enumeration infrastructure from items 6/13.

---

## 16. Structured cache format

**Value: medium** | **Effort: medium**

`FileCache` subclasses serialize as tab-separated positional fields. Adding a field requires updating both `serialize()` and `deserialize()` and any field order mismatch silently corrupts data.

- **Approach**: Replace with a self-describing format that tolerates field additions without breaking existing caches.
- **Note**: Consider removing cache entirely — benchmarking on ~20k LOC / 488-class project showed zero measurable difference. Needs testing on larger projects.

---

## 17. Gradle incremental task support

**Value: medium** | **Effort: high**

Support Gradle's incremental task API (`@InputFiles`, `@OutputFile`, `InputChanges`) to skip unchanged files. Call graph analysis is inherently whole-program, so incremental support is most beneficial for leaf tasks (`cnavListClasses`, `cnavFindSymbol`, `cnavFindClass`).

---

## 18. DSM what-if simulation

**Value: medium** | **Effort: high**

`-Pwhat-if=<class>:<target-package>` — simulate moving a class to a different package and re-evaluate cycles without actually making the change.

- **Prerequisite**: Item 8 (cycle fix suggestions) should ship first.

---

## Parked

Items below are low-priority or may not be worth building. Revisit if demand emerges.

- **Custom entry-point config file** (`.cnav-entry-points`): Framework presets + `exclude-annotated` + `exclude-framework` cover most cases. A config file adds marginal value over the existing parameters. Revisit if users request it.
- **DI-aware `cnavInjectors`**: Largely solvable with `cnavUsages -Ptype=X` combined with interface dispatch resolution. High effort for marginal gain.
- **Stable JSON schemas** (`cnavSchema`): JSON output is already self-describing. Agents infer schema from examples.
- **Split root package** (S9): Lower priority now that `navigation/` has been split into sub-packages. Dependency direction is already clear enough.

## Future ideas (not yet planned)

- **Dead code: flag methods called only from test scope**: Use source set tagging from item 2 to identify production methods/classes whose only callers are in the test source set. These are candidates for removal since no production code depends on them. Replaces the current separate `testGraph` approach in `DeadCodeFinder` with a unified call graph that has source set metadata.
- **Remove cnav disk cache entirely**: Zero measurable difference on ~20k LOC. Reduces complexity. Needs testing on larger projects.
- **Fail fast on wrong bytecode**: Replace `ScanResult<T>` partial-fail with hard failure + clear error.
- **Cross-reference hotspots with bytecode**: Combine `cnavHotspots` with `cnavCallers`/`cnavDeps`.
- **Entity ownership / main developer**: Who "owns" each file by contribution weight. Mode on `cnavAuthors`.
- **Architectural-level grouping**: Aggregate file-level results by logical component/layer.
- **Source-level structural analysis**: Analyze imports from source files without requiring compilation.
