package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName

data class StructureGroup(
    val targetPackage: PackageName,
    val classes: List<MoveSuggestion>,
)

data class StructureResult(
    val groups: List<StructureGroup>,
    val driftScore: Double,
    val totalClassCount: Int,
    val misplacedCount: Int,
)

object StructureGrouper {

    fun group(
        suggestions: MoveSuggestionResult,
        totalClassCount: Int,
        minGroupSize: Int = 2,
    ): StructureResult {
        val grouped = suggestions.suggestions
            .groupBy { it.suggestedPackage }
            .filter { (_, classes) -> classes.size >= minGroupSize }
            .map { (pkg, classes) -> StructureGroup(pkg, classes.sortedByDescending { it.confidence }) }
            .sortedByDescending { it.classes.size }

        val misplacedCount = suggestions.suggestions.size
        val driftScore = if (totalClassCount > 0) misplacedCount.toDouble() / totalClassCount else 0.0

        return StructureResult(grouped, driftScore, totalClassCount, misplacedCount)
    }
}
