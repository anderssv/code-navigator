package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.PackageName

data class RankedEdge(
    val from: PackageName,
    val to: PackageName,
    val weight: Int,
    val breaksycle: Boolean,
)

object CycleBreakAnalyzer {

    fun rankEdges(cycle: CycleDetail): List<RankedEdge> {
        val packages = cycle.packages.toSet()
        val edges = cycle.edges

        // Build adjacency for the cycle subgraph
        val adjacency = mutableMapOf<PackageName, MutableSet<PackageName>>()
        for (edge in edges) {
            adjacency.getOrPut(edge.from) { mutableSetOf() }.add(edge.to)
        }

        val ranked = edges.map { edge ->
            val weight = edge.classEdges.size

            // Check if removing this edge breaks the SCC
            val reducedAdj = packages.associateWith { pkg ->
                val targets = adjacency[pkg]?.toMutableSet() ?: mutableSetOf()
                if (pkg == edge.from) targets.remove(edge.to)
                targets as Set<PackageName>
            }
            val sccsAfterRemoval = TarjanSCC.findSCCs(packages, reducedAdj, minSize = 2)
            val breaksycle = sccsAfterRemoval.isEmpty() ||
                sccsAfterRemoval.none { it.containsAll(packages) }

            RankedEdge(from = edge.from, to = edge.to, weight = weight, breaksycle = breaksycle)
        }

        // Sort: break points first, then by weight ascending
        return ranked.sortedWith(compareBy({ !it.breaksycle }, { it.weight }))
    }
}
