package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName

data class Cycle(
    val packages: List<PackageName>,
)

data class CycleDetail(
    val packages: List<PackageName>,
    val edges: List<CycleEdge>,
)

data class CycleEdge(
    val from: PackageName,
    val to: PackageName,
    val classEdges: Set<Pair<ClassName, ClassName>>,
)

object CycleDetector {

    fun adjacencyMapFrom(matrix: DsmMatrix): Map<PackageName, Set<PackageName>> {
        val result = mutableMapOf<PackageName, MutableSet<PackageName>>()
        for ((source, target) in matrix.cells.keys) {
            result.getOrPut(source) { mutableSetOf() }.add(target)
        }
        return result
    }

    fun enrich(cycles: List<Cycle>, matrix: DsmMatrix): List<CycleDetail> =
        cycles.map { cycle ->
            val edges = mutableListOf<CycleEdge>()
            for (from in cycle.packages) {
                for (to in cycle.packages) {
                    if (from == to) continue
                    val classEdges = matrix.classDependencies[from to to]
                    if (classEdges != null) {
                        edges.add(CycleEdge(from = from, to = to, classEdges = classEdges))
                    }
                }
            }
            CycleDetail(packages = cycle.packages, edges = edges.sortedWith(compareBy({ it.from }, { it.to })))
        }

    fun findCycles(graph: Map<PackageName, Set<PackageName>>): List<Cycle> {
        val allNodes = mutableSetOf<PackageName>()
        for ((source, targets) in graph) {
            allNodes.add(source)
            allNodes.addAll(targets)
        }

        val sccs = TarjanSCC.findSCCs(allNodes, graph, minSize = 2)

        return sccs
            .sortedBy { it.size }
            .map { Cycle(packages = it.sorted()) }
    }
}
