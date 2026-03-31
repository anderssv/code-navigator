# Code Navigator

A Gradle and Maven plugin for **reliable code navigation** and **code smell analysis** in JVM projects. Provides structural navigation (class listing, symbol search, call graph traversal, class detail inspection, interface implementation lookup, package dependency analysis) and behavioral analysis from git history (hotspots, change coupling, code age, author distribution, churn).

Built primarily for use by **coding agents** (AI assistants that write and refactor code), though it is equally useful for human developers. Works with any JVM language (Kotlin, Java, Scala, etc.) since it analyzes compiled output rather than source text. The git history analysis is inspired by [Code Maat](https://github.com/adamtornhill/code-maat) and the ideas in Adam Tornhill's *Your Code as a Crime Scene*.

## Getting started

Copy-paste this to your agent:

**Gradle:**

> Add the no.f12.code-navigator Gradle plugin to this project. After installing, run `./gradlew cnavAgentHelp` to get full usage instructions optimized for AI agents, and `./gradlew cnavHelp` to see all available tasks and their parameters.
>
> Then add a "Code Navigator (cnav)" section to AGENTS.md documenting the plugin. It should include:
 > - A short description of what it does (reliable code navigation + code smell/complexity analysis + git history)
> - A nudge to prefer cnav over grep/ripgrep for finding callers, implementations, and dependencies
> - A note to run cnavAgentHelp for full instructions
> - A compact command list showing all available tasks with one-line comments (navigation tasks and git history tasks), grouped by whether they require compilation

**Maven:**

> Add the no.f12 code-navigator-maven-plugin to this project. After installing, run `mvn cnav:agent-help` to get full usage instructions optimized for AI agents, and `mvn cnav:help` to see all available goals and their parameters.
>
> Then add a "Code Navigator (cnav)" section to AGENTS.md documenting the plugin. It should include:
> - A short description of what it does (reliable code navigation + code smell/complexity analysis + git history)
> - A nudge to prefer cnav over grep/ripgrep for finding callers, implementations, and dependencies
> - A note to run `mvn cnav:agent-help` for full instructions
> - A compact command list showing all available goals with one-line comments (navigation goals and git history goals), grouped by whether they require compilation

The `cnavAgentHelp` task prints agent-optimized instructions covering workflow, parameters, JSON schemas, and output extraction tips. You can also use its output as the starting point for a custom agent skill if your tool supports it (e.g. a Claude Code skill or Cursor rule).

## Why use Code Navigator?

Text search (grep, ripgrep) requires iterative discovery. You search for `cache.get(`, find some results, then realize you missed the Kotlin safe-call `cache?.get(`, then extension functions, then delegation patterns. Each iteration requires you to know what syntactic variant you haven't tried yet — and you can't know what you've missed until you find it by accident.

Code Navigator sidesteps this entirely. All syntax variants compile to the same call. One `cnavCallers` query returns all call sites — complete, correct, no false positives, no missed calls.

For an agent, each grep iteration is a tool call round-trip. For a human, each is a context switch. Code Navigator eliminates the iterative discovery loop: you get the full call graph from one query.

## Requirements

- **JDK 17** or newer (both for running the plugin and for compiling your project)
- **Gradle 9.x** or **Maven 3.9+**

## Installation

### Gradle

Apply the plugin in your `build.gradle.kts`:

```kotlin
plugins {
    id("no.f12.code-navigator") version "0.1.46"
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
            <version>0.1.46</version>
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
    <version>0.1.46</version>
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

Navigation tasks analyze compiled code (compilation happens automatically). Analysis tasks analyze git history and do not require compilation. All tasks support `-Pformat=json` (Gradle) / `-Dformat=json` (Maven) and `-Pllm=true` / `-Dllm=true` for compact agent output.

See [doc/tasks.md](doc/tasks.md) for detailed usage with examples.

| Task (Gradle / Maven) | Description |
|---|---|
| `cnavHelp` / `cnav:help` | Show help text for all tasks |
| `cnavAgentHelp` / `cnav:agent-help` | Agent-optimized usage instructions |
| `cnavHelpConfig` / `cnav:config-help` | List all configuration parameters |
| **Code navigation** | |
| `cnavListClasses` / `cnav:list-classes` | List all classes with source files |
| `cnavFindClass` / `cnav:find-class` | Find classes by regex pattern |
| `cnavFindSymbol` / `cnav:find-symbol` | Find methods and fields by regex |
| `cnavClass` / `cnav:class-detail` | Show class signature (fields, methods, interfaces) |
| `cnavCallers` / `cnav:find-callers` | Call tree: who calls this method? |
| `cnavCallees` / `cnav:find-callees` | Call tree: what does this method call? |
| `cnavInterfaces` / `cnav:find-interfaces` | Find all implementors of an interface |
| `cnavTypeHierarchy` / `cnav:type-hierarchy` | Show inheritance tree (up and down) |
| `cnavDeps` / `cnav:package-deps` | Package-level dependency edges |
| `cnavDsm` / `cnav:dsm` | Dependency Structure Matrix with cycle detection |
| `cnavCycles` / `cnav:cycles` | Detect dependency cycles (Tarjan's SCC) |
| `cnavUsages` / `cnav:find-usages` | Find references to types, methods, fields |
| `cnavRank` / `cnav:rank` | Rank types by importance (PageRank) |
| `cnavDead` / `cnav:dead` | Detect dead code with framework-aware filtering |
| `cnavComplexity` / `cnav:complexity` | Fan-in/fan-out complexity per class |
| `cnavMetrics` / `cnav:metrics` | Quick project health snapshot |
| `cnavAnnotations` / `cnav:annotations` | Find classes/methods by annotation |
| `cnavFindStringConstant` / `cnav:find-string-constant` | Search string literals in compiled code |
| **Git history analysis** | |
| `cnavHotspots` / `cnav:hotspots` | Files ranked by change frequency |
| `cnavCoupling` / `cnav:coupling` | Files that change together (temporal coupling) |
| `cnavAge` / `cnav:code-age` | Time since last change per file |
| `cnavAuthors` / `cnav:authors` | Distinct contributors per file |
| `cnavChurn` / `cnav:churn` | Lines added/deleted per file |

### Dead code detection and framework awareness

`cnavDead` finds unreferenced classes and methods in your project. It includes built-in awareness of common JVM frameworks — classes annotated with framework entry-point annotations (e.g. `@RestController`, `@Scheduled`, `@Entity`, `@Test`) are automatically excluded from results, since they are invoked by the framework rather than from project code.

Supported framework presets (all active by default): **Spring**, **Quarkus**, **JPA**, **Jackson**, **JAX-RS**, **CDI**, **MicroProfile**, **gRPC**, **Jakarta**, **Bean Validation**, and **JUnit**. Use `-Pexclude-framework=<name>` to disable a specific preset, or `-Pexclude-framework=ALL` to disable all framework filtering.

## Agent setup

See [doc/agent-setup.md](doc/agent-setup.md) for Claude Code permission rules and other agent configuration.

## How it works

See [doc/how-it-works.md](doc/how-it-works.md) for details on how the analysis works, including call graph construction, git log parsing, caching, and filtering.

## Building from source

```bash
./gradlew build
```

Requires Gradle 9.4+ (included via the Gradle wrapper).

## License

See [LICENSE](LICENSE) for details.
