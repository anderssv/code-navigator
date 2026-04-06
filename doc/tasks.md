# Task Reference

The plugin provides three categories of tasks:

- **Navigation tasks** analyze compiled code and depend on the `classes` task (compilation happens automatically before analysis).
- **Analysis tasks** analyze git history and do **not** require compilation.
- **Source analysis tasks** scan source files directly and do **not** require compilation.

## Navigation Tasks

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
./gradlew cnavCallers -Ppattern=resetPassword
./gradlew cnavCallers -Ppattern=".*Service\.find.*" -Pmaxdepth=5

# Maven
mvn cnav:find-callers -Dpattern=resetPassword
mvn cnav:find-callers -Dpattern=".*Service\.find.*" -Dmaxdepth=5
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
./gradlew cnavCallees -Ppattern="Controller\.handle.*"
./gradlew cnavCallees -Ppattern="Service\.create" -Pmaxdepth=5

# Maven
mvn cnav:find-callees -Dpattern="Controller\.handle.*"
mvn cnav:find-callees -Dpattern="Service\.create" -Dmaxdepth=5
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

### layer-check

Checks architecture conformance against a `.cnav-layers.json` config. Layers are defined by class naming patterns (globs like `*Controller`, `*Service`, `*`), not by listing packages. First matching pattern wins. Detects OUTWARD violations (class depends on a higher layer) and PEER violations (class exceeds `peerLimit` for same-layer dependencies).

```bash
# Gradle — generate starter config
./gradlew cnavLayerCheck -Pinit=true

# Gradle — check conformance
./gradlew cnavLayerCheck

# Maven
mvn cnav:layer-check -Dinit=true
mvn cnav:layer-check
```

Config file (`.cnav-layers.json`):

```json
{
  "layers": [
    { "name": "wiring", "patterns": ["*Dependencies", "*TestContext", "App"], "testInfrastructure": true },
    { "name": "http", "patterns": ["*Route", "*Routes", "*Endpoint"] },
    { "name": "service", "patterns": ["*Service"], "peerLimit": 3 },
    { "name": "adapter", "patterns": ["*Repository", "*Client", "*Cache"] },
    { "name": "domain", "patterns": ["*"], "peerLimit": -1 }
  ]
}
```

Parameters:
- `-Pconfig=<path>` / `-Dconfig=<path>` — config file path (default: `.cnav-layers.json`)
- `-Pinit=true` / `-Dinit=true` — generate starter config from project dependencies
- `-Pllm=true` / `-Dllm=true` — structured output for agents

## Analysis Tasks (Git History)

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

## Source Analysis Tasks

These tasks scan source files directly and do **not** require compilation.

### size

Lists source files (Kotlin/Java) by line count, largest first. Use `-Pover` / `-Dover` to filter out small files.

```bash
# Gradle
./gradlew cnavSize
./gradlew cnavSize -Pover=100
./gradlew cnavSize -Pover=200 -Ptop=10

# Maven
mvn cnav:size
mvn cnav:size -Dover=100
mvn cnav:size -Dover=200 -Dtop=10
```
