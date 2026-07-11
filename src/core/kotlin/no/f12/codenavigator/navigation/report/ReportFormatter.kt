package no.f12.codenavigator.navigation.report

import no.f12.codenavigator.formatting.JsonRaw
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.navigation.deadcode.DeadCodeFormatter
import no.f12.codenavigator.navigation.dsm.CohesionFormatter
import no.f12.codenavigator.navigation.dsm.CyclesFormatter
import no.f12.codenavigator.navigation.dsm.MoveSuggestFormatter
import no.f12.codenavigator.navigation.dsm.RingFormatter
import no.f12.codenavigator.navigation.metrics.MetricsFormatter

/**
 * Renders [ReportData] three ways. TEXT/DIFF reproduces the original sectioned markdown; JSON and LLM
 * compose each sub-feature's own `formatJson`/`formatLlm` under a named key so the composite report
 * has a real structured shape instead of echoing the markdown blob for every format.
 */
object ReportFormatter {

    fun format(data: ReportData): String {
        val sections = mutableListOf<String>()
        sections += "## Metrics\n\n${MetricsFormatter.format(data.metrics)}"
        sections += if (data.cycles.isNotEmpty()) {
            "## Cycles\n\n${CyclesFormatter.format(data.cycles, displayPrefix = data.displayPrefix)}"
        } else {
            "## Cycles\n\nNo package cycles detected."
        }
        sections += "## Rings\n\n${RingFormatter.format(data.rings)}"
        sections += if (data.moveSuggestions != null) {
            "## Move Suggestions\n\n${MoveSuggestFormatter.format(data.moveSuggestions)}"
        } else {
            "## Move Suggestions\n\nNo misplaced classes detected."
        }
        if (data.cohesion != null) {
            sections += "## Cohesion\n\n${CohesionFormatter.format(data.cohesion)}"
        }
        sections += if (data.deadCode.isNotEmpty()) {
            "## Dead Code (top ${data.topN})\n\n${DeadCodeFormatter.format(data.deadCode.take(data.topN), data.scope)}"
        } else {
            "## Dead Code\n\nNo dead code detected."
        }
        return sections.joinToString("\n\n---\n\n")
    }

    fun formatJson(data: ReportData): String = jsonObject(
        "metrics" to JsonRaw(MetricsFormatter.formatJson(data.metrics)),
        "cycles" to JsonRaw(CyclesFormatter.formatJson(data.cycles, displayPrefix = data.displayPrefix)),
        "rings" to JsonRaw(RingFormatter.formatJson(data.rings)),
        "moveSuggestions" to data.moveSuggestions?.let { JsonRaw(MoveSuggestFormatter.formatJson(it)) },
        "cohesion" to data.cohesion?.let { JsonRaw(CohesionFormatter.formatJson(it)) },
        "deadCode" to JsonRaw(DeadCodeFormatter.formatJson(data.deadCode.take(data.topN), data.scope)),
    )

    fun formatLlm(data: ReportData): String {
        val sections = mutableListOf<String>()
        sections += "# metrics\n${MetricsFormatter.formatLlm(data.metrics)}"
        sections += "# cycles\n${CyclesFormatter.formatLlm(data.cycles, displayPrefix = data.displayPrefix)}"
        sections += "# rings\n${RingFormatter.format(data.rings, format = no.f12.codenavigator.config.OutputFormat.LLM)}"
        data.moveSuggestions?.let { sections += "# move-suggestions\n${MoveSuggestFormatter.formatLlm(it)}" }
        data.cohesion?.let { sections += "# cohesion\n${CohesionFormatter.formatLlm(it)}" }
        sections += "# dead-code\n${DeadCodeFormatter.formatLlm(data.deadCode.take(data.topN), data.scope)}"
        return sections.joinToString("\n\n")
    }
}
