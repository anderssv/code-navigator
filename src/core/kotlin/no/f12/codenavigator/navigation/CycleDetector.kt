package no.f12.codenavigator.navigation

data class Cycle(
    val packages: List<String>,
)

data class CycleDetail(
    val packages: List<String>,
    val edges: List<CycleEdge>,
)

data class CycleEdge(
    val from: String,
    val to: String,
    val classEdges: Set<Pair<String, String>>,
)

object CycleDetector {

    fun adjacencyMapFrom(matrix: DsmMatrix): Map<String, Set<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()
        for ((source, target) in matrix.cells.keys) {
            result.getOrPut(source) { mutableSetOf() }.add(target)
        }
        return result
    }

    fun enrich(cycles: List<Cycle>, matrix: DsmMatrix): List<CycleDetail> =
        cycles.map { cycle ->
            val packageSet = cycle.packages.toSet()
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

    fun findCycles(graph: Map<String, Set<String>>): List<Cycle> {
        val allNodes = mutableSetOf<String>()
        for ((source, targets) in graph) {
            allNodes.add(source)
            allNodes.addAll(targets)
        }

        val index = mutableMapOf<String, Int>()
        val lowLink = mutableMapOf<String, Int>()
        val onStack = mutableSetOf<String>()
        val stack = ArrayDeque<String>()
        var nextIndex = 0
        val sccs = mutableListOf<List<String>>()

        fun strongConnect(node: String) {
            index[node] = nextIndex
            lowLink[node] = nextIndex
            nextIndex++
            stack.addLast(node)
            onStack.add(node)

            for (target in graph[node] ?: emptySet()) {
                if (target !in index) {
                    strongConnect(target)
                    lowLink[node] = minOf(lowLink[node]!!, lowLink[target]!!)
                } else if (target in onStack) {
                    lowLink[node] = minOf(lowLink[node]!!, index[target]!!)
                }
            }

            if (lowLink[node] == index[node]) {
                val scc = mutableListOf<String>()
                while (true) {
                    val w = stack.removeLast()
                    onStack.remove(w)
                    scc.add(w)
                    if (w == node) break
                }
                if (scc.size > 1) {
                    sccs.add(scc.sorted())
                }
            }
        }

        for (node in allNodes.sorted()) {
            if (node !in index) {
                strongConnect(node)
            }
        }

        return sccs
            .sortedBy { it.size }
            .map { Cycle(packages = it) }
    }
}
