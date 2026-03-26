package no.f12.codenavigator.navigation

object CyclesFormatter {

    fun format(details: List<CycleDetail>): String {
        if (details.isEmpty()) return "No dependency cycles found."

        return details.joinToString("\n\n") { detail ->
            buildString {
                append("CYCLE: ${detail.packages.joinToString(", ")}")
                for (edge in detail.edges) {
                    append("\n  ${edge.from} -> ${edge.to}:")
                    for ((src, tgt) in edge.classEdges.sortedBy { "${it.first}-${it.second}" }) {
                        append("\n    $src -> $tgt")
                    }
                }
            }
        }
    }
}
