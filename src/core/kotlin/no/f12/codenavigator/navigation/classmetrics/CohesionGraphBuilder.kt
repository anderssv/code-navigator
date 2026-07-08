package no.f12.codenavigator.navigation.classmetrics

data class ClassCohesion(
    val totalMethods: Int,
    val tcc: Double,
    val lcc: Double,
    val verdict: ClassCohesionVerdict,
)

/**
 * Builds the method-pair cohesion graph from a class's field-access map (no bytecode involved).
 * TCC = direct pairs (methods sharing >=1 field) / total pairs.
 * LCC = pairs connected through a chain of shared fields (transitive closure) / total pairs.
 */
object CohesionGraphBuilder {

    fun build(fieldAccessByMethod: Map<String, Set<String>>): ClassCohesion {
        val methods = fieldAccessByMethod.keys.toList()
        val n = methods.size
        val totalPairs = n * (n - 1) / 2

        if (totalPairs == 0) {
            return ClassCohesion(n, 0.0, 0.0, verdictFor(0.0, 0.0))
        }

        val parent = IntArray(n) { it }
        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            var cursor = x
            while (parent[cursor] != root) {
                val next = parent[cursor]
                parent[cursor] = root
                cursor = next
            }
            return root
        }
        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }

        var directPairs = 0
        for (i in 0 until n) {
            val fieldsI = fieldAccessByMethod[methods[i]] ?: emptySet()
            if (fieldsI.isEmpty()) continue
            for (j in i + 1 until n) {
                val fieldsJ = fieldAccessByMethod[methods[j]] ?: emptySet()
                if (fieldsJ.isEmpty()) continue
                if (fieldsI.any { it in fieldsJ }) {
                    directPairs++
                    union(i, j)
                }
            }
        }

        val componentSizes = (0 until n).groupingBy { find(it) }.eachCount()
        val lccPairs = componentSizes.values.sumOf { it * (it - 1) / 2 }

        val tcc = directPairs.toDouble() / totalPairs
        val lcc = lccPairs.toDouble() / totalPairs
        return ClassCohesion(n, tcc, lcc, verdictFor(tcc, lcc))
    }

    private fun verdictFor(tcc: Double, lcc: Double): ClassCohesionVerdict = when {
        tcc >= 0.7 -> ClassCohesionVerdict.HIGH
        tcc >= 0.4 -> ClassCohesionVerdict.MEDIUM
        lcc >= 0.7 -> ClassCohesionVerdict.LOW
        else -> ClassCohesionVerdict.MONOLITH
    }
}
