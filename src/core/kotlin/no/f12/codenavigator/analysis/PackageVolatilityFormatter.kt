package no.f12.codenavigator.analysis

import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.withInterpretation

object PackageVolatilityFormatter {

    internal const val VOLATILITY_INTERPRETATION = "Interpretation: Package-level volatility aggregates file changes. High-volatility packages with many outgoing dependencies are the riskiest — changes ripple outward. Stable packages (low volatility) with high fan-in are good dependency targets."

    fun noResultsHints(): List<String> = buildList {
        add("No source files with git history matched known source roots (src/main/kotlin/, src/main/java/, etc.).")
    }

    fun format(result: PackageVolatilityResult): String {
        if (result.entries.isEmpty()) return "No package volatility data found."

        val entries = result.entries
        val pkgWidth = maxOf("Package".length, entries.maxOf { it.packageName.length })
        val revWidth = maxOf("Revisions".length, entries.maxOf { it.revisions.toString().length })
        val churnWidth = maxOf("Churn".length, entries.maxOf { it.totalChurn.toString().length })
        val filesWidth = maxOf("Files".length, entries.maxOf { it.fileCount.toString().length })
        val avgWidth = maxOf("Avg Rev/File".length, entries.maxOf { "%.1f".format(it.avgRevisionsPerFile).length })

        return buildString {
            appendLine(
                "%-${pkgWidth}s  %${revWidth}s  %${churnWidth}s  %${filesWidth}s  %${avgWidth}s".format(
                    "Package", "Revisions", "Churn", "Files", "Avg Rev/File",
                ),
            )
            entries.forEachIndexed { index, entry ->
                if (index > 0) appendLine()
                append(
                    "%-${pkgWidth}s  %${revWidth}d  %${churnWidth}d  %${filesWidth}d  %${avgWidth}s".format(
                        entry.packageName, entry.revisions, entry.totalChurn, entry.fileCount,
                        "%.1f".format(entry.avgRevisionsPerFile),
                    ),
                )
            }
        }
    }

    fun formatJson(result: PackageVolatilityResult): String =
        jsonArray(result.entries) { entry ->
            jsonObject(
                "package" to entry.packageName,
                "revisions" to entry.revisions,
                "totalChurn" to entry.totalChurn,
                "fileCount" to entry.fileCount,
                "avgRevisionsPerFile" to "%.1f".format(entry.avgRevisionsPerFile).toDouble(),
            )
        }

    fun formatLlm(result: PackageVolatilityResult): String =
        result.entries.joinToString("\n") { "${it.packageName} revisions=${it.revisions} churn=${it.totalChurn} files=${it.fileCount} avgRev=${"%.1f".format(it.avgRevisionsPerFile)}" }
            .withInterpretation(VOLATILITY_INTERPRETATION)
}
