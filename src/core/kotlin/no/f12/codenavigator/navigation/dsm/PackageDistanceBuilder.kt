package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.PackageName

data class PackageDistanceEntry(
    val source: PackageName,
    val target: PackageName,
    val distance: Int,
    val dependencyCount: Int,
)

data class PackageDistanceResult(
    val entries: List<PackageDistanceEntry>,
)

object PackageDistanceBuilder {

    fun build(
        matrix: DsmMatrix,
        top: Int = Int.MAX_VALUE,
        packageFilter: String? = null,
    ): PackageDistanceResult {
        val filterRegex = packageFilter?.let { Regex(it) }

        val entries = matrix.cells.map { (key, count) ->
            val (source, target) = key
            PackageDistanceEntry(
                source = source,
                target = target,
                distance = PackageDistanceCalculator.distance(source, target),
                dependencyCount = count,
            )
        }
            .filter { entry ->
                filterRegex == null || entry.source.matches(filterRegex) || entry.target.matches(filterRegex)
            }
            .sortedWith(compareByDescending<PackageDistanceEntry> { it.distance }.thenBy { it.source }.thenBy { it.target })
            .take(top)

        return PackageDistanceResult(entries)
    }
}
