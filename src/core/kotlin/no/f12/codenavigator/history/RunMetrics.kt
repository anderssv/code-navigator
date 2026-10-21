package no.f12.codenavigator.history

import no.f12.codenavigator.navigation.complexity.ClassComplexity
import no.f12.codenavigator.navigation.deadcode.DeadCode
import no.f12.codenavigator.navigation.deadcode.DeadCodeConfidence
import no.f12.codenavigator.navigation.dsm.BalanceResult
import no.f12.codenavigator.navigation.dsm.BalanceVerdict
import no.f12.codenavigator.navigation.dsm.CohesionResult
import no.f12.codenavigator.navigation.dsm.CohesionVerdict
import no.f12.codenavigator.navigation.dsm.CycleDetail
import no.f12.codenavigator.navigation.dsm.DsmMatrix
import no.f12.codenavigator.navigation.dsm.HexRingsOutput
import no.f12.codenavigator.navigation.dsm.IntegrationStrength
import no.f12.codenavigator.navigation.dsm.PackageDependencies
import no.f12.codenavigator.navigation.dsm.StrengthResult
import no.f12.codenavigator.navigation.metrics.MetricsResult
import no.f12.codenavigator.navigation.testcoupling.CouplingSubjectKind
import no.f12.codenavigator.navigation.testcoupling.TestCouplingResult

/**
 * The few numbers per analysis task worth watching between runs. Deliberately counts the task already
 * has in hand — no extra analysis — and names that read naturally in "cycles 3 → 4 (+1)".
 */
object RunMetrics {

    fun dsm(matrix: DsmMatrix): Map<String, Double> = counts(
        "packages" to matrix.packages.size,
        "dependencies" to matrix.cells.size,
        "cyclicPairs" to matrix.findCyclicPairs().size,
    )

    fun cycles(details: List<CycleDetail>): Map<String, Double> = counts("cycles" to details.size)

    fun rings(output: HexRingsOutput): Map<String, Double> = counts(
        "rings" to output.layering.ringCount,
        "adapters" to output.graph.ioClasses.size,
        "violations" to output.layering.violations.size,
        "domainServiceViolations" to output.domainServiceViolations.size,
        "portBypassViolations" to output.portBypassViolations.size,
    )

    fun balance(result: BalanceResult?): Map<String, Double> {
        val entries = result?.entries.orEmpty()
        return counts(
            "pairs" to entries.size,
            "danger" to entries.count { it.verdict == BalanceVerdict.DANGER },
            "overEngineered" to entries.count { it.verdict == BalanceVerdict.OVER_ENGINEERED },
        )
    }

    fun strength(result: StrengthResult?): Map<String, Double> {
        val entries = result?.entries.orEmpty()
        return counts(
            "pairs" to entries.size,
            "functional" to entries.count { it.strength == IntegrationStrength.FUNCTIONAL },
            "model" to entries.count { it.strength == IntegrationStrength.MODEL },
            "contract" to entries.count { it.strength == IntegrationStrength.CONTRACT },
        )
    }

    fun cohesion(result: CohesionResult?): Map<String, Double> {
        val entries = result?.entries.orEmpty()
        return counts(
            "packages" to entries.size,
            "review" to entries.count { it.verdict == CohesionVerdict.REVIEW },
            "thinLayer" to entries.count { it.verdict == CohesionVerdict.THIN_LAYER },
        )
    }

    fun metrics(result: MetricsResult): Map<String, Double> = mapOf(
        "classes" to result.totalClasses.toDouble(),
        "packages" to result.packageCount.toDouble(),
        "cycles" to result.cycleCount.toDouble(),
        "deadClasses" to result.deadClassCount.toDouble(),
        "deadMethods" to result.deadMethodCount.toDouble(),
        "avgFanIn" to result.averageFanIn,
        "avgFanOut" to result.averageFanOut,
    )

    fun dead(items: List<DeadCode>): Map<String, Double> = counts(
        "dead" to items.size,
        "highConfidence" to items.count { it.confidence == DeadCodeConfidence.HIGH },
        "mediumConfidence" to items.count { it.confidence == DeadCodeConfidence.MEDIUM },
        "lowConfidence" to items.count { it.confidence == DeadCodeConfidence.LOW },
    )

    fun testCoupling(result: TestCouplingResult?): Map<String, Double> {
        val actionable = result?.actionableViolations.orEmpty()
        return counts(
            "violations" to actionable.size,
            "adapterViolations" to actionable.count { it.subjectKind == CouplingSubjectKind.ADAPTER },
            "testViolations" to actionable.count { it.subjectKind == CouplingSubjectKind.TEST },
        )
    }

    fun packageDeps(deps: PackageDependencies, packages: List<*>): Map<String, Double> = counts(
        "packages" to packages.size,
        "dependencies" to deps.allPackages().sumOf { deps.dependenciesOf(it).size },
    )

    fun complexity(results: List<ClassComplexity>): Map<String, Double> = counts(
        "classes" to results.size,
        "maxFanOut" to (results.maxOfOrNull { it.fanOut } ?: 0),
        "maxFanIn" to (results.maxOfOrNull { it.fanIn } ?: 0),
    )

    private fun counts(vararg pairs: Pair<String, Int>): Map<String, Double> =
        pairs.associate { (name, value) -> name to value.toDouble() }
}
