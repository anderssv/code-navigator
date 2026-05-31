# ADR: Strict separation of parsing, resolution, and formatting

## Status

Accepted (historical — project inception)

## Context

Analysis tools often interleave data collection, query logic, and output formatting in a single pass. This makes it hard to add new output formats, test individual layers, or reuse resolution logic.

## Decision

Code is organized into three independently testable layers:

1. **Parsing** — reads raw input (bytecode, git log) → data structure. No formatting, no output.
2. **Resolution** — takes parsed data + query → result structure. No formatting, no I/O.
3. **Formatting** — takes result structure → text/JSON/LLM. No graph walking, no query logic.

Formatters never reach back into parsed data. When two formatters need the same data, they consume the same result structure.

## Consequences

- New output format = new formatter only, no duplicated resolution logic
- Bugs are isolated to one layer
- Tests per layer are fast and focused
- Result structures must carry all data any formatter might need
- Visible in the per-feature triple pattern: `*Builder.kt` + `*Config.kt` + `*Formatter.kt`
