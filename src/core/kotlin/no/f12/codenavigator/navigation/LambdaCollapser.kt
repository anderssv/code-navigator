package no.f12.codenavigator.navigation

object LambdaCollapser {

    private val TRAILING_NUMERIC_SEGMENT = Regex("""\$\d+$""")
    private val TRAILING_LOWERCASE_SEGMENT = Regex("""\$[a-z][^$]*$""")

    fun collapse(className: String): String {
        var result = className
        while (true) {
            val afterNumeric = result.replace(TRAILING_NUMERIC_SEGMENT, "")
            if (afterNumeric == result) break
            val afterFunction = afterNumeric.replace(TRAILING_LOWERCASE_SEGMENT, "")
            result = afterFunction
        }
        return result
    }

    fun collapseComplexity(results: List<ClassComplexity>): List<ClassComplexity> =
        results.map { complexity ->
            val collapsedOutgoing = collapseByClass(complexity.outgoingByClass)
            val collapsedIncoming = collapseByClass(complexity.incomingByClass)
            complexity.copy(
                distinctOutgoingClasses = collapsedOutgoing.size,
                distinctIncomingClasses = collapsedIncoming.size,
                outgoingByClass = collapsedOutgoing,
                incomingByClass = collapsedIncoming,
            )
        }

    private fun collapseByClass(entries: List<Pair<String, Int>>): List<Pair<String, Int>> =
        entries
            .groupBy { (className, _) -> collapse(className) }
            .map { (collapsed, group) -> collapsed to group.sumOf { it.second } }
            .sortedByDescending { it.second }
}
