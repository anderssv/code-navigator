package no.f12.codenavigator.navigation.rank

import no.f12.codenavigator.navigation.callgraph.CallGraph
import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.LambdaCollapser

data class RankedType(
    val className: ClassName,
    val rank: Double,
    val inDegree: Int,
    val outDegree: Int,
)

object TypeRanker {

    private const val DAMPING = 0.85
    private const val ITERATIONS = 20

    fun rank(graph: CallGraph, top: Int = Int.MAX_VALUE, projectOnly: Boolean = false, collapseLambdas: Boolean = false): List<RankedType> {
        val collapse: (ClassName) -> ClassName = if (collapseLambdas) LambdaCollapser::collapse else { it -> it }
        val typeEdges = mutableMapOf<ClassName, MutableSet<ClassName>>()
        val allTypes = mutableSetOf<ClassName>()

        graph.forEachEdge { caller, callee ->
            val from = collapse(caller.className)
            val to = collapse(callee.className)
            if (from != to) {
                typeEdges.getOrPut(from) { mutableSetOf() }.add(to)
            }
            allTypes.add(from)
            allTypes.add(to)
        }

        if (allTypes.isEmpty()) return emptyList()

        val types = if (projectOnly) {
            val projectClasses = graph.projectClasses().map { collapse(it) }.toSet()
            allTypes.filter { it in projectClasses }
        } else {
            allTypes.toList()
        }
        if (types.isEmpty()) return emptyList()

        val n = types.size
        val typesSet = types.toSet()
        val initialRank = 1.0 / n
        val ranks = mutableMapOf<ClassName, Double>()
        types.forEach { ranks[it] = initialRank }

        val incomingEdges = mutableMapOf<ClassName, MutableSet<ClassName>>()
        for ((from, targets) in typeEdges) {
            for (to in targets) {
                if (to in typesSet && from in typesSet) {
                    incomingEdges.getOrPut(to) { mutableSetOf() }.add(from)
                }
            }
        }

        repeat(ITERATIONS) {
            val newRanks = mutableMapOf<ClassName, Double>()
            val base = (1.0 - DAMPING) / n

            for (type in types) {
                var sum = 0.0
                val incoming = incomingEdges[type]
                if (incoming != null) {
                    for (source in incoming) {
                        val outCount = typeEdges[source]?.count { it in typesSet } ?: 1
                        sum += (ranks[source] ?: 0.0) / outCount
                    }
                }
                newRanks[type] = base + DAMPING * sum
            }

            for (type in types) {
                ranks[type] = newRanks[type] ?: 0.0
            }
        }

        val inDegree = mutableMapOf<ClassName, Int>()
        val outDegree = mutableMapOf<ClassName, Int>()
        for ((from, targets) in typeEdges) {
            if (from in typesSet) {
                val filteredTargets = targets.count { it in typesSet }
                outDegree[from] = (outDegree[from] ?: 0) + filteredTargets
            }
            for (to in targets) {
                if (to in typesSet && from in typesSet) {
                    inDegree[to] = (inDegree[to] ?: 0) + 1
                }
            }
        }

        return types
            .map { type ->
                RankedType(
                    className = type,
                    rank = ranks[type] ?: 0.0,
                    inDegree = inDegree[type] ?: 0,
                    outDegree = outDegree[type] ?: 0,
                )
            }
            .sortedByDescending { it.rank }
            .take(top)
    }
}
