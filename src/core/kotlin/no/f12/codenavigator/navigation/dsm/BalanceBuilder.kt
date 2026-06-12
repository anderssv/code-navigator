package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.analysis.PackageVolatilityResult
import no.f12.codenavigator.navigation.types.PackageName

enum class BalanceVerdict(val severity: Int) {
    BALANCED(0),
    TOLERABLE(1),
    OVER_ENGINEERED(2),
    DANGER(3),
}

data class BalanceEntry(
    val source: PackageName,
    val target: PackageName,
    val strength: IntegrationStrength,
    val distance: Int,
    val sourceVolatility: Int,
    val targetVolatility: Int,
    val verdict: BalanceVerdict,
    val suggestion: String,
)

data class BalanceResult(
    val entries: List<BalanceEntry>,
)

object BalanceBuilder {

    private const val DISTANCE_THRESHOLD = 2
    private const val STRENGTH_HIGH_LEVEL = 3 // FUNCTIONAL

    /**
     * @param rings topological ring per package (from RingDetector). "Distance" is the number of
     *   rings an edge crosses — a dependency-structure signal, not package-name nesting depth.
     * @param compositionRoots packages that wire many rings together (DI/composition). Edges
     *   originating from these are never flagged DANGER — their high fan-out is by design.
     */
    fun build(
        strength: StrengthResult,
        rings: Map<PackageName, Int>,
        compositionRoots: Set<PackageName>,
        volatility: PackageVolatilityResult,
        top: Int = Int.MAX_VALUE,
    ): BalanceResult {
        if (strength.entries.isEmpty()) return BalanceResult(entries = emptyList())

        val volatilityMap = volatility.entries.associateBy { it.packageName }
        val volatilityMedian = computeMedianRevisions(volatility)

        val entries = strength.entries.map { entry ->
            val dist = ringSeparation(rings, entry.source, entry.target)

            val sourceVol = volatilityMap[entry.source.value]?.revisions ?: 0
            val targetVol = volatilityMap[entry.target.value]?.revisions ?: 0

            val strengthHigh = entry.strength.level >= STRENGTH_HIGH_LEVEL
            val distanceHigh = dist >= DISTANCE_THRESHOLD
            val volatilityHigh = maxOf(sourceVol, targetVol) >= volatilityMedian && volatilityMedian > 0
            val isCompositionRoot = entry.source in compositionRoots

            val verdict = classify(strengthHigh, distanceHigh, volatilityHigh, isCompositionRoot)
            val suggestion = suggest(verdict)

            BalanceEntry(
                source = entry.source,
                target = entry.target,
                strength = entry.strength,
                distance = dist,
                sourceVolatility = sourceVol,
                targetVolatility = targetVol,
                verdict = verdict,
                suggestion = suggestion,
            )
        }
            .sortedWith(
                compareByDescending<BalanceEntry> { it.verdict.severity }
                    .thenByDescending { it.strength.level }
                    .thenBy { it.source.value }
                    .thenBy { it.target.value },
            )
            .take(top)

        return BalanceResult(entries = entries)
    }

    /**
     * Number of rings the edge crosses. Packages without a known ring (e.g. external or
     * unranked) contribute 0 separation so they are never spuriously flagged.
     */
    private fun ringSeparation(rings: Map<PackageName, Int>, source: PackageName, target: PackageName): Int {
        val sourceRing = rings[source] ?: return 0
        val targetRing = rings[target] ?: return 0
        return kotlin.math.abs(sourceRing - targetRing)
    }

    private fun classify(
        strengthHigh: Boolean,
        distanceHigh: Boolean,
        volatilityHigh: Boolean,
        isCompositionRoot: Boolean,
    ): BalanceVerdict {
        val modularityGood = strengthHigh != distanceHigh // XOR: good when they differ
        if (modularityGood) return BalanceVerdict.BALANCED

        // Modularity is poor (both high or both low)
        if (!strengthHigh && !distanceHigh) {
            // Low coupling at short distance = intentional layering (e.g. domain.service → domain.model).
            // MODEL/CONTRACT strength between nearby packages is healthy architecture, not over-engineering.
            return if (volatilityHigh) BalanceVerdict.TOLERABLE else BalanceVerdict.BALANCED
        }

        if (!volatilityHigh) {
            return BalanceVerdict.TOLERABLE
        }

        // Composition roots wire distant rings together by design — high fan-out to volatile
        // infrastructure is expected, not debt. Downgrade from DANGER to TOLERABLE.
        if (isCompositionRoot) {
            return BalanceVerdict.TOLERABLE
        }

        // High strength + high ring separation + high volatility
        return BalanceVerdict.DANGER
    }

    private fun suggest(verdict: BalanceVerdict): String = when (verdict) {
        BalanceVerdict.DANGER ->
            "Tightly coupled across distant packages in volatile code. Consider co-locating packages or introducing a contract/interface."

        BalanceVerdict.OVER_ENGINEERED ->
            "Loosely coupled nearby packages — consider simplifying or merging."

        BalanceVerdict.TOLERABLE ->
            "Poor modularity but low volatility — monitor for changes."

        BalanceVerdict.BALANCED ->
            ""
    }

    private fun computeMedianRevisions(volatility: PackageVolatilityResult): Int {
        val sorted = volatility.entries.map { it.revisions }.sorted()
        if (sorted.isEmpty()) return 0
        return sorted[(sorted.size - 1) / 2]
    }
}
