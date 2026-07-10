package no.f12.codenavigator.navigation.converge

import no.f12.codenavigator.formatting.JsonRaw
import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.withInterpretation

object ConvergeFormatter {

    internal const val INTERSECT_INTERPRETATION = "Interpretation: ACT NOW means a structural problem (cycle or ring violation) and real change coupling both point at the same package pair — the strongest signal for where to invest. LATENT is structural but hasn't caused coupling pain yet. MISSING ABSTRACTION is packages that change together with no structural coupling detected — often a sign of a missing shared interface/contract."

    internal const val RISK_INTERPRETATION = "Interpretation: risk = change frequency x complexity x coupling degree. High-risk classes change often, are structurally complex, and are entangled with other files — the combination that tends to produce the costliest bugs and hardest reviews."

    private fun verdictLabel(verdict: ConvergeVerdict): String = when (verdict) {
        ConvergeVerdict.ACT_NOW -> "ACT NOW"
        ConvergeVerdict.LATENT -> "LATENT"
        ConvergeVerdict.MISSING_ABSTRACTION -> "MISSING ABSTRACTION"
    }

    private fun signals(edge: ConvergedEdge): String = buildList {
        if (edge.hasCycle) add("cycle")
        if (edge.hasRingViolation) add("ring-violation")
        edge.couplingDegree?.let { add("coupling=$it%") }
    }.joinToString(", ")

    fun format(output: ConvergeOutput): String = when (output) {
        is ConvergeOutput.Intersect -> formatIntersect(output.output)
        is ConvergeOutput.Risk -> formatRisk(output.output)
    }

    private fun formatIntersect(output: ConvergeIntersectOutput): String {
        if (output.edges.isEmpty()) return "No converging structural/coupling signals found."

        return buildString {
            for (verdict in ConvergeVerdict.entries) {
                val group = output.edges.filter { it.verdict == verdict }
                if (group.isEmpty()) continue
                appendLine("${verdictLabel(verdict)}:")
                for (edge in group) {
                    appendLine("  ${edge.source} <-> ${edge.target}  (${signals(edge)})")
                }
                appendLine()
            }
            if (output.unresolvedCouplingPairs > 0) {
                appendLine("(${output.unresolvedCouplingPairs} coupled file pair(s) could not be resolved to a project package and were skipped)")
            }
        }.trimEnd()
    }

    private fun formatRisk(output: ConvergeRiskOutput): String {
        if (output.entries.isEmpty()) return "No risk-ranked classes found."

        return output.entries.mapIndexed { index, entry ->
            "${index + 1}. ${entry.className} (${entry.sourceFile})  risk=${entry.riskScore}  changes=${entry.changeFrequency}  complexity=${entry.complexity}  coupling=${entry.couplingDegree?.toString() ?: "-"}"
        }.joinToString("\n")
    }

    fun formatJson(output: ConvergeOutput): String = when (output) {
        is ConvergeOutput.Intersect -> formatIntersectJson(output.output)
        is ConvergeOutput.Risk -> formatRiskJson(output.output)
    }

    private fun formatIntersectJson(output: ConvergeIntersectOutput): String {
        val edgesJson = jsonArray(output.edges) { edge ->
            jsonObject(
                "source" to edge.source.toString(),
                "target" to edge.target.toString(),
                "verdict" to verdictLabel(edge.verdict),
                "hasCycle" to edge.hasCycle,
                "hasRingViolation" to edge.hasRingViolation,
                "couplingDegree" to edge.couplingDegree,
            )
        }
        return jsonObject(
            "mode" to "intersect",
            "edges" to JsonRaw(edgesJson),
            "unresolvedCouplingPairs" to output.unresolvedCouplingPairs,
        )
    }

    private fun formatRiskJson(output: ConvergeRiskOutput): String {
        val entriesJson = jsonArray(output.entries) { entry ->
            jsonObject(
                "className" to entry.className.toString(),
                "sourceFile" to entry.sourceFile,
                "changeFrequency" to entry.changeFrequency,
                "complexity" to entry.complexity,
                "couplingDegree" to entry.couplingDegree,
                "riskScore" to entry.riskScore,
            )
        }
        return jsonObject("mode" to "risk", "entries" to JsonRaw(entriesJson))
    }

    fun formatLlm(output: ConvergeOutput): String = when (output) {
        is ConvergeOutput.Intersect -> formatIntersectLlm(output.output)
        is ConvergeOutput.Risk -> formatRiskLlm(output.output)
    }

    private fun formatIntersectLlm(output: ConvergeIntersectOutput): String {
        if (output.edges.isEmpty()) return "(no converging signals)"

        return output.edges.joinToString("\n") { edge ->
            "${verdictLabel(edge.verdict)} ${edge.source}<->${edge.target} ${signals(edge)}"
        }.withInterpretation(INTERSECT_INTERPRETATION)
    }

    private fun formatRiskLlm(output: ConvergeRiskOutput): String {
        if (output.entries.isEmpty()) return "(no risk-ranked classes)"

        return output.entries.joinToString("\n") { entry ->
            "${entry.className} risk=${entry.riskScore} changes=${entry.changeFrequency} complexity=${entry.complexity} coupling=${entry.couplingDegree ?: 0}"
        }.withInterpretation(RISK_INTERPRETATION)
    }
}
