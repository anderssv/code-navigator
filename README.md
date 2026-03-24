# Code Navigator

A Gradle and Maven plugin that provides bytecode-level code navigation and git history analysis for JVM projects. It analyzes compiled `.class` files for structural navigation (class listing, symbol search, call graph traversal, class detail inspection, interface implementation lookup, package dependency analysis) and git logs for behavioral analysis (hotspots, change coupling, code age, author distribution, churn).

Built primarily for use by **coding agents** (AI assistants that write and refactor code), though it is equally useful for human developers. Works with any JVM language (Kotlin, Java, Scala, etc.) since it operates on compiled `.class` files using [ASM](https://asm.ow2.io/). The git history analysis is inspired by [Code Maat](https://github.com/adamtornhill/code-maat) and the ideas in Adam Tornhill's *Your Code as a Crime Scene*.

## Getting started

Copy-paste this to your agent:

**Gradle:**

> Add the no.f12.code-navigator Gradle plugin to this project. After installing, run `./gradlew cnavAgentHelp` to get full usage instructions optimized for AI agents, and `./gradlew cnavHelp` to see all available tasks and their parameters.
>
> Then add a "Code Navigator (cnav)" section to AGENTS.md documenting the plugin. It should include:
> - A short description of what it does (bytecode analysis + git history)
> - A nudge to prefer cnav over grep/ripgrep for finding callers, implementations, and dependencies
> - A note to run cnavAgentHelp for full instructions
> - A compact command list showing all available tasks with one-line comments (navigation tasks and git history tasks), grouped by whether they require compilation

**Maven:**

> Add the no.f12 code-navigator-maven-plugin to this project. After installing, run `mvn cnav:agent-help` to get full usage instructions optimized for AI agents, and `mvn cnav:help` to see all available goals and their parameters.
>
> Then add a "Code Navigator (cnav)" section to AGENTS.md documenting the plugin. It should include:
> - A short description of what it does (bytecode analysis + git history)
> - A nudge to prefer cnav over grep/ripgrep for finding callers, implementations, and dependencies
> - A note to run `mvn cnav:agent-help` for full instructions
> - A compact command list showing all available goals with one-line comments (navigation goals and git history goals), grouped by whether they require compilation

The `cnavAgentHelp` task prints agent-optimized instructions covering workflow, parameters, JSON schemas, and output extraction tips. You can also use its output as the starting point for a custom agent skill if your tool supports it (e.g. a Claude Code skill or Cursor rule).

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

## Requirements

- **JDK 21** or newer (both for running the plugin and for compiling your project)
- **Gradle 9.x** or **Maven 3.9+**

## Installation

### Gradle

Apply the plugin in your `build.gradle.kts`:

```kotlin
plugins {
    id("no.f12.code-navigator") version "0.1.16"
}
```

No configuration is needed. The plugin registers tasks that operate on the `main` source set's compiled output. Run `./gradlew cnavHelpConfig` to see all available configuration parameters.

You can optionally configure persistent defaults via the `codeNavigator` block:

```kotlin
codeNavigator {
    rootPackage = "com.example"  // default: "" (all packages)
}
```

| Property      | Default | Description                                                                 |
|---------------|---------|-----------------------------------------------------------------------------|
| `rootPackage` | `""`    | Only include packages under this prefix (used by `cnavDsm`). Empty = all.  |

These defaults are used when the corresponding `-P` flag is not provided. A `-P` flag always overrides the config block.

### Maven

Add the plugin to your `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>no.f12</groupId>
            <artifactId>code-navigator-maven-plugin</artifactId>
            <version>0.1.16</version>
        </plugin>
    </plugins>
</build>
```

No configuration is needed. Run `mvn cnav:config-help` to see all available configuration parameters. Maven goals use the `cnav:` prefix with kebab-case names (e.g. `mvn cnav:find-class -Dpattern=Service`).

You can optionally configure persistent defaults via the `<configuration>` block:

```xml
<plugin>
    <groupId>no.f12</groupId>
    <artifactId>code-navigator-maven-plugin</artifactId>
    <version>0.1.16</version>
    <configuration>
        <rootPackage>com.example</rootPackage>  <!-- default: "" (all packages) -->
    </configuration>
</plugin>
```

| Property      | Default | Description                                                                 |
|---------------|---------|-----------------------------------------------------------------------------|
| `rootPackage` | `""`    | Only include packages under this prefix (used by `cnav:dsm`). Empty = all.  |

These defaults are used when the corresponding `-D` flag is not provided. A `-D` flag always overrides the configuration block.

## Tasks

The plugin provides two categories of tasks:

- **Navigation tasks** analyze compiled bytecode and depend on the `classes` task (bytecode is compiled automatically before analysis).
- **Analysis tasks** analyze git history and do **not** require compilation.

### Navigation Tasks

### help

Shows help text for all available tasks.

```bash
# Gradle
./gradlew cnavHelp

# Maven
mvn cnav:help
```

### agent-help

Shows detailed instructions for AI coding agents on how to use code-navigator effectively. Includes recommended workflows, task selection guidance, JSON schemas, and tips for optimal results.

```bash
# Gradle
./gradlew cnavAgentHelp

# Maven
mvn cnav:agent-help
```

### config-help

Lists all available configuration parameters with defaults and which tasks they apply to.

```bash
# Gradle
./gradlew cnavHelpConfig

# Maven
mvn cnav:config-help
```

### list-classes

Lists all classes in the project with their source files.

```bash
# Gradle
./gradlew cnavListClasses

# Maven
mvn cnav:list-classes
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

### find-class

Searches for classes matching a regex pattern. Matches against both class name and source file path (case-insensitive).

```bash
# Gradle
./gradlew cnavFindClass -Ppattern=Service
./gradlew cnavFindClass -Ppattern="domain\..*"

# Maven
mvn cnav:find-class -Dpattern=Service
mvn cnav:find-class -Dpattern="domain\..*"
```

### find-symbol

Searches for methods and fields matching a regex pattern. Filters out constructors, synthetic methods, Kotlin property accessors, and data class boilerplate.

```bash
# Gradle
./gradlew cnavFindSymbol -Ppattern=resetPassword
./gradlew cnavFindSymbol -Ppattern="find.*"

# Maven
mvn cnav:find-symbol -Dpattern=resetPassword
mvn cnav:find-symbol -Dpattern="find.*"
```

Output columns: `Package | Class | Symbol | Kind | Source File`

### find-callers

Shows who calls a given method as an indented tree. Walks callers transitively up to a configurable depth.

```bash
# Gradle
./gradlew cnavCallers -Pmethod=resetPassword
./gradlew cnavCallers -Pmethod=".*Service\.find.*" -Pmaxdepth=5

# Maven
mvn cnav:find-callers -Dmethod=resetPassword
mvn cnav:find-callers -Dmethod=".*Service\.find.*" -Dmaxdepth=5
```

Output:

```
UserService.resetPassword
  <- PasswordController.handleReset (PasswordController.kt)
    <- Router.configure (Router.kt)
```

### find-callees

Shows what a method calls as an indented tree. Walks callees transitively up to a configurable depth.

```bash
# Gradle
./gradlew cnavCallees -Pmethod="Controller\.handle.*"
./gradlew cnavCallees -Pmethod="Service\.create" -Pmaxdepth=5

# Maven
mvn cnav:find-callees -Dmethod="Controller\.handle.*"
mvn cnav:find-callees -Dmethod="Service\.create" -Dmaxdepth=5
```

### class-detail

Shows a class signature: fields with types, methods with parameter and return types, superclass, and implemented interfaces.

```bash
# Gradle
./gradlew cnavClass -Ppattern=ResetPasswordService
./gradlew cnavClass -Ppattern=".*Client"

# Maven
mvn cnav:class-detail -Dpattern=ResetPasswordService
mvn cnav:class-detail -Dpattern=".*Client"
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

### find-interfaces

Finds classes that implement a given interface.

```bash
# Gradle
./gradlew cnavInterfaces -Ppattern=Repository
./gradlew cnavInterfaces -Ppattern=".*Client"

# Maven
mvn cnav:find-interfaces -Dpattern=Repository
mvn cnav:find-interfaces -Dpattern=".*Client"
```

Output:

```
=== com.example.repository.Repository (2 implementors) ===
  com.example.repository.OrderRepository (OrderRepository.kt)
  com.example.repository.UserRepository (UserRepository.kt)
```

### package-deps

Shows package-level dependencies. Without parameters, shows all packages. With `-Ppackage` / `-Dpackage`, filters to matching packages.

```bash
# Gradle
./gradlew cnavDeps
./gradlew cnavDeps -Ppackage=services

# Maven
mvn cnav:package-deps
mvn cnav:package-deps -Dpackage=services
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

### dsm

Shows a Dependency Structure Matrix (DSM) — a compact grid showing how packages depend on each other. Each cell shows how many references flow from row to column. Highlights cyclic dependencies with class-level detail.

```bash
# Gradle
./gradlew cnavDsm
./gradlew cnavDsm -Proot-package=com.example -Pdsm-depth=3
./gradlew cnavDsm -Pdsm-html=build/dsm.html

# Maven
mvn cnav:dsm
mvn cnav:dsm -Droot-package=com.example -Ddsm-depth=3
mvn cnav:dsm -Ddsm-html=target/dsm.html
```

Use `-Pdsm-html=<path>` (Gradle) or `-Ddsm-html=<path>` (Maven) to generate an interactive HTML matrix with color-coded cells (green = forward, red = backward/cyclic) and hover tooltips showing class-level dependencies.

### Analysis Tasks (Git History)

These tasks analyze git history and do **not** require compilation. All accept `-Pafter=YYYY-MM-DD` (Gradle) or `-Dafter=YYYY-MM-DD` (Maven) to set the analysis window (default: 1 year ago). Git rename tracking is enabled by default; use `-Pno-follow` (Gradle) or `-Dno-follow` (Maven) to disable it.

### hotspots

Ranks files by revision count and total churn (lines added + deleted). Highlights files that change frequently and are complex.

```bash
# Gradle
./gradlew cnavHotspots
./gradlew cnavHotspots -Pmin-revs=5 -Ptop=20

# Maven
mvn cnav:hotspots
mvn cnav:hotspots -Dmin-revs=5 -Dtop=20
```

### coupling

Finds files that change together (temporal coupling). High coupling may indicate hidden dependencies.

```bash
# Gradle
./gradlew cnavCoupling
./gradlew cnavCoupling -Pmin-coupling=50 -Pmin-shared-revs=10

# Maven
mvn cnav:coupling
mvn cnav:coupling -Dmin-coupling=50 -Dmin-shared-revs=10
```

### code-age

Shows time since last change per file. Old code may be stable — or forgotten.

```bash
# Gradle
./gradlew cnavAge
./gradlew cnavAge -Ptop=20

# Maven
mvn cnav:code-age
mvn cnav:code-age -Dtop=20
```

### authors

Shows distinct contributor count per file. Files with many authors may need more review attention.

```bash
# Gradle
./gradlew cnavAuthors
./gradlew cnavAuthors -Pmin-revs=3 -Ptop=20

# Maven
mvn cnav:authors
mvn cnav:authors -Dmin-revs=3 -Dtop=20
```

### churn

Shows lines added and deleted per file. High churn files are where most development effort goes.

```bash
# Gradle
./gradlew cnavChurn
./gradlew cnavChurn -Ptop=20

# Maven
mvn cnav:churn
mvn cnav:churn -Dtop=20
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
