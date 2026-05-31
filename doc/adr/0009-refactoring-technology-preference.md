# ADR: Refactoring technology preference order

## Status

Accepted (2026-05-31)

## Context

code-navigator uses multiple technologies for source rewriting:

- **PSI** (kotlin-compiler-embeddable) — fast, offset-based, no type resolution
- **OpenRewrite** — type-resolved AST with recipe composition, but slow (7s warm on 140 files)
- **Regex/text manipulation** — fast but fragile, no structural awareness

After migrating rename-method, rename-param, and rename-property from OpenRewrite to PSI, the question arose: what should MoveClassRewriter use?

PSI without full classpath binding cannot distinguish between same-named classes in different packages. OpenRewrite's `ChangeType` recipe has proper type attribution and correctly resolves which `import` statements and type references refer to the target class. A regex-based approach (`\bClassName\b`) risks false positives when common names collide.

## Decision

The preference order for refactoring implementations is:

1. **PSI** — use when name-based matching is sufficient (renames within a known scope, bytecode-verified locations)
2. **OpenRewrite** — use when type resolution is needed (cross-file type reference rewriting, import management with disambiguation)
3. **Regex/text** — avoid for structural code changes; acceptable only for trivial transformations (e.g., package declaration replacement in a known file)

### Current allocation

| Operation | Technology | Reason |
|-----------|-----------|--------|
| rename-method | PSI + ASM | Bytecode identifies exact call sites; PSI applies edits |
| rename-param | PSI | Scope is contained within method + named arg call sites |
| rename-property | PSI | Similar to param rename with dot-access heuristic |
| move-class (ChangeType) | OpenRewrite | Needs type-resolved import rewriting across files |
| move-class (file ops) | Plain Kotlin | File move, package decl replacement — no AST needed |

### Combining technologies in a single task

A single refactoring task may use both PSI and OpenRewrite when each handles a distinct concern:

- PSI for fast, scoped edits (rename a declaration, update references within a file)
- OpenRewrite for type-resolved cross-file operations (import rewriting, type reference disambiguation)

This is acceptable when it reduces overall complexity. Evaluate whether the combination is simpler than implementing the full operation in one technology. If OpenRewrite alone handles the task correctly and the performance cost is acceptable (e.g., move-class is infrequent), prefer the simpler single-technology approach.

### Migration path

If `kotlin-compiler-embeddable` gains lightweight binding context in the future (or we add classpath-aware resolution), MoveClassRewriter can be migrated to PSI. Until then, OpenRewrite's `ChangeType` is the correct tool for type-aware cross-file rewriting.

## Consequences

### Positive

- Rename operations are fast (~2s for 140-file project) via PSI
- MoveClass remains correct for edge cases (same-named classes, wildcard imports, nested types)
- Clear decision framework for future refactoring operations

### Negative

- OpenRewrite dependency remains in the build (classloader isolation, ~16s cold start for move-class)
- Two rewriting technologies to maintain

### Neutral

- The two technologies coexist cleanly — PSI handles the hot path (renames), OpenRewrite handles the complex path (moves)
