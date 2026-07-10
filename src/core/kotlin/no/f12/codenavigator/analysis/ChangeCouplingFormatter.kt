package no.f12.codenavigator.analysis

import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.withInterpretation

object ChangeCouplingFormatter {

    internal const val COUPLING_INTERPRETATION = "Interpretation: High degree (%) means these files almost always change together. Intentional coupling (e.g., interface+implementation) is fine. Unintentional coupling suggests hidden dependencies or shared responsibilities that should be extracted. Pairs marked [stale] reference a file that no longer exists (rename/delete from git history) — ignore them."

    private const val HIGH_COUPLING_THRESHOLD = 70

    private fun isSourceFile(path: String): Boolean = path.startsWith("src/")

    private fun testMainPair(a: String, b: String): Boolean {
        val aIsMain = a.startsWith("src/main/")
        val aIsTest = a.startsWith("src/test/")
        val bIsMain = b.startsWith("src/main/")
        val bIsTest = b.startsWith("src/test/")
        return (aIsMain && bIsTest) || (aIsTest && bIsMain)
    }

    fun format(pairs: List<CoupledPair>): String {
        if (pairs.isEmpty()) return "No coupling found."

        val entityWidth = maxOf("Entity".length, pairs.maxOf { it.entity.length })
        val coupledWidth = maxOf("Coupled".length, pairs.maxOf { it.coupled.length })
        val degreeWidth = maxOf("Degree".length, pairs.maxOf { "${it.degree}%".length })
        val sharedWidth = maxOf("Shared".length, pairs.maxOf { it.sharedRevs.toString().length })

        return buildString {
            appendLine("%-${entityWidth}s  %-${coupledWidth}s  %${degreeWidth}s  %${sharedWidth}s".format(
                "Entity", "Coupled", "Degree", "Shared",
            ))
            pairs.forEachIndexed { index, p ->
                if (index > 0) appendLine()
                append("%-${entityWidth}s  %-${coupledWidth}s  %${degreeWidth}s  %${sharedWidth}d".format(
                    p.entity, p.coupled, "${p.degree}%", p.sharedRevs,
                ))
                if (p.stale) {
                    append("  [stale] — one or both files no longer exist (rename/delete from git history).")
                } else if (p.degree >= HIGH_COUPLING_THRESHOLD && !testMainPair(p.entity, p.coupled) && isSourceFile(p.entity) && isSourceFile(p.coupled)) {
                    append("  ← High coupling — likely same responsibility, consider merging or extracting shared logic.")
                }
            }
        }
    }

    fun formatJson(pairs: List<CoupledPair>): String =
        jsonArray(pairs) { p ->
            jsonObject(
                "entity" to p.entity,
                "coupled" to p.coupled,
                "degree" to p.degree,
                "sharedRevs" to p.sharedRevs,
                "avgRevs" to p.avgRevs,
                "stale" to (if (p.stale) true else null),
            )
        }

    fun formatLlm(pairs: List<CoupledPair>): String =
        pairs.joinToString("\n") { "${it.entity} -- ${it.coupled} degree=${it.degree}% shared=${it.sharedRevs} avg=${it.avgRevs}${if (it.stale) " [stale]" else ""}" }
            .withInterpretation(COUPLING_INTERPRETATION)
}
