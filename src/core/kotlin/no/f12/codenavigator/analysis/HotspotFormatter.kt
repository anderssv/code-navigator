package no.f12.codenavigator.analysis

import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.withInterpretation

object HotspotFormatter {

    internal const val HOTSPOT_INTERPRETATION = "Interpretation: Files with high revision counts change frequently and are likely complexity hotspots. Prioritize refactoring files that are both hot (many revisions) and large (high churn). Cross-reference with coupling to find risky change clusters."

    fun format(hotspots: List<Hotspot>): String {
        if (hotspots.isEmpty()) return "No hotspots found."

        val fileWidth = maxOf("File".length, hotspots.maxOf { it.file.length })
        val revWidth = maxOf("Revisions".length, hotspots.maxOf { it.revisions.toString().length })
        val churnWidth = maxOf("Churn".length, hotspots.maxOf { it.totalChurn.toString().length })
        val hotspotThreshold = hotspotThreshold(hotspots)

        return buildString {
            appendLine("%-${fileWidth}s  %${revWidth}s  %${churnWidth}s".format("File", "Revisions", "Churn"))
            hotspots.forEachIndexed { index, h ->
                if (index > 0) appendLine()
                append("%-${fileWidth}s  %${revWidth}d  %${churnWidth}d".format(h.file, h.revisions, h.totalChurn))
                if (hotspotThreshold != null && h.revisions >= hotspotThreshold && h.file.startsWith("src/")) {
                    append("  ← Change hotspot — review for unclear responsibilities or missing abstractions.")
                }
            }
        }
    }

    private fun hotspotThreshold(hotspots: List<Hotspot>): Int? {
        if (hotspots.size < 5) return null
        val sorted = hotspots.map { it.revisions }.sorted()
        val median = sorted[sorted.size / 2]
        val threshold = median * 2
        return if (threshold > median) threshold else null
    }

    fun formatJson(hotspots: List<Hotspot>): String =
        jsonArray(hotspots) { h ->
            jsonObject(
                "file" to h.file,
                "revisions" to h.revisions,
                "totalChurn" to h.totalChurn,
            )
        }

    fun formatLlm(hotspots: List<Hotspot>): String =
        hotspots.joinToString("\n") { "${it.file} revisions=${it.revisions} churn=${it.totalChurn}" }
            .withInterpretation(HOTSPOT_INTERPRETATION)
}
