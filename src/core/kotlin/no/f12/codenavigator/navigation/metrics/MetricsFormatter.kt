package no.f12.codenavigator.navigation.metrics

import no.f12.codenavigator.formatting.JsonRaw
import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import java.util.Locale

object MetricsFormatter {

    fun format(result: MetricsResult): String = buildString {
        appendLine("Project Metrics")
        appendLine("===============")
        appendLine("  Classes:       ${result.totalClasses}")
        appendLine("  Packages:      ${result.packageCount}")
        appendLine("  Avg fan-in:    ${formatDecimal(result.averageFanIn)}")
        appendLine("  Avg fan-out:   ${formatDecimal(result.averageFanOut)}")
        appendLine("  Cycles:        ${result.cycleCount}")
        appendLine("  Dead classes:  ${result.deadClassCount}")
        appendLine("  Dead methods:  ${result.deadMethodCount}")
        if (result.topHotspots.isNotEmpty()) {
            appendLine()
            appendLine("Top Hotspots")
            appendLine("------------")
            val fileWidth = maxOf("File".length, result.topHotspots.maxOf { it.file.length })
            val revWidth = maxOf("Revs".length, result.topHotspots.maxOf { it.revisions.toString().length })
            val churnWidth = maxOf("Churn".length, result.topHotspots.maxOf { it.totalChurn.toString().length })
            appendLine("  %-${fileWidth}s  %${revWidth}s  %${churnWidth}s".format("File", "Revs", "Churn"))
            result.topHotspots.forEachIndexed { index, h ->
                if (index > 0) appendLine()
                append("  %-${fileWidth}s  %${revWidth}d  %${churnWidth}d".format(h.file, h.revisions, h.totalChurn))
            }
        }
    }

    private fun formatDecimal(value: Double): String =
        String.format(Locale.US, "%.1f", value)

    fun formatJson(metrics: MetricsResult): String =
        jsonObject(
            "totalClasses" to metrics.totalClasses,
            "packageCount" to metrics.packageCount,
            "averageFanIn" to metrics.averageFanIn,
            "averageFanOut" to metrics.averageFanOut,
            "cycleCount" to metrics.cycleCount,
            "deadClassCount" to metrics.deadClassCount,
            "deadMethodCount" to metrics.deadMethodCount,
            "topHotspots" to JsonRaw(jsonArray(metrics.topHotspots) { h ->
                jsonObject(
                    "file" to h.file,
                    "revisions" to h.revisions,
                    "totalChurn" to h.totalChurn,
                )
            }),
        )

    fun formatLlm(metrics: MetricsResult): String = buildString {
        append("classes=${metrics.totalClasses}")
        append(" packages=${metrics.packageCount}")
        append(" avg-fan-in=${"%.1f".format(Locale.US, metrics.averageFanIn)}")
        append(" avg-fan-out=${"%.1f".format(Locale.US, metrics.averageFanOut)}")
        append(" cycles=${metrics.cycleCount}")
        append(" dead-classes=${metrics.deadClassCount}")
        append(" dead-methods=${metrics.deadMethodCount}")
        if (metrics.topHotspots.isNotEmpty()) {
            appendLine()
            appendLine("hotspots:")
            append(metrics.topHotspots.joinToString("\n") { "${it.file} revisions=${it.revisions} churn=${it.totalChurn}" })
        }
    }
}
