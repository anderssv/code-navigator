package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName

enum class CohesionVerdict {
    COHESIVE,
    REVIEW,
    THIN_LAYER,
}

data class CohesionEntry(
    val packageName: PackageName,
    val classCount: Int,
    val internalEdges: Int,
    val externalEdges: Int,
    val cohesion: Double,
    val verdict: CohesionVerdict,
)

data class CohesionDetailEntry(
    val className: ClassName,
    val internalEdges: Int,
    val externalEdges: Int,
)

data class CohesionResult(
    val entries: List<CohesionEntry>,
)

object CohesionScorer {

    fun score(
        dependencies: List<PackageDependency>,
        top: Int = Int.MAX_VALUE,
        minEdges: Int = 0,
    ): CohesionResult {
        val allPackages = (dependencies.map { it.sourcePackage } + dependencies.map { it.targetPackage }).distinct()

        val classesPerPackage = dependencies
            .groupBy { it.sourcePackage }
            .mapValues { (_, deps) -> deps.map { it.sourceClass }.distinct().size }

        val entries = allPackages.map { pkg ->
            val internal = dependencies.count { it.sourcePackage == pkg && it.targetPackage == pkg }
            val external = dependencies.count { it.sourcePackage == pkg && it.targetPackage != pkg }
            val total = internal + external
            val cohesion = if (total == 0) 1.0 else internal.toDouble() / total
            val classCount = classesPerPackage[pkg] ?: 0
            val verdict = verdict(cohesion, internal)
            CohesionEntry(pkg, classCount, internal, external, cohesion, verdict)
        }
            .filter { it.internalEdges + it.externalEdges > 0 }
            .filter { it.internalEdges + it.externalEdges >= minEdges }
            .sortedBy { it.cohesion }
            .take(top)

        return CohesionResult(entries)
    }

    private fun verdict(cohesion: Double, internalEdges: Int): CohesionVerdict = when {
        internalEdges == 0 -> CohesionVerdict.THIN_LAYER
        cohesion >= 0.5 -> CohesionVerdict.COHESIVE
        else -> CohesionVerdict.REVIEW
    }

    fun detail(dependencies: List<PackageDependency>, packageName: PackageName): List<CohesionDetailEntry> {
        val pkgDeps = dependencies.filter { it.sourcePackage == packageName }
        val classes = pkgDeps.map { it.sourceClass }.distinct()
        return classes.map { cls ->
            val internal = pkgDeps.count { it.sourceClass == cls && it.targetPackage == packageName }
            val external = pkgDeps.count { it.sourceClass == cls && it.targetPackage != packageName }
            CohesionDetailEntry(cls, internal, external)
        }.sortedByDescending { it.externalEdges }
    }
}
