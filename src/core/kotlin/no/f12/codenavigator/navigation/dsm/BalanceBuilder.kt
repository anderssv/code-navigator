package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.analysis.PackageVolatilityResult
import no.f12.codenavigator.navigation.core.PackageName

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

    private const val DISTANCE_THRESHOLD = 3
    private const val STRENGTH_HIGH_LEVEL = 3 // FUNCTIONAL

    fun build(
        strength: StrengthResult,
        distance: PackageDistanceResult,
        volatility: PackageVolatilityResult,
        top: Int = Int.MAX_VALUE,
    ): BalanceResult {
        if (strength.entries.isEmpty()) return BalanceResult(entries = emptyList())

        val distanceMap = distance.entries.associateBy { it.source to it.target }
        val volatilityMap = volatility.entries.associateBy { it.packageName }
        val volatilityMedian = computeMedianRevisions(volatility)

        val entries = strength.entries.map { entry ->
            val dist = distanceMap[entry.source to entry.target]?.distance
                ?: PackageDistanceCalculator.distance(entry.source, entry.target)

            val sourceVol = volatilityMap[entry.source.value]?.revisions ?: 0
            val targetVol = volatilityMap[entry.target.value]?.revisions ?: 0

            val strengthHigh = entry.strength.level >= STRENGTH_HIGH_LEVEL
            val distanceHigh = dist >= DISTANCE_THRESHOLD
            val volatilityHigh = maxOf(sourceVol, targetVol) >= volatilityMedian && volatilityMedian > 0

            val verdict = classify(strengthHigh, distanceHigh, volatilityHigh)
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

    private fun classify(
        strengthHigh: Boolean,
        distanceHigh: Boolean,
        volatilityHigh: Boolean,
    ): BalanceVerdict {
        val modularityGood = strengthHigh != distanceHigh // XOR: good when they differ
        if (modularityGood) return BalanceVerdict.BALANCED

        // Modularity is poor (both high or both low)
        if (!volatilityHigh) {
            return if (!strengthHigh && !distanceHigh) {
                BalanceVerdict.OVER_ENGINEERED
            } else {
                BalanceVerdict.TOLERABLE
            }
        }

        // Modularity poor + high volatility
        return if (strengthHigh && distanceHigh) {
            BalanceVerdict.DANGER
        } else {
            BalanceVerdict.OVER_ENGINEERED
        }
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
