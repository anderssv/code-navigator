# Code Navigator

A Gradle and Maven plugin for **code navigation**, **coupling analysis**, and **git activity analysis** in JVM projects. Works with any JVM language (Kotlin, Java, Scala, etc.) since it analyzes compiled bytecode and git history rather than source text.

Built primarily for **coding agents** (AI assistants that write and refactor code), though equally useful for human developers. The git history analysis is inspired by [Code Maat](https://github.com/adamtornhill/code-maat) and Adam Tornhill's *Your Code as a Crime Scene*.

**Opinionated about architecture:** Code Navigator's analysis tasks are designed to measure and guide codebases toward **hexagonal architecture** (ports & adapters). They quantify how well your code separates domain logic from infrastructure, identify where coupling crosses boundaries, and suggest concrete improvements — from package cohesion scoring to class move suggestions.

This is an attempt at making a useful tool for coding agents when navigating JVM based projects. It is tested on production code, but also some open source projects. If you find that something is missing or wrong, a public project to run on that illustrates the problem is appreciated.

Refactoring operations try to be as deterministic as possible, but sometimes resort to heuristics when full type resolution is unavailable. Your LLM should be able to solve the remaining issues if the result is incorrect.

## Getting started

Copy-paste this to your agent:

> Install the no.f12.code-navigator plugin and run cnavAgentHelp to figure out what to have in AGENTS.md and update it.

That's it. The agent will install the plugin, run the help task, and set up its own instructions. If you prefer to install manually:

**Upgrading?** Tell your agent:

> Upgrade code-navigator to the newest version, run cnavAgentHelp --section=install, and update the code-navigator section in AGENTS.md with the latest instructions.

**Gradle** (`build.gradle.kts`):
```kotlin
plugins {
    id("no.f12.code-navigator") version "0.1.99"
}
```

**Maven** (`pom.xml`):
```xml
<build>
    <plugins>
        <plugin>
            <groupId>no.f12</groupId>
            <artifactId>code-navigator-maven-plugin</artifactId>
            <version>0.1.89</version>
        </plugin>
    </plugins>
</build>
```

The `cnavAgentHelp` output covers workflows, parameters, JSON schemas, and output tips. You can also use it as the basis for a custom agent skill (e.g. a Claude Code skill or Cursor rule).

## Why use Code Navigator?

Text search (grep, ripgrep) requires iterative discovery. You search for `cache.get(`, find some results, then realize you missed the Kotlin safe-call `cache?.get(`, then extension functions, then delegation patterns. Each iteration requires you to know what syntactic variant you haven't tried yet.

Code Navigator sidesteps this entirely. All syntax variants compile to the same bytecode call. One `cnavCallers` query returns all call sites — complete, correct, no false positives, no missed calls. For an agent, each grep iteration is a tool call round-trip. Code Navigator eliminates that loop.

## Requirements

- **JDK 17** or newer
- **Gradle 9.x** or **Maven 3.9+**

## Tasks

All tasks support `--format=json` / `-Dformat=json` and `--llm` / `-Dllm=true` for compact agent output. See [doc/tasks.md](doc/tasks.md) for detailed usage with examples.

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
| `cnavCohesion` / `cnav:cohesion` | Package cohesion scoring (internal vs external edges) |
| `cnavMoveSuggest` / `cnav:move-suggest` | Suggest misplaced classes based on dependency gravity |
| `cnavWhyDepends` / `cnav:why-depends` | Explain why one package depends on another (class-level edges) |
| `cnavLayerCheck` / `cnav:layer-check` | Architecture conformance check (hexagonal layers) |
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
| **Source analysis** (no compilation needed) | |
| `cnavSize` / `cnav:size` | Source files ranked by line count |
| **Refactoring** (source-level) | |
| `cnavRenameParam` / `cnav:rename-param` | Rename a method parameter with cascade detection |
| `cnavRenameMethod` / `cnav:rename-method` | Rename a method and update all call sites (including interface implementations) |
| `cnavRenameProperty` / `cnav:rename-property` | Rename a property and update all access sites (constructor args, copy(), property access) |
| `cnavMoveClass` / `cnav:move-class` | Move and/or rename a class, updating all references |

### Dead code detection and framework awareness

`cnavDead` finds unreferenced classes and methods. It includes built-in awareness of common JVM frameworks — classes annotated with framework entry-point annotations (e.g. `@RestController`, `@Scheduled`, `@Entity`, `@Test`) are automatically excluded.

Supported presets (all active by default): **Spring**, **Quarkus**, **JPA**, **Jackson**, **JAX-RS**, **CDI**, **MicroProfile**, **gRPC**, **Jakarta**, **Bean Validation**, and **JUnit**. Use `--exclude-framework=<name>` to disable a specific preset, or `--exclude-framework=ALL` to disable all.

### Where to start improving code quality

Run these tasks in order to assess and improve your codebase structure:

1. **`cnavMetrics`** — Quick health snapshot (cycles, dead code, coupling stats)
2. **`cnavCycles`** — Find circular package dependencies (the #1 structural problem)
3. **`cnavCohesion`** — Find packages with low internal cohesion (split candidates)
4. **`cnavMoveSuggest`** — Find classes that belong in a different package
5. **`cnavStrength`** — Are dependencies going through contracts or concrete classes?
6. **`cnavBalance`** — Combined verdict: which package pairs need attention?
7. **`cnavLayerCheck`** — Enforce hexagonal layers (define in `.cnav-layers.json`)

The ideal hexagonal structure:
- **Domain packages**: COHESIVE, high internal edges, few external, no framework deps
- **Port packages**: THIN_LAYER (interfaces only), depended on via CONTRACT strength
- **Adapter packages**: Depend inward on ports, never referenced by domain
- **No cycles** between layers; dependencies flow inward only

Run `cnavAgentHelp --section=getting-started` for detailed guidance with examples.

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

Run `cnavHelpConfig` / `cnav:config-help` to see all available parameters. CLI options (`--option` for Gradle / `-D` for Maven) always override the config block.

## Further reading

- [doc/tasks.md](doc/tasks.md) — detailed task usage with examples
- [doc/agent-setup.md](doc/agent-setup.md) — Claude Code permission rules and agent configuration
- [doc/how-it-works.md](doc/how-it-works.md) — how the analysis works (call graph construction, git log parsing, caching, filtering)

## Building from source

```bash
./gradlew build
```

Requires Gradle 9.4+ (included via the Gradle wrapper).

## Acknowledgements

The PSI-based refactoring architecture was inspired by Audun Fauchald Strand's [martin](https://github.com/audunstrand/martin) — a CLI tool for semantically-correct Kotlin refactorings using the embedded Kotlin compiler frontend. Its clean design demonstrated that `kotlin-compiler-embeddable` is viable outside an IDE and influenced our two-phase approach (bytecode location finding + PSI editing).

## License

See [LICENSE](LICENSE) for details.
