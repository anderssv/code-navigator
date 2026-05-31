# ADR: Bytecode analysis (ASM) as primary analysis engine

## Status

Accepted (historical — project inception)

## Context

code-navigator needs to analyze JVM project structure, dependencies, and call graphs. Two fundamental approaches exist:
- **Source-level analysis** — parse Kotlin/Java source files (PSI, tree-sitter, OpenRewrite)
- **Bytecode analysis** — read compiled `.class` files (ASM)

## Decision

All navigation and dependency analysis operates on compiled `.class` files via the ASM bytecode library.

## Consequences

### Positive
- Language-agnostic: works across Kotlin, Java, Scala, Groovy with one implementation
- Sees actual call targets including compiler-generated synthetics, lambdas, inline functions
- Fast: binary file scanning is much faster than parsing source
- Precise: no ambiguity about types, overloads, or generics (already resolved by compiler)

### Negative
- Requires compilation before analysis (must run after `compileKotlin`)
- Cannot analyze uncompiled/broken code
- Loses source-level constructs (comments, formatting, named arguments)
- Cannot perform source transformations directly (needs separate rewrite layer)

### Exception
The `analysis/` package (git-history based: hotspots, churn, change coupling) works without compilation — it reads git log output directly.
