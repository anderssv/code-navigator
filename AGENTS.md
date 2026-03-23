# Code Navigator - Agent Instructions

## Release Process

Version is in `build.gradle.kts`. Development versions use `-SNAPSHOT` suffix.

To release:

1. Update `CHANGELOG.md` with the new version and a summary of changes since the last release. Use `git diff` or `git log` since the last release tag to identify what changed.
2. Remove `-SNAPSHOT` from `version` in `build.gradle.kts` (e.g. `0.1.2-SNAPSHOT` → `0.1.2`)
3. Update the version in the `README.md` installation example to match the release version
4. Commit: `git commit -am "Release X.Y.Z"`
5. Tag: `git tag vX.Y.Z`
6. Publish to mavenLocal: `mise exec -- ./gradlew publishToMavenLocal`
7. Publish to Gradle Plugin Portal: `mise exec -- ./gradlew publishPlugins`
8. Bump to next snapshot: change `version` to next patch with `-SNAPSHOT` (e.g. `0.1.3-SNAPSHOT`)
9. Update `test-project/build.gradle.kts` to use the new snapshot version
10. Commit: `git commit -am "Bump to X.Y.Z-SNAPSHOT"`
11. Push: `git push && git push --tags`

## Package Structure

The codebase is split into three areas:

- **`no.f12.codenavigator`** (root) — Shared infrastructure: `CodeNavigatorPlugin`, formatters (`JsonFormatter`, `LlmFormatter`, `TableFormatter`), `OutputFormat`, `OutputWrapper`, `CacheFreshness`, help text tasks.
- **`no.f12.codenavigator.navigation`** — Bytecode-based navigation: class scanning, symbol extraction, call graph building, interface registry, package dependencies, DSM (Dependency Structure Matrix). All navigation tasks depend on compiled `classes`.
- **`no.f12.codenavigator.analysis`** — Git history analysis: hotspots, coupling, code age, authors, churn. Parses `git log` output. No compilation required.

## Code Structure Principles

### Separate parsing, resolution, and formatting

Code should be structured in distinct layers:

1. **Parsing** — reads raw input (bytecode, files) and produces a data structure. No formatting, no output.
2. **Resolution / tree-building** — takes the parsed data and a query, produces a result data structure (e.g. a tree of nodes). No formatting, no I/O.
3. **Formatting** — takes the result data structure and renders it to text, JSON, etc. No graph walking, no query logic.

Each layer is independently testable. Formatters never reach back into the parsed data to resolve more information — that belongs in the resolution layer. When two formatters (e.g. text and JSON) need the same data, they consume the same result structure rather than independently walking the source data.

### Why this matters

- Bugs are isolated to one layer. If the tree is wrong, it's the resolution layer. If the output looks wrong but the tree is correct, it's the formatter.
- Adding a new output format means writing only a formatter, not duplicating resolution logic.
- Tests for each layer are fast and focused — no need for integration-level setup to test formatting.
