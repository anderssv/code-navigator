package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.PackageName

object CyclesFormatter {

    fun format(details: List<CycleDetail>, displayPrefix: PackageName = PackageName("")): String {
        if (details.isEmpty()) return "No dependency cycles found."

        return buildString {
            if (displayPrefix.isNotEmpty()) {
                appendLine("Common prefix: $displayPrefix (stripped for readability)")
                appendLine()
            }
            append(details.joinToString("\n\n") { detail ->
                formatCycle(detail, displayPrefix)
            })
        }.trimEnd()
    }

    private fun formatCycle(detail: CycleDetail, displayPrefix: PackageName): String = buildString {
        append("CYCLE: ${detail.packages.joinToString(", ")}")

        for (edge in detail.edges) {
            val weight = edge.classEdges.size
            append("\n  ${edge.from} -> ${edge.to} ($weight ref${if (weight != 1) "s" else ""}):")
            for ((src, tgt) in edge.classEdges.sortedBy { "${it.first}-${it.second}" }) {
                append("\n    ${src.stripPackagePrefix(displayPrefix)} -> ${tgt.stripPackagePrefix(displayPrefix)}")
            }
        }

        // Edge ranking
        val ranked = CycleBreakAnalyzer.rankEdges(detail)
        val breakPoints = ranked.filter { it.breaksycle }

        if (breakPoints.isNotEmpty()) {
            append("\n  ⚡ Weakest link${if (breakPoints.size > 1) "s" else ""} to break:")
            for (bp in breakPoints.take(3)) {
                append("\n    ${bp.from} -> ${bp.to} (${bp.weight} ref${if (bp.weight != 1) "s" else ""})")
            }
        }

        append("\n  → Extract shared types into a new package or invert one dependency direction.")
    }
}
