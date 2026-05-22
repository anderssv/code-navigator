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

    fun suggest(
        dependencies: List<PackageDependency>,
        top: Int = Int.MAX_VALUE,
        maxFanIn: Int = Int.MAX_VALUE,
    ): MoveSuggestionResult {
        val ubiquitousTargets = dependencies
            .groupBy { it.targetClass }
            .filter { (_, edges) -> edges.map { it.sourceClass }.distinct().size >= maxFanIn }
            .keys

        val filteredDeps = dependencies.filter { it.targetClass !in ubiquitousTargets }

        val classes = filteredDeps.map { it.sourceClass to it.sourcePackage }.distinct()

        val suggestions = classes.mapNotNull { (cls, currentPkg) ->
            val outgoing = filteredDeps.filter { it.sourceClass == cls }
            if (outgoing.isEmpty()) return@mapNotNull null

            val edgesByTarget = outgoing.groupBy { it.targetPackage }
                .mapValues { (_, edges) -> edges.size }

            val edgesToOwn = edgesByTarget[currentPkg] ?: 0
            val bestOther = edgesByTarget.filter { it.key != currentPkg }
                .maxByOrNull { it.value } ?: return@mapNotNull null

            if (bestOther.value > edgesToOwn) {
                val total = outgoing.size
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
}
