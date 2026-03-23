package no.f12.codenavigator.navigation

object DsmFormatter {

    fun format(matrix: DsmMatrix): String {
        val packages = matrix.packages
        if (packages.isEmpty()) return "No inter-package dependencies found."

        return buildString {
            appendLine("=== Dependency Structure Matrix (DSM) ===")
            appendLine()
            appendLine("Legend:")
            packages.forEachIndexed { i, pkg ->
                appendLine("  ${(i + 1).toString().padStart(3)}: $pkg")
            }
            appendLine()

            appendLine("Reading: row depends on column. Cell value = number of dependency references.")
            appendLine("         Cells below the diagonal indicate forward dependencies (good).")
            appendLine("         Cells above the diagonal indicate backward/cyclic dependencies (review these).")
            appendLine()

            val colWidth = maxOf(packages.size.toString().length, 4)
            val labelWidth = packages.maxOf { it.length }.coerceAtLeast(10)

            append("".padEnd(labelWidth + 6))
            packages.forEachIndexed { i, _ ->
                append((i + 1).toString().padStart(colWidth))
            }
            appendLine()

            val totalWidth = labelWidth + 6 + packages.size * colWidth
            appendLine("-".repeat(totalWidth))

            packages.forEachIndexed { rowIdx, rowPkg ->
                append("${(rowIdx + 1).toString().padStart(3)}. ${rowPkg.padEnd(labelWidth)}")
                packages.forEachIndexed { colIdx, colPkg ->
                    val cell = when {
                        rowIdx == colIdx -> "."
                        else -> matrix.cells[rowPkg to colPkg]?.toString() ?: ""
                    }
                    append(cell.padStart(colWidth))
                }
                appendLine()
            }

            val cyclicPairs = matrix.findCyclicPairs()
            if (cyclicPairs.isNotEmpty()) {
                appendLine()
                appendLine("WARNING: Cyclic dependencies detected:")
                cyclicPairs.forEach { (a, b, counts) ->
                    appendLine("  $a <-> $b  (${counts.first} refs / ${counts.second} refs)")
                    val fwd = matrix.classDependencies[a to b]
                    val bwd = matrix.classDependencies[b to a]
                    fwd?.take(5)?.forEach { (src, tgt) -> appendLine("    $a.$src -> $b.$tgt") }
                    bwd?.take(5)?.forEach { (src, tgt) -> appendLine("    $b.$src -> $a.$tgt") }
                }
            }
        }.trimEnd()
    }
}
