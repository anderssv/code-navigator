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
) {
    /**
     * No same-package edges at all, so the verdict rests on the *absence* of ties to the current
     * package rather than on evidence of belonging elsewhere — and `best / total` then rounds toward
     * 1.00, which reads as certainty. The weakest rows in the output, not the strongest.
     */
    val absenceDriven: Boolean get() = edgesToCurrent == 0
}

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

    /**
     * A single edge to another package is not evidence of anything, but it divides into a confidence
     * of 1.00 and reads as certainty. Field reports name that specifically: "the confidence numbers
     * make it look authoritative".
     */
    private const val MIN_EVIDENCE_EDGES = 2
    private const val STRUCTURAL_EDGE_WEIGHT = 3
    private const val RECEIVER_EDGE_WEIGHT = 3

    fun suggest(
        dependencies: List<PackageDependency>,
        structuralSupertypes: List<StructuralSupertypeInfo> = emptyList(),
        top: Int = Int.MAX_VALUE,
        maxFanIn: Int = Int.MAX_VALUE,
        receiverTypes: Map<ClassName, Set<ClassName>> = emptyMap(),
        projectClasses: Set<ClassName> = emptySet(),
        testClasses: Set<ClassName> = emptySet(),
    ): MoveSuggestionResult {
        val ubiquitousTargets = dependencies
            .groupBy { it.targetClass }
            .filter { (_, edges) -> edges.map { it.sourceClass }.distinct().size >= maxFanIn }
            .keys

        val filteredDeps = dependencies.filter { it.targetClass !in ubiquitousTargets }

        // A library package is never somewhere a class can move to. Extension-function receiver
        // gravity can otherwise propose destinations like `kotlinx.html`.
        val projectPackages = projectClasses.map { it.packageName() }.toSet()

        val structuralEdgesByClass: Map<ClassName, Map<ClassName, Int>> = structuralSupertypes
            .groupBy { it.sourceClass }
            .mapValues { (_, types) ->
                types.groupBy { it.supertypeClass }.mapValues { it.value.size * STRUCTURAL_EDGE_WEIGHT }
            }

        // Classes that something else extends or implements. A supertype's outgoing edges are its
        // signature types, so "it references the domain package" is not evidence about where it
        // belongs — see [isSignatureOnlySupertype].
        val supertypes = structuralSupertypes.map { it.supertypeClass }.toSet()

        val classes = (filteredDeps.map { it.sourceClass } + structuralEdgesByClass.keys)
            .distinct()
            .filter { it !in testClasses }
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

            // Rule 1: receiver type counts as weighted gravitational pull toward its package.
            // Rule 2: Kt facades whose ALL receiver types are external (not project classes)
            //         are adapter glue by definition — suppress from move suggestions.
            val receivers = receiverTypes[cls]
            if (receivers != null) {
                val allExternal = receivers.isNotEmpty() && receivers.none { it in projectClasses }
                if (allExternal) return@mapNotNull null
                receivers.forEach { receiverClass ->
                    val receiverPkg = receiverClass.packageName()
                    edgesByTarget[receiverPkg] = (edgesByTarget[receiverPkg] ?: 0) + RECEIVER_EDGE_WEIGHT
                }
            }

            val allOutgoing = outgoing.isEmpty() && structuralEdgesByClass.containsKey(cls).not()

            if (allOutgoing) return@mapNotNull null

            if (isCompositionRoot(cls, outgoing) && structuralEdgesByClass.containsKey(cls).not()) return@mapNotNull null

            // Edges into a child package are internal, not gravity pulling the class away: a service
            // package that orchestrates a `model` sub-package will always have more edges to the child
            // than within itself — that is what layering is. Counting them as "elsewhere" makes every
            // service in such a package look misplaced, and following the advice converges on one
            // giant package. Nested targets are therefore folded into the own count and never
            // proposed as a destination.
            val edgesToOwn = edgesByTarget.filterKeys { it.isNestedWith(currentPkg) }.values.sum()
            val bestOther = edgesByTarget
                .filterKeys { !it.isNestedWith(currentPkg) }
                .filterKeys { projectPackages.isEmpty() || it in projectPackages }
                .maxByOrNull { it.value } ?: return@mapNotNull null

            if (bestOther.value > edgesToOwn && bestOther.value >= MIN_EVIDENCE_EDGES) {
                if (isDriver(cls)) return@mapNotNull null
                if (isFeatureSliceMember(cls, currentPkg, filteredDeps)) return@mapNotNull null
                if (isSignatureOnlySupertype(cls, supertypes, edgesToOwn)) return@mapNotNull null

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

    /**
     * A port interface naming its domain types has no same-package edges by nature, so gravity can
     * only ever conclude "move me to whoever I name" at confidence 1.00. Suppress that: the edges are
     * the interface's signature, not a statement about where the interface belongs.
     */
    private fun isSignatureOnlySupertype(cls: ClassName, supertypes: Set<ClassName>, edgesToOwn: Int): Boolean =
        edgesToOwn == 0 && cls in supertypes

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
