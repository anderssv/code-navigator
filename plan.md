# Plan

## 1. Include test source set in cnavInterfaces (High value)

Currently only searches main sources. Test fakes (RAClientFake, FakeCache, FakeEventSender) are invisible. For hexagonal architecture projects, seeing which interfaces have both production implementations AND test fakes is extremely valuable. Add a --include-test flag or make it the default.

## 2. True tree indentation for cnavCallers/cnavCallees (High value)

Docs say "indented tree" but the output is flat — each method lists its direct callers/callees at depth 1 only, even with -Pdepth=5. A nested tree showing multi-level call chains would be much more useful for understanding deep call paths:

```
resetPassword
  ← ReissueRoute.registerReissueRoute
    ← Application.setupKtor
```

## 3. "No packages found" message for cnavDeps with invalid filter (Low effort, high polish)

cnavDeps -Ppackage=nonexistent silently succeeds with empty output. Should print "No packages found matching 'nonexistent'" like the other commands do.

## 4. Reverse dependency view for cnavDeps (High value)

Currently shows "package X depends on Y". A reverse view ("who depends on package X?") would be invaluable for impact analysis: "If I change the domain package, what breaks?" Could be -Preverse=true or a separate cnavReverseDeps task.

## 5. Filter out stdlib/JDK noise in cnavCallees and cnavDeps (Medium value)

cnavCallees and cnavDeps output includes kotlin.jvm.internal, java.lang, kotlin.coroutines.intrinsics etc. A --project-only flag to filter to only project packages would make the output much more actionable. The signal-to-noise ratio suffers on larger methods.

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

## 8. cnavClass show interfaces implemented (Low effort)

cnavClass shows superclass ("Extends:") but doesn't explicitly list interfaces. Adding an "Implements:" section would complete the class signature view.
