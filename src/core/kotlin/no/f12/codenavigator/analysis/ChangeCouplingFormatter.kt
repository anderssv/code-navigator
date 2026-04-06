package no.f12.codenavigator.analysis

object ChangeCouplingFormatter {

    private const val HIGH_COUPLING_THRESHOLD = 70

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
                if (p.degree >= HIGH_COUPLING_THRESHOLD && !testMainPair(p.entity, p.coupled)) {
                    append("  ← High coupling — likely same responsibility, consider merging or extracting shared logic.")
                }
            }
        }
    }
}
