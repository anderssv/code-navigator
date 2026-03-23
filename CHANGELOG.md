# Changelog

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
