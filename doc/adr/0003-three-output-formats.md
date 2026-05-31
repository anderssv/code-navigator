# ADR: Three output formats (TEXT/JSON/LLM)

## Status

Accepted (historical)

## Context

code-navigator's primary audience is AI coding agents, but humans and CI scripts also use it. Different consumers need different output shapes:
- Agents need interpretation hints, workflow guidance, and structured context
- Scripts need machine-parseable structured data
- Humans need scannable tables

Alternatives considered:
- JSON only (universal but agents struggle to interpret raw data without context)
- Text only (simple but not programmatically consumable)

## Decision

Every task produces output in three formats controlled by `-Pformat=`:
- **TEXT** — human-readable tables
- **JSON** — machine-parseable structured data
- **LLM** — markdown with interpretation sections, no-results hints, schemas, and workflow guidance

## Consequences

- Agents get rich contextual output that helps them decide next steps
- CI pipelines parse JSON for automated checks
- Every new feature requires implementing three formatter paths
- Formatters have grown large — per-feature splitting is planned
