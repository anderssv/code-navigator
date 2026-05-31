# ADR: TaskRegistry with declarative ParamDef/TaskDef DSL

## Status

Accepted (historical)

## Context

With 47+ tasks and growing, parameter definitions were being duplicated across Gradle tasks, Maven mojos, help text, and agent guidance. Inconsistencies crept in between what the help system advertised and what tasks actually accepted.

## Decision

All tasks and parameters are declaratively registered in a central `TaskRegistry` using typed `ParamDef<T>` and `TaskDef` data classes. This registry is the single source of truth.

## Consequences

- Auto-generates: `cnavAgentHelp`, `cnavHelp`, `cnavConfigHelp` output
- Parameter parsing is type-safe (`INT`, `STRING`, `BOOLEAN`)
- Build-tool rendering is consistent (Gradle `-P` vs Maven `-D`)
- JSON schemas for agent integration are always in sync
- Adding a parameter requires updating the registry — more ceremony but catches inconsistencies at compile time
- Test (`TaskRegistryTest`) asserts exact counts to prevent accidental omissions
