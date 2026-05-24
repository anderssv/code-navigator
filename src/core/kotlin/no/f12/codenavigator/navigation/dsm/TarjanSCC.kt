package no.f12.codenavigator.navigation.dsm

/**
 * Tarjan's Strongly Connected Components algorithm.
 * Reusable across CycleDetector and RingDetector.
 */
object TarjanSCC {

    /**
     * Find all strongly connected components in the given directed graph.
     * Returns ALL SCCs (including singletons) unless [minSize] filters them.
     */
    fun <T : Comparable<T>> findSCCs(
        nodes: Set<T>,
        adjacency: Map<T, Set<T>>,
        minSize: Int = 1,
    ): List<Set<T>> {
        var index = 0
        val indices = mutableMapOf<T, Int>()
        val lowlinks = mutableMapOf<T, Int>()
        val onStack = mutableSetOf<T>()
        val stack = ArrayDeque<T>()
        val sccs = mutableListOf<Set<T>>()

        fun strongConnect(v: T) {
            indices[v] = index
            lowlinks[v] = index
            index++
            stack.addLast(v)
            onStack.add(v)

            for (w in adjacency[v] ?: emptySet()) {
                if (w !in indices) {
                    strongConnect(w)
                    lowlinks[v] = minOf(lowlinks[v]!!, lowlinks[w]!!)
                } else if (w in onStack) {
                    lowlinks[v] = minOf(lowlinks[v]!!, indices[w]!!)
                }
            }

            if (lowlinks[v] == indices[v]) {
                val scc = mutableSetOf<T>()
                while (true) {
                    val w = stack.removeLast()
                    onStack.remove(w)
                    scc.add(w)
                    if (w == v) break
                }
                if (scc.size >= minSize) {
                    sccs.add(scc)
                }
            }
        }

        for (node in nodes.sorted()) {
            if (node !in indices) strongConnect(node)
        }

        return sccs
    }
}
