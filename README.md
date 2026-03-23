# Code Navigator

A Gradle plugin that provides bytecode-level code navigation and git history analysis for JVM projects. It analyzes compiled `.class` files for structural navigation (class listing, symbol search, call graph traversal, class detail inspection, interface implementation lookup, package dependency analysis) and git logs for behavioral analysis (hotspots, change coupling, code age, author distribution, churn).

Built primarily for use by **coding agents** (AI assistants that write and refactor code), though it is equally useful for human developers. Works with any JVM language (Kotlin, Java, Scala, etc.) since it operates on compiled `.class` files using [ASM](https://asm.ow2.io/). The git history analysis is inspired by [Code Maat](https://github.com/adamtornhill/code-maat) and the ideas in Adam Tornhill's *Your Code as a Crime Scene*.

## Why use Code Navigator?

Text search (grep, ripgrep) requires iterative discovery. You search for `cache.get(`, find some results, then realize you missed the Kotlin safe-call `cache?.get(`, then extension functions, then delegation patterns. Each iteration requires you to know what syntactic variant you haven't tried yet — and you can't know what you've missed until you find it by accident.

Bytecode analysis sidesteps this entirely. All syntax variants compile to the same invocation instruction. One `cnavCallers` query returns all call sites — complete, correct, no false positives, no missed calls.

For an agent, each grep iteration is a tool call round-trip. For a human, each is a context switch. Code Navigator eliminates the iterative discovery loop: you get the full call graph from one query.

Beyond call graphs, it provides structured answers to common navigation questions:

- **"What classes exist?"** -- `cnavListClasses` and `cnavFindClass` give a complete index of classes with source file paths.
- **"Where is this method defined?"** -- `cnavFindSymbol` searches methods and fields by regex.
- **"Who calls this?"** -- `cnavCallers` walks the call graph backwards to show all transitive callers as an indented tree.
- **"What does this call?"** -- `cnavCallees` walks the call graph forward.
- **"What does this class look like?"** -- `cnavClass` shows the full signature: fields, methods, superclass, interfaces.
- **"Who implements this interface?"** -- `cnavInterfaces` finds all implementors.
- **"How do packages depend on each other?"** -- `cnavDeps` shows package-level dependency edges.
- **"Is there a cyclic dependency?"** -- `cnavDsm` shows a Dependency Structure Matrix highlighting cyclic package dependencies.

And git history analysis questions (no compilation needed):

- **"Which files change the most?"** -- `cnavHotspots` ranks files by revision count and churn.
- **"Which files change together?"** -- `cnavCoupling` finds temporal coupling between files.
- **"How stale is this code?"** -- `cnavAge` shows time since last change per file.
- **"Who works on what?"** -- `cnavAuthors` shows distinct contributor count per file.
- **"Where is the most churn?"** -- `cnavChurn` shows lines added/deleted per file.

All output is compact, structured text that fits easily into an agent's context window.

## Getting started

Copy-paste this to your agent:

> Add the no.f12.code-navigator Gradle plugin to this project. After installing, run `./gradlew cnavAgentHelp` to get full usage instructions optimized for AI agents, and `./gradlew cnavHelp` to see all available tasks and their parameters.
>
> Then add a "Code Navigator (cnav)" section to AGENTS.md documenting the plugin. It should include:
> - A short description of what it does (bytecode analysis + git history)
> - A nudge to prefer cnav over grep/ripgrep for finding callers, implementations, and dependencies
> - A note to run cnavAgentHelp for full instructions
> - A compact command list showing all available tasks with one-line comments (navigation tasks and git history tasks), grouped by whether they require compilation

The `cnavAgentHelp` task prints agent-optimized instructions covering workflow, parameters, JSON schemas, and output extraction tips. You can also use its output as the starting point for a custom agent skill if your tool supports it (e.g. a Claude Code skill or Cursor rule).

## Installation

Apply the plugin in your `build.gradle.kts`:

```kotlin
plugins {
    id("no.f12.code-navigator") version "0.1.0"
}
```

No configuration is needed. The plugin registers tasks that operate on the `main` source set's compiled output. Run `./gradlew cnavHelpConfig` to see all available configuration parameters.

## Tasks

The plugin provides two categories of tasks:

- **Navigation tasks** analyze compiled bytecode and depend on the `classes` task (bytecode is compiled automatically before analysis).
- **Analysis tasks** analyze git history and do **not** require compilation.

### Navigation Tasks

### cnavHelp

Shows help text for all available tasks.

```bash
./gradlew cnavHelp
```

### cnavListClasses

Lists all classes in the project with their source files.

```bash
./gradlew cnavListClasses
```

Output:

```
Class                          | Source File
------------------------------ | -------------------------------------------
com.example.api.OrderController | com/example/api/OrderController.kt
com.example.model.Order         | com/example/model/Order.kt
...

42 classes found.
```

Results are cached to disk and reused when class files have not changed.

### cnavFindClass

Searches for classes matching a regex pattern. Matches against both class name and source file path (case-insensitive).

```bash
./gradlew cnavFindClass -Ppattern=Service
./gradlew cnavFindClass -Ppattern="domain\..*"
```

### cnavFindSymbol

Searches for methods and fields matching a regex pattern. Filters out constructors, synthetic methods, Kotlin property accessors, and data class boilerplate.

```bash
./gradlew cnavFindSymbol -Ppattern=resetPassword
./gradlew cnavFindSymbol -Ppattern="find.*"
```

Output columns: `Package | Class | Symbol | Kind | Source File`

### cnavCallers

Shows who calls a given method as an indented tree. Walks callers transitively up to a configurable depth.

```bash
./gradlew cnavCallers -Pmethod=resetPassword
./gradlew cnavCallers -Pmethod=".*Service\.find.*" -Pdepth=5
```

Output:

```
UserService.resetPassword
  <- PasswordController.handleReset (PasswordController.kt)
    <- Router.configure (Router.kt)
```

### cnavCallees

Shows what a method calls as an indented tree. Walks callees transitively up to a configurable depth.

```bash
./gradlew cnavCallees -Pmethod="Controller\.handle.*"
./gradlew cnavCallees -Pmethod="Service\.create" -Pdepth=5
```

### cnavClass

Shows a class signature: fields with types, methods with parameter and return types, superclass, and implemented interfaces.

```bash
./gradlew cnavClass -Ppattern=ResetPasswordService
./gradlew cnavClass -Ppattern=".*Client"
```

Output:

```
=== com.example.service.UserService (UserService.kt) ===
Implements: UserOperations

Fields:
  repository: UserRepository
  notifier: EmailNotifier

Methods:
  register(String, String): User
  findById(long): User
  resetPassword(String): void
```

### cnavInterfaces

Finds classes that implement a given interface.

```bash
./gradlew cnavInterfaces -Ppattern=Repository
./gradlew cnavInterfaces -Ppattern=".*Client"
```

Output:

```
=== com.example.repository.Repository (2 implementors) ===
  com.example.repository.OrderRepository (OrderRepository.kt)
  com.example.repository.UserRepository (UserRepository.kt)
```

### cnavDeps

Shows package-level dependencies. Without parameters, shows all packages. With `-Ppackage`, filters to matching packages.

```bash
./gradlew cnavDeps
./gradlew cnavDeps -Ppackage=services
```

Output:

```
com.example.api
  -> com.example.model
  -> com.example.service

com.example.service
  -> com.example.model
  -> com.example.repository
```

### cnavDsm

Shows a Dependency Structure Matrix (DSM) — a compact grid showing how packages depend on each other. Each cell shows how many references flow from row to column. Highlights cyclic dependencies with class-level detail.

```bash
./gradlew cnavDsm
./gradlew cnavDsm -Proot-package=com.example -Pdepth=3
./gradlew cnavDsm -Pdsm-html=build/dsm.html
```

Use `-Pdsm-html=<path>` to generate an interactive HTML matrix with color-coded cells (green = forward, red = backward/cyclic) and hover tooltips showing class-level dependencies.

### Analysis Tasks (Git History)

These tasks analyze git history and do **not** require compilation. All accept `-Pafter=YYYY-MM-DD` to set the analysis window (default: 1 year ago). Git rename tracking is enabled by default; use `-Pno-follow` to disable it.

### cnavHotspots

Ranks files by revision count and total churn (lines added + deleted). Highlights files that change frequently and are complex.

```bash
./gradlew cnavHotspots
./gradlew cnavHotspots -Pmin-revs=5 -Ptop=20
```

### cnavCoupling

Finds files that change together (temporal coupling). High coupling may indicate hidden dependencies.

```bash
./gradlew cnavCoupling
./gradlew cnavCoupling -Pmin-coupling=50 -Pmin-shared-revs=10
```

### cnavAge

Shows time since last change per file. Old code may be stable — or forgotten.

```bash
./gradlew cnavAge
./gradlew cnavAge -Ptop=20
```

### cnavAuthors

Shows distinct contributor count per file. Files with many authors may need more review attention.

```bash
./gradlew cnavAuthors
./gradlew cnavAuthors -Pmin-revs=3 -Ptop=20
```

### cnavChurn

Shows lines added and deleted per file. High churn files are where most development effort goes.

```bash
./gradlew cnavChurn
./gradlew cnavChurn -Ptop=20
```

## How it works

1. **Bytecode scanning** -- Walks all `.class` files from the main source set's output directories. Uses ASM's `ClassVisitor` and `MethodVisitor` to extract class metadata, symbols, and call edges.

2. **Call graph construction** -- Builds a bidirectional call graph (`caller -> callees` and `callee -> callers`) from method invocation instructions in bytecode.

3. **Git log parsing** -- Runs `git log --numstat` and parses the output to extract per-file revision counts, author lists, and line-level churn data.

4. **Caching** -- Class index results are cached to disk with timestamp-based freshness checking, so repeated queries on unchanged code are fast.

5. **Filtering** -- All search tasks accept regex patterns and match case-insensitively against relevant fields (class name, source path, symbol name, package name).

## Building from source

```bash
./gradlew build
```

Requires Gradle 9.4+ (included via the Gradle wrapper).

## License

See [LICENSE](LICENSE) for details.
