package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.complexity.ClassComplexity

/**
 * Maps synthetic lambda/anonymous class names back to their enclosing "real" class.
 *
 * Used by complexity analysis and PageRank to avoid inflated counts. Without collapsing,
 * compiler-generated classes like `Controller$handle$1` count as separate coupling partners,
 * distorting fan-in/fan-out and type rankings.
 *
 * Not used in call trees — those rely on [KotlinMethodFilter] to filter synthetic methods,
 * and line numbers for navigation.
 */
object LambdaCollapser {

    fun collapse(className: ClassName): ClassName = className.collapseLambda()

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

    private fun collapseByClass(entries: List<Pair<ClassName, Int>>): List<Pair<ClassName, Int>> =
        entries
            .groupBy { (className, _) -> collapse(className) }
            .map { (collapsed, group) -> collapsed to group.sumOf { it.second } }
            .sortedByDescending { it.second }
}
