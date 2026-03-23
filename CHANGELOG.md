# Changelog

## 0.1.11

- Add `BuildTool` enum for build-tool-aware help text — Gradle users see `./gradlew cnavXxx -Pparam=value`, Maven users see `mvn cnav:goal -Dparam=value`
- Make `HelpText`, `AgentHelpText`, and `ConfigHelpText` accept a `BuildTool` parameter
- Gradle tasks and Maven Mojos now pass the correct build tool for contextual help output
- Set Maven plugin `goalPrefix` to `cnav` (previously derived as `code-navigator`)
- Add `test-project-maven/` for end-to-end Maven plugin testing
- Add Maven examples alongside Gradle in README.md Tasks section

## 0.1.10

- Add Maven plugin (`code-navigator-maven-plugin`) with full feature parity — all 17 goals available via `mvn cnav:<goal>`
- Restructure source layout to separate roots: `src/core/kotlin/` (shared), `src/gradle/kotlin/`, `src/maven/kotlin/`
- Extract shared Config data classes for all tasks (used by both Gradle and Maven parameter parsing)
- Extract `ClassDetailScanner` from `FindClassDetailTask` for reuse across build tools
- Configure Maven Central publishing with GPG signing, source jars, and Dokka javadoc
- Update release process in AGENTS.md for dual Gradle + Maven publishing

## 0.1.9

- Fix: cnavDsm returning empty results — rename `-Pdepth` to `-Pdsm-depth` to avoid Gradle built-in property collision
- Fix: stale `-Pdepth` references in README for cnavCallers/cnavCallees (should be `-Pmaxdepth`)
- Add integration test for DsmDependencyExtractor against real compiled Kotlin classes

## 0.1.8

- Port DSM (Dependency Structure Matrix) from dsm-plugin into `navigation` package
  - Bytecode scanning with `DsmDependencyExtractor` (ASM-based)
  - `DsmMatrixBuilder` with cyclic dependency detection
  - Text, HTML, JSON, and LLM output formats
  - `cnavDsm` task with `-Proot-package=`, `-Pdsm-depth=`, `-Pdsm-html=` properties
- Enable git rename tracking by default (`-M` flag), opt out with `-Pno-follow`
  - `GitLogParser` handles both full-path and brace rename syntax
- Add `cnavHelpConfig` task listing all `-P` configuration parameters with defaults
- Update HelpText, AgentHelpText, README, and AGENTS.md

## 0.1.7

- Add 5 git history analysis tasks (no compilation required):
  - `cnavHotspots` — files ranked by revision count and churn
  - `cnavCoupling` — temporal coupling between files
  - `cnavAge` — time since last change per file
  - `cnavAuthors` — distinct contributors per file
  - `cnavChurn` — lines added/deleted per file
- Add shared git infrastructure: `GitLogParser`, `GitLogRunner`
- Reorganize codebase into `navigation` (bytecode) and `analysis` (git history) subpackages
- Update JSON and LLM formatters with analysis output support
- Update `cnavHelp` and `cnavAgentHelp` with git task documentation

## 0.1.6

- Update README with `cnavAgentHelp` as primary agent entry point
- Add skill mention: agentHelp output can be used as starting point for a custom agent skill
- Add Maven plugin support to plan.md

## 0.1.5

- Add compact LLM output format (`-Pllm=true`) for token-efficient output across all tasks
- Wrap output with `---CNAV_BEGIN---` / `---CNAV_END---` markers for reliable extraction from Gradle stdout

## 0.1.4

- Wrap JSON output with markers to separate it from Gradle lifecycle noise

## 0.1.3

- Update AGENTS.md release process to include Gradle Plugin Portal publishing

## 0.1.2

- Add JSON output format (`-Pformat=json`) for all tasks
- Add `cnavAgentHelp` task with workflow guidance, JSON schemas, and jq examples
- Add `-Pprojectonly=true` flag to filter stdlib/JDK noise in cnavCallers, cnavCallees, cnavDeps
- Add reverse dependency view (`-Preverse=true`) for cnavDeps
- Add test source set support (`-Pincludetest=true`) for cnavInterfaces
- Fix: rename `-Pdepth` to `-Pmaxdepth` to avoid Gradle built-in property collision
- Refactor: extract CallTreeBuilder to separate tree resolution from formatting
- Add AGENTS.md with code structure principles
- Add plan.md with feature roadmap

## 0.1.1

- Refactor cache layer: atomic writes, corruption safety, shared freshness checking
- Add disk caching for call graph, symbol index, and interface registry
