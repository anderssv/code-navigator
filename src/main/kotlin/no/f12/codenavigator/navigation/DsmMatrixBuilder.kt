package no.f12.codenavigator.navigation

data class PackageDependency(
    val sourcePackage: String,
    val targetPackage: String,
    val sourceClass: String,
    val targetClass: String,
)

data class DsmMatrix(
    val packages: List<String>,
    val cells: Map<Pair<String, String>, Int>,
    val classDependencies: Map<Pair<String, String>, Set<Pair<String, String>>>,
) {
    fun findCyclicPairs(): List<Triple<String, String, Pair<Int, Int>>> =
        packages.flatMapIndexed { rowIdx, rowPkg ->
            packages.mapIndexedNotNull { colIdx, colPkg ->
                if (colIdx > rowIdx &&
                    cells.containsKey(rowPkg to colPkg) &&
                    cells.containsKey(colPkg to rowPkg)
                ) {
                    Triple(rowPkg, colPkg, cells[rowPkg to colPkg]!! to cells[colPkg to rowPkg]!!)
                } else {
                    null
                }
            }
        }
}

object DsmMatrixBuilder {

    fun build(dependencies: List<PackageDependency>, rootPrefix: String, depth: Int): DsmMatrix {
        if (dependencies.isEmpty()) return DsmMatrix(emptyList(), emptyMap(), emptyMap())

        val allTruncated = dependencies.map { dep ->
            Triple(truncate(dep.sourcePackage, rootPrefix, depth),
                   truncate(dep.targetPackage, rootPrefix, depth),
                   dep)
        }

        val crossPackage = allTruncated.filter { (src, tgt, _) -> src != tgt }

        val cells = mutableMapOf<Pair<String, String>, Int>()
        val classDeps = mutableMapOf<Pair<String, String>, MutableSet<Pair<String, String>>>()

        for ((src, tgt, dep) in crossPackage) {
            val key = src to tgt
            cells[key] = (cells[key] ?: 0) + 1
            classDeps.getOrPut(key) { mutableSetOf() }.add(dep.sourceClass to dep.targetClass)
        }

        val packages = (crossPackage.flatMap { listOf(it.first, it.second) }).distinct().sorted()

        return DsmMatrix(packages, cells, classDeps)
    }

    private fun truncate(pkg: String, rootPrefix: String, depth: Int): String {
        val stripped = if (rootPrefix.isNotEmpty() && pkg.startsWith("$rootPrefix.")) {
            pkg.removePrefix("$rootPrefix.")
        } else {
            pkg
        }
        val segments = stripped.split(".")
        return segments.take(depth).joinToString(".")
    }
}
