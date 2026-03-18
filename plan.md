# Plan

## ~~1. Include test source set in cnavInterfaces (High value)~~ DONE

`cnavInterfaces` now supports `-Pincludetest=true` to also scan test class directories. This reveals test fakes (e.g., `FakeRepo`, `StubClient`) alongside production implementations. Uses a separate cache file (`interface-registry-all.cache`) when test classes are included to avoid mixing results.

## 2. True tree indentation for cnavCallers/cnavCallees (High value)

Docs say "indented tree" but the output is flat — each method lists its direct callers/callees at depth 1 only, even with -Pdepth=5. A nested tree showing multi-level call chains would be much more useful for understanding deep call paths:

```
resetPassword
  ← ReissueRoute.registerReissueRoute
    ← Application.setupKtor
```

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

## 7. JSON/machine-readable output format (Medium value)

All commands output human-readable tables/trees. Adding -Pformat=json would enable integration with CI pipelines, IDE plugins, and other tooling.

## ~~8. cnavClass show interfaces implemented (Low effort)~~ DONE

Already implemented. `ClassDetailExtractor` extracts interfaces from bytecode and `ClassDetailFormatter` outputs "Implements: ..." when interfaces are present.
