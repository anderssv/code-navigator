# ADR: Synthetic bytecode tests + real test-project

## Status

Accepted (historical)

## Context

Testing bytecode analysis requires `.class` files with specific patterns. Two approaches:
- Generate synthetic bytecode with ASM ClassWriter (precise control, fast)
- Compile real Kotlin/Java code and scan the output (realistic, but less controlled)

## Decision

Use both:
- **Most tests** generate synthetic `.class` files using ASM `ClassWriter` for fine-grained control over exact bytecode patterns.
- **`test-project/`** contains real Kotlin source compiled by Gradle, used when testing patterns that are hard to reproduce synthetically (INVOKEDYNAMIC from lambdas, method references, inline functions, coroutines).

Tests using the test-project call `buildTestProject()` to ensure compilation is up to date.

## Consequences

- Tests are fast (~15s for 2,200+ tests) because synthetic bytecode requires no compilation
- Exact bytecode patterns can be targeted (e.g., specific INVOKE opcodes)
- test-project catches real compiler behavior that synthetic bytecode might miss
- `FileSizeScannerTest` asserts exact file count (36) to detect unintended test-project changes
- Adding new test fixtures requires updating both the fixture and the assertion count
