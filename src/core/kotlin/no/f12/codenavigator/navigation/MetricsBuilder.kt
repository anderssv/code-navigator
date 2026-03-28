package no.f12.codenavigator.navigation

import no.f12.codenavigator.analysis.Hotspot
import no.f12.codenavigator.navigation.rank.RankedType

data class MetricsResult(
    val totalClasses: Int,
    val packageCount: Int,
    val averageFanIn: Double,
    val averageFanOut: Double,
    val cycleCount: Int,
    val deadClassCount: Int,
    val deadMethodCount: Int,
    val topHotspots: List<Hotspot>,
)

object MetricsBuilder {

    fun build(
        classes: List<ClassInfo>,
        packages: List<PackageName>,
        rankedTypes: List<RankedType>,
        cyclicPairCount: Int,
        deadCode: List<DeadCode>,
        hotspots: List<Hotspot>,
    ): MetricsResult = MetricsResult(
        totalClasses = classes.size,
        packageCount = packages.size,
        averageFanIn = rankedTypes.averageOrZero { it.inDegree },
        averageFanOut = rankedTypes.averageOrZero { it.outDegree },
        cycleCount = cyclicPairCount,
        deadClassCount = deadCode.count { it.kind == DeadCodeKind.CLASS },
        deadMethodCount = deadCode.count { it.kind == DeadCodeKind.METHOD },
        topHotspots = hotspots,
    )

    private fun <T> List<T>.averageOrZero(selector: (T) -> Int): Double =
        if (isEmpty()) 0.0 else map(selector).average()
}
