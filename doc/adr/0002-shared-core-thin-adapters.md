# ADR: Shared core with thin build-tool adapters

## Status

Accepted (historical — project inception)

## Context

code-navigator needs to support both Gradle and Maven. The analysis logic is the same regardless of build tool — only the entry point and parameter passing differ.

Alternatives considered:
- CLI-first architecture (evaluated and rejected — build-tool plugins provide frictionless installation, automatic versioning, and dependency resolution)
- Separate plugins per build tool (duplicates logic)
- Monolithic Gradle-only plugin (excludes Maven users)

## Decision

All analysis logic lives in `src/core/` with no build-tool dependencies. Gradle tasks (`src/gradle/`) and Maven mojos (`src/maven/`) are thin wrappers that read properties and call core code.

## Consequences

- Dual build-tool support without logic duplication
- Core is independently testable without Gradle/Maven test infrastructure
- Adding a new build tool adapter is mechanical (read params, call core, format output)
- Orchestration still gets somewhat duplicated across Task/Mojo pairs
