package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.core.PackageName

object CyclesFormatter {

    fun format(details: List<CycleDetail>, displayPrefix: PackageName = PackageName("")): String {
        if (details.isEmpty()) return "No dependency cycles found."

        return buildString {
            if (displayPrefix.isNotEmpty()) {
                appendLine("Common prefix: $displayPrefix (stripped for readability)")
                appendLine()
            }
            append(details.joinToString("\n\n") { detail ->
                buildString {
                    append("CYCLE: ${detail.packages.joinToString(", ")}")
                    for (edge in detail.edges) {
                        append("\n  ${edge.from} -> ${edge.to}:")
                        for ((src, tgt) in edge.classEdges.sortedBy { "${it.first}-${it.second}" }) {
                            append("\n    ${src.stripPackagePrefix(displayPrefix)} -> ${tgt.stripPackagePrefix(displayPrefix)}")
                        }
                    }
                }
            })
        }.trimEnd()
    }
}
