package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName

data class MoveSuggestion(
    val className: ClassName,
    val currentPackage: PackageName,
    val suggestedPackage: PackageName,
    val edgesToCurrent: Int,
    val edgesToSuggested: Int,
    val confidence: Double,
)

data class MoveSuggestionResult(
    val suggestions: List<MoveSuggestion>,
)

object MoveSuggester {

    private val COMPOSITION_ROOT_PATTERNS = listOf(
        Regex(".*Context$", RegexOption.IGNORE_CASE),
        Regex(".*Module$", RegexOption.IGNORE_CASE),
        Regex(".*Application.*", RegexOption.IGNORE_CASE),
        Regex(".*Wiring.*", RegexOption.IGNORE_CASE),
        Regex(".*Dependencies.*", RegexOption.IGNORE_CASE),
    )

    private val DRIVER_PATTERNS = listOf(
        Regex(".*Routes.*", RegexOption.IGNORE_CASE),
        Regex(".*Controller.*", RegexOption.IGNORE_CASE),
        Regex(".*Endpoint.*", RegexOption.IGNORE_CASE),
        Regex(".*Handler.*", RegexOption.IGNORE_CASE),
    )

    private const val COMPOSITION_ROOT_PACKAGE_THRESHOLD = 5
    private const val STRUCTURAL_EDGE_WEIGHT = 3

    fun suggest(
        dependencies: List<PackageDependency>,
        structuralSupertypes: List<StructuralSupertypeInfo> = emptyList(),
        top: Int = Int.MAX_VALUE,
        maxFanIn: Int = Int.MAX_VALUE,
    ): MoveSuggestionResult {
        val ubiquitousTargets = dependencies
            .groupBy { it.targetClass }
            .filter { (_, edges) -> edges.map { it.sourceClass }.distinct().size >= maxFanIn }
            .keys

        val filteredDeps = dependencies.filter { it.targetClass !in ubiquitousTargets }

        val structuralEdgesByClass: Map<ClassName, Map<ClassName, Int>> = structuralSupertypes
            .groupBy { it.sourceClass }
            .mapValues { (_, types) ->
                types.groupBy { it.supertypeClass }.mapValues { it.value.size * STRUCTURAL_EDGE_WEIGHT }
            }

        val classes = (filteredDeps.map { it.sourceClass } + structuralEdgesByClass.keys)
            .distinct()
            .map { cls -> cls to cls.packageName() }

        val suggestions = classes.mapNotNull { (cls, currentPkg) ->
            val outgoing = filteredDeps.filter { it.sourceClass == cls }

            val edgesByTarget = outgoing.groupBy { it.targetPackage }
                .mapValues { (_, edges) -> edges.size }
                .toMutableMap()

            structuralEdgesByClass[cls]?.forEach { (supertypeClass, weight) ->
                val supertypePkg = supertypeClass.packageName()
                edgesByTarget[supertypePkg] = (edgesByTarget[supertypePkg] ?: 0) + weight
            }

            val allOutgoing = outgoing.isEmpty() && structuralEdgesByClass.containsKey(cls).not()

            if (allOutgoing) return@mapNotNull null

            if (isCompositionRoot(cls, outgoing) && structuralEdgesByClass.containsKey(cls).not()) return@mapNotNull null

            val edgesToOwn = edgesByTarget[currentPkg] ?: 0
            val bestOther = edgesByTarget.filter { it.key != currentPkg }
                .maxByOrNull { it.value } ?: return@mapNotNull null

            if (bestOther.value > edgesToOwn) {
                if (isDriver(cls)) return@mapNotNull null
                if (isFeatureSliceMember(cls, currentPkg, filteredDeps)) return@mapNotNull null

                val callersFromSamePackage = filteredDeps.count { it.targetClass == cls && it.sourcePackage == currentPkg }
                val total = edgesByTarget.values.sum() + callersFromSamePackage
                val confidence = bestOther.value.toDouble() / total
                MoveSuggestion(cls, currentPkg, bestOther.key, edgesToOwn, bestOther.value, confidence)
            } else {
                null
            }
        }
            .sortedByDescending { it.confidence }
            .take(top)

        return MoveSuggestionResult(suggestions)
    }

    private fun isFeatureSliceMember(cls: ClassName, pkg: PackageName, deps: List<PackageDependency>): Boolean {
        // Find same-package classes this class depends on
        val samePackageTargets = deps
            .filter { it.sourceClass == cls && it.targetPackage == pkg }
            .map { it.targetClass }
            .distinct()
        if (samePackageTargets.isEmpty()) return false

        // Check if any sibling (different class in same package) also depends on the same target
        return samePackageTargets.any { target ->
            deps.any { it.sourceClass != cls && it.sourcePackage == pkg && it.targetClass == target }
        }
    }

    private fun isCompositionRoot(cls: ClassName, outgoing: List<PackageDependency>): Boolean {
        val simpleName = cls.simpleName()
        if (COMPOSITION_ROOT_PATTERNS.any { it.matches(simpleName) }) return true
        val distinctTargetPackages = outgoing.map { it.targetPackage }.distinct().size
        return distinctTargetPackages >= COMPOSITION_ROOT_PACKAGE_THRESHOLD
    }

    private fun isDriver(cls: ClassName): Boolean {
        val simpleName = cls.simpleName()
        return DRIVER_PATTERNS.any { it.matches(simpleName) }
    }
}
