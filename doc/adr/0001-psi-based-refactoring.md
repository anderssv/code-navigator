# ADR: PSI-based refactoring with bytecode-guided precision

## Status

Accepted (2026-05-31)

## Context

code-navigator's refactoring tasks (rename-method, rename-class, move-class) currently use OpenRewrite's KotlinParser for source transformations. OpenRewrite provides type-resolved AST traversal but is slow — 7s warm for a 140-file project on a simple rename.

A trial using `kotlin-compiler-embeddable` (PSI-only, no type resolution) completed the same rename in 678ms — 10x faster. The PSI approach parsed all 140 files and matched by name. Without type resolution, name-based matching can produce false positives for common method names.

However, code-navigator already has precise, type-resolved information from bytecode analysis (ASM). The call graph knows exactly which files reference a given method, resolved by fully-qualified class name and method descriptor. This information is available before any refactoring begins.

## Decision

Refactoring tasks use a two-phase architecture:

1. **Bytecode analysis (existing)** — determines exact locations using type-resolved call graph
2. **PSI rewrite (new)** — applies text edits at known offsets, no type resolution needed

The PSI layer is a dumb applicator — it receives exact file paths and symbol locations from bytecode analysis, parses only those files, and applies renames/moves at known positions.

```
┌─────────────────────────────────────────────────────┐
│ Agent workflow                                       │
│                                                     │
│  cnavFindUsages (bytecode) → exact locations        │
│         │                                           │
│         ▼                                           │
│  cnavRenameMethod (PSI) → apply edits at offsets    │
│         │                                           │
│         ▼                                           │
│  Result: diff output                                │
└─────────────────────────────────────────────────────┘
```

### Principles

- **Refactoring tasks require exact input** — fully-qualified class name, exact method name, exact file paths. They are precision tools, not search tools.
- **Discovery is separate from transformation** — agents use analysis tasks (cnavFindUsages, cnavFindSymbol, cnavClassDetail) to discover targets, then refactoring tasks to apply changes.
- **AgentHelpText guides the workflow** — TaskGuidance instructs agents to discover before refactoring.
- **PSI parses only affected files** — bytecode analysis identifies the file set, PSI only touches those files.
- **No type resolution in the rewrite phase** — locations are already type-verified by bytecode analysis. PSI navigates to offsets and replaces identifiers.

### Dependency

Add `kotlin-compiler-embeddable` as a dependency for refactoring tasks. Use the same classloader isolation (WorkerExecutor) already used for OpenRewrite.

## Consequences

### Positive

- **10x faster refactorings** — 678ms vs 7s for rename on a 140-file project
- **Even faster with file targeting** — parsing only affected files (~4 of 140) reduces to ~220ms
- **Simpler rewrite logic** — `TextEdit(file, offset, length, replacement)` vs OpenRewrite recipe composition
- **Existing analysis reused** — no duplication of reference-finding logic
- **Clear separation of concerns** — analysis = bytecode (precise), transformation = PSI (fast)

### Negative

- **New dependency** — `kotlin-compiler-embeddable` is ~40MB
- **Kotlin version coupling** — PSI parser version should roughly match project's Kotlin version (minor mismatches OK for identifier-level edits)
- **Offset computation** — need to map bytecode line numbers to PSI offsets (straightforward: parse file, find declaration at line)

### Neutral

- OpenRewrite remains available for operations that benefit from recipe composition (e.g., ChangeType for import rewriting across many files). The two approaches can coexist.
- BindingContext (full type resolution) is NOT needed for the initial migration. It can be added later for operations like extract-function that need data-flow analysis.

## Trial Results

| Metric | OpenRewrite | PSI (all files) | PSI (targeted, projected) |
|--------|-------------|-----------------|---------------------------|
| Warm time | 7s | 678ms | ~220ms |
| Files parsed | 140 | 140 | 4 |
| Precision | Type-resolved | Name-based | Bytecode-verified |
| Edits found | 10 | 6 | 6 |

Tested on Greitt project (140 Kotlin files, Kotlin 2.3.21) renaming `DateToggleService.toggleAdminDate` → `toggleAdminDateSelection`.

## References

- Trial prototype: `kotlin-compiler-embeddable:2.0.21` with `KtPsiFactory` + `collectDescendantsOfType`
- Inspiration: [Martin](https://github.com/audunstrand/martin) — CLI tool using the same embedded compiler approach
- Martin's `SourceRewriter`: offset-based `TextEdit` applied in reverse order per file
