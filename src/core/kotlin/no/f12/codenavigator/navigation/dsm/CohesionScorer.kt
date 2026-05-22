package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.PackageName

data class CohesionEntry(
    val packageName: PackageName,
    val internalEdges: Int,
    val externalEdges: Int,
    val cohesion: Double,
)

data class CohesionResult(
    val entries: List<CohesionEntry>,
)

object CohesionScorer {

    fun score(
        dependencies: List<PackageDependency>,
        top: Int = Int.MAX_VALUE,
    ): CohesionResult {
        val allPackages = (dependencies.map { it.sourcePackage } + dependencies.map { it.targetPackage }).distinct()

        val entries = allPackages.map { pkg ->
            val internal = dependencies.count { it.sourcePackage == pkg && it.targetPackage == pkg }
            val external = dependencies.count { it.sourcePackage == pkg && it.targetPackage != pkg }
            val total = internal + external
            val cohesion = if (total == 0) 1.0 else internal.toDouble() / total
            CohesionEntry(pkg, internal, external, cohesion)
        }
            .filter { it.internalEdges + it.externalEdges > 0 }
            .sortedBy { it.cohesion }
            .take(top)

        return CohesionResult(entries)
    }
}
