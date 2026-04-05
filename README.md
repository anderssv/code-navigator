# Code Navigator

A Gradle and Maven plugin for **code navigation**, **coupling analysis**, and **git activity analysis** in JVM projects. Works with any JVM language (Kotlin, Java, Scala, etc.) since it analyzes compiled bytecode and git history rather than source text.

Built primarily for **coding agents** (AI assistants that write and refactor code), though equally useful for human developers. The git history analysis is inspired by [Code Maat](https://github.com/adamtornhill/code-maat) and Adam Tornhill's *Your Code as a Crime Scene*.

## Getting started

Add the plugin, then ask your agent to run the help task. It will figure out the rest.

**Gradle** — add to `build.gradle.kts`, then tell your agent:

```kotlin
plugins {
    id("no.f12.code-navigator") version "0.1.51"
}
```

> Run `./gradlew cnavAgentHelp` and follow the instructions.

**Maven** — add to `pom.xml`, then tell your agent:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>no.f12</groupId>
            <artifactId>code-navigator-maven-plugin</artifactId>
            <version>0.1.51</version>
        </plugin>
    </plugins>
</build>
```

> Run `mvn cnav:agent-help` and follow the instructions.

The `cnavAgentHelp` output covers workflows, parameters, JSON schemas, and output tips. You can also use it as the basis for a custom agent skill (e.g. a Claude Code skill or Cursor rule).

## Why use Code Navigator?

Text search (grep, ripgrep) requires iterative discovery. You search for `cache.get(`, find some results, then realize you missed the Kotlin safe-call `cache?.get(`, then extension functions, then delegation patterns. Each iteration requires you to know what syntactic variant you haven't tried yet.

Code Navigator sidesteps this entirely. All syntax variants compile to the same bytecode call. One `cnavCallers` query returns all call sites — complete, correct, no false positives, no missed calls. For an agent, each grep iteration is a tool call round-trip. Code Navigator eliminates that loop.

## Requirements

- **JDK 17** or newer
- **Gradle 9.x** or **Maven 3.9+**

## Tasks

All tasks support `-Pformat=json` / `-Dformat=json` and `-Pllm=true` / `-Dllm=true` for compact agent output. See [doc/tasks.md](doc/tasks.md) for detailed usage with examples.

| Task (Gradle / Maven) | Description |
|---|---|
| **Help** | |
| `cnavHelp` / `cnav:help` | Show help text for all tasks |
| `cnavAgentHelp` / `cnav:agent-help` | Agent-optimized usage instructions |
| `cnavHelpConfig` / `cnav:config-help` | List all configuration parameters |
| **Code navigation** (requires compilation) | |
| `cnavListClasses` / `cnav:list-classes` | List all classes with source files |
| `cnavFindClass` / `cnav:find-class` | Find classes by regex pattern |
| `cnavFindSymbol` / `cnav:find-symbol` | Find methods and fields by regex |
| `cnavClass` / `cnav:class-detail` | Show class signature (fields, methods, interfaces) |
| `cnavContext` / `cnav:context` | Full context for a class: detail, callers, callees, interfaces |
| `cnavCallers` / `cnav:find-callers` | Call tree: who calls this method? |
| `cnavCallees` / `cnav:find-callees` | Call tree: what does this method call? |
| `cnavInterfaces` / `cnav:find-interfaces` | Find all implementors of an interface |
| `cnavTypeHierarchy` / `cnav:type-hierarchy` | Show inheritance tree (up and down) |
| `cnavUsages` / `cnav:find-usages` | Find references to types, methods, fields |
| `cnavAnnotations` / `cnav:annotations` | Find classes/methods by annotation |
| `cnavFindStringConstant` / `cnav:find-string-constant` | Search string literals in compiled code |
| `cnavDead` / `cnav:dead` | Detect dead code with framework-aware filtering |
| `cnavRank` / `cnav:rank` | Rank types by importance (PageRank) |
| `cnavComplexity` / `cnav:complexity` | Fan-in/fan-out complexity per class |
| `cnavMetrics` / `cnav:metrics` | Quick project health snapshot |
| **Package structure** (requires compilation) | |
| `cnavDeps` / `cnav:package-deps` | Package-level dependency edges |
| `cnavDsm` / `cnav:dsm` | Dependency Structure Matrix with cycle detection |
| `cnavCycles` / `cnav:cycles` | Detect dependency cycles (Tarjan's SCC) |
| `cnavStrength` / `cnav:strength` | Classify integration strength of inter-package dependencies |
| `cnavDistance` / `cnav:distance` | Structural distance between coupled packages |
| **Git activity analysis** (no compilation needed) | |
| `cnavHotspots` / `cnav:hotspots` | Files ranked by change frequency |
| `cnavCoupling` / `cnav:coupling` | Files that change together (temporal coupling) |
| `cnavAge` / `cnav:code-age` | Time since last change per file |
| `cnavAuthors` / `cnav:authors` | Distinct contributors per file |
| `cnavChurn` / `cnav:churn` | Lines added/deleted per file |
| `cnavVolatility` / `cnav:volatility` | Package-level volatility (change frequency and churn) |
| **Hybrid and composite** | |
| `cnavChangedSince` / `cnav:changed-since` | Blast radius of changes since a git ref (changed classes + their callers) |
| `cnavBalance` / `cnav:balance` | Balanced coupling analysis: strength x distance x volatility |

### Dead code detection and framework awareness

`cnavDead` finds unreferenced classes and methods. It includes built-in awareness of common JVM frameworks — classes annotated with framework entry-point annotations (e.g. `@RestController`, `@Scheduled`, `@Entity`, `@Test`) are automatically excluded.

Supported presets (all active by default): **Spring**, **Quarkus**, **JPA**, **Jackson**, **JAX-RS**, **CDI**, **MicroProfile**, **gRPC**, **Jakarta**, **Bean Validation**, and **JUnit**. Use `-Pexclude-framework=<name>` to disable a specific preset, or `-Pexclude-framework=ALL` to disable all.

## Configuration

No configuration is needed. The plugin works out of the box. You can optionally set persistent defaults:

**Gradle** (`build.gradle.kts`):
```kotlin
codeNavigator {
    rootPackage = "com.example"  // default: "" (all packages)
}
```

**Maven** (`pom.xml`):
```xml
<configuration>
    <rootPackage>com.example</rootPackage>
</configuration>
```

Run `cnavHelpConfig` / `cnav:config-help` to see all available parameters. CLI flags (`-P` / `-D`) always override the config block.

## Further reading

- [doc/tasks.md](doc/tasks.md) — detailed task usage with examples
- [doc/agent-setup.md](doc/agent-setup.md) — Claude Code permission rules and agent configuration
- [doc/how-it-works.md](doc/how-it-works.md) — how the analysis works (call graph construction, git log parsing, caching, filtering)

## Building from source

```bash
./gradlew build
```

Requires Gradle 9.4+ (included via the Gradle wrapper).

## License

See [LICENSE](LICENSE) for details.
