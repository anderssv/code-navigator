package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName

enum class CohesionVerdict {
    COHESIVE,
    REVIEW,
    THIN_LAYER,

    /** Weak on its own, but its edges stay within its feature: a ring of a feature, not an incoherent package. */
    LAYERED,
}

data class CohesionEntry(
    val packageName: PackageName,
    val classCount: Int,
    val internalEdges: Int,
    val externalEdges: Int,
    val cohesion: Double,
    val verdict: CohesionVerdict,
    val feature: PackageName? = null,
    val featureCohesion: Double = cohesion,
)

data class FeatureCohesionEntry(
    val feature: PackageName,
    val packageCount: Int,
    val internalEdges: Int,
    val externalEdges: Int,
    val cohesion: Double,
)

data class CohesionDetailEntry(
    val className: ClassName,
    val internalEdges: Int,
    val externalEdges: Int,
)

data class CohesionResult(
    val entries: List<CohesionEntry>,
    val features: List<FeatureCohesionEntry> = emptyList(),
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

        val sourcePackages = dependencies.map { it.sourcePackage.toString() }.distinct()
        val projectRoot = commonPackagePrefix(sourcePackages)
        val featureOf = { pkg: PackageName -> featureOf(pkg.toString(), projectRoot)?.let(::PackageName) }

        val entries = allPackages.map { pkg ->
            val outgoing = dependencies.filter { it.sourcePackage == pkg }
            val internal = outgoing.count { isNested(pkg.toString(), it.targetPackage.toString()) }
            val external = outgoing.size - internal
            val cohesion = ratio(internal, outgoing.size)
            val feature = featureOf(pkg)
            val featureInternal = if (feature == null) internal else outgoing.count { featureOf(it.targetPackage) == feature }
            val featureCohesion = ratio(featureInternal, outgoing.size)
            val classCount = classesPerPackage[pkg] ?: 0
            CohesionEntry(pkg, classCount, internal, external, cohesion, verdict(cohesion, internal, featureCohesion), feature, featureCohesion)
        }
            .filter { it.internalEdges + it.externalEdges > 0 }
            .filter { it.internalEdges + it.externalEdges >= minEdges }
            .sortedBy { it.cohesion }
            .take(top)

        val features = dependencies.groupBy { featureOf(it.sourcePackage) }
            .filterKeys { it != null }
            .map { (feature, deps) ->
                val internal = deps.count { featureOf(it.targetPackage) == feature }
                val packages = allPackages.count { featureOf(it) == feature }
                FeatureCohesionEntry(feature!!, packages, internal, deps.size - internal, ratio(internal, deps.size))
            }
            .filter { it.packageCount > 1 }
            .sortedBy { it.cohesion }

        return CohesionResult(entries, features)
    }

    private fun ratio(internal: Int, total: Int) = if (total == 0) 1.0 else internal.toDouble() / total

    /** Same package, or a parent/child of it. Matches move-suggest, where nested packages count as internal. */
    private fun isNested(a: String, b: String) = a == b || a.startsWith("$b.") || b.startsWith("$a.")

    /** The longest package prefix shared by all of [packages], on segment boundaries. */
    internal fun commonPackagePrefix(packages: List<String>): String {
        if (packages.isEmpty()) return ""
        val split = packages.map { it.split('.') }
        val common = split.first().indices.takeWhile { i -> split.all { it.getOrNull(i) == split.first()[i] } }.size
        return split.first().take(common).joinToString(".")
    }

    /**
     * The feature a package belongs to: the first segment below the project root (`app.users.web` -> `app.users`).
     * Null outside the project, and for the root itself. A project with one feature has its root at the feature, so
     * its subpackages become the features.
     */
    private fun featureOf(pkg: String, root: String): String? {
        val rest = when {
            root.isEmpty() -> pkg
            pkg.startsWith("$root.") -> pkg.removePrefix("$root.")
            else -> return null
        }
        return if (root.isEmpty()) rest.substringBefore('.') else "$root.${rest.substringBefore('.')}"
    }

    private fun verdict(cohesion: Double, internalEdges: Int, featureCohesion: Double): CohesionVerdict = when {
        cohesion >= 0.5 -> CohesionVerdict.COHESIVE
        featureCohesion >= 0.5 && featureCohesion > cohesion -> CohesionVerdict.LAYERED
        internalEdges == 0 -> CohesionVerdict.THIN_LAYER
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
