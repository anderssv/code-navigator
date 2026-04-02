package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.ClassName
import no.f12.codenavigator.navigation.PackageName

data class PackageDependency(
    val sourcePackage: PackageName,
    val targetPackage: PackageName,
    val sourceClass: ClassName,
    val targetClass: ClassName,
)

fun List<PackageDependency>.filterByPackage(packageFilter: PackageName?): List<PackageDependency> {
    if (packageFilter == null || packageFilter.isEmpty()) return this
    return filter { it.sourcePackage.startsWith(packageFilter) || it.targetPackage.startsWith(packageFilter) }
}

data class DsmMatrix(
    val packages: List<PackageName>,
    val cells: Map<Pair<PackageName, PackageName>, Int>,
    val classDependencies: Map<Pair<PackageName, PackageName>, Set<Pair<ClassName, ClassName>>>,
    val displayPrefix: PackageName = PackageName(""),
) {
    fun findCyclicPairs(cycleFilter: Pair<PackageName, PackageName>? = null): List<Triple<PackageName, PackageName, Pair<Int, Int>>> {
        val allPairs = packages.flatMapIndexed { rowIdx, rowPkg ->
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
        if (cycleFilter == null) return allPairs
        return allPairs.filter { (a, b, _) ->
            (a == cycleFilter.first && b == cycleFilter.second) ||
                (a == cycleFilter.second && b == cycleFilter.first)
        }
    }
}

object DsmMatrixBuilder {

    fun build(dependencies: List<PackageDependency>, rootPrefix: PackageName, depth: Int): DsmMatrix {
        if (dependencies.isEmpty()) return DsmMatrix(emptyList(), emptyMap(), emptyMap())

        val allTruncated = dependencies.map { dep ->
            Triple(dep.sourcePackage.truncate(rootPrefix, depth),
                   dep.targetPackage.truncate(rootPrefix, depth),
                   dep)
        }

        val crossPackage = allTruncated.filter { (src, tgt, _) -> src != tgt }

        val cells = mutableMapOf<Pair<PackageName, PackageName>, Int>()
        val classDeps = mutableMapOf<Pair<PackageName, PackageName>, MutableSet<Pair<ClassName, ClassName>>>()

        for ((src, tgt, dep) in crossPackage) {
            val key = src to tgt
            cells[key] = (cells[key] ?: 0) + 1
            classDeps.getOrPut(key) { mutableSetOf() }.add(dep.sourceClass to dep.targetClass)
        }

        val packages = (crossPackage.flatMap { listOf(it.first, it.second) }).distinct().sorted()

        return DsmMatrix(packages, cells, classDeps, displayPrefix = rootPrefix)
    }

}
