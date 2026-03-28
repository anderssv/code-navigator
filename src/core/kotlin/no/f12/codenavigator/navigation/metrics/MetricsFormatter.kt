package no.f12.codenavigator.navigation.metrics

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
}
