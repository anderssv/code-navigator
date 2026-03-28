# How it works

1. **Bytecode scanning** -- Walks all `.class` files from the main source set's output directories. Uses ASM's `ClassVisitor` and `MethodVisitor` to extract class metadata, symbols, and call edges.

2. **Call graph construction** -- Builds a bidirectional call graph (`caller -> callees` and `callee -> callers`) from method invocation instructions in bytecode.

3. **Git log parsing** -- Runs `git log --numstat` and parses the output to extract per-file revision counts, author lists, and line-level churn data.

4. **Caching** -- Class index results are cached to disk with timestamp-based freshness checking, so repeated queries on unchanged code are fast.

5. **Filtering** -- All search tasks accept regex patterns and match case-insensitively against relevant fields (class name, source path, symbol name, package name).
