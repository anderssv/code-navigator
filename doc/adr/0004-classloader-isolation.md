# ADR: Worker/classloader isolation for OpenRewrite tasks

## Status

Accepted (historical)

## Context

OpenRewrite has a large dependency tree that can conflict with the host project's classpath and Gradle's own dependencies. Bundling OpenRewrite in the plugin JAR would make the plugin heavy and risk version conflicts.

## Decision

Refactoring tasks use Gradle's `WorkerExecutor.classLoaderIsolation` to run OpenRewrite in an isolated classloader. OpenRewrite is declared `compileOnly` — it's resolved and loaded at runtime only when refactoring tasks execute.

Parameters are passed via `WorkParameters` interfaces. Results are serialized to JSON temp files and read back by the task.

## Consequences

- Plugin ships lightweight (ASM only, ~2MB) despite OpenRewrite being ~40MB
- No classpath conflicts between OpenRewrite and host project
- Requires boilerplate: WorkAction classes, parameter interfaces, temp file serialization
- Test configuration needs the OpenRewrite classpath explicitly
