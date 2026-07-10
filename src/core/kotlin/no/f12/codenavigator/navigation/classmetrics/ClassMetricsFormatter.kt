package no.f12.codenavigator.navigation.classmetrics

import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.withInterpretation

object ClassMetricsFormatter {

    internal const val CLASS_METRICS_INTERPRETATION = "Interpretation: TCC/LCC measure cohesion (fraction of method pairs sharing field access). HIGH (TCC>=0.7) = cohesive. MEDIUM (0.4-0.7) = acceptable. LOW (TCC<0.4, LCC>=0.7) = weakly cohesive but methods still chain-connect via shared fields. MONOLITH (TCC<0.4, LCC<0.7) = disjoint method groups — candidate for splitting into separate classes. WMC = summed cyclomatic complexity (higher = harder to test). CBO = distinct non-JDK/stdlib types referenced in signatures (higher = more context needed to understand the class). DIT = superclass chain depth (deeper = more inherited behavior to reason about)."

    fun format(results: List<ClassMetricsResult>): String {
        if (results.isEmpty()) return "No matching classes found."

        val header = String.format("%-50s %6s %6s %-8s %5s %5s %5s", "Class", "TCC", "LCC", "Verdict", "WMC", "CBO", "DIT")
        val separator = "-".repeat(header.length)
        val rows = results.joinToString("\n") { r ->
            String.format(
                "%-50s %6.2f %6.2f %-8s %5d %5d %5d",
                r.className, r.tcc, r.lcc, r.verdict, r.wmc, r.cbo, r.dit,
            )
        }

        return "$header\n$separator\n$rows"
    }

    fun formatJson(results: List<ClassMetricsResult>): String =
        jsonArray(results) { r ->
            jsonObject(
                "className" to r.className.toString(),
                "package" to r.packageName.toString(),
                "totalMethods" to r.totalMethods,
                "tcc" to r.tcc,
                "lcc" to r.lcc,
                "verdict" to r.verdict.name,
                "wmc" to r.wmc,
                "cbo" to r.cbo,
                "dit" to r.dit,
            )
        }

    fun formatLlm(results: List<ClassMetricsResult>): String =
        results.joinToString("\n") { r ->
            "${r.className} methods=${r.totalMethods} tcc=${"%.2f".format(r.tcc)} lcc=${"%.2f".format(r.lcc)} verdict=${r.verdict} wmc=${r.wmc} cbo=${r.cbo} dit=${r.dit}"
        }.withInterpretation(CLASS_METRICS_INTERPRETATION)
}
