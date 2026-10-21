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
 * What to watch between runs, per analysis task: a few counts the task already has in hand, and — where the
 * task reports individual findings — an identifier per finding, so two runs can be diffed down to which
 * cycle appeared or which violation was fixed (a count alone hides one-in-one-out). No extra analysis.
 * Tasks with no individual findings worth tracking (metrics, complexity, package-deps) are counts only.
 */
object RunMetrics {

    fun dsm(matrix: DsmMatrix): RunSnapshot {
        val pairs = matrix.findCyclicPairs()
        return snapshot(
            counts("packages" to matrix.packages.size, "dependencies" to matrix.cells.size, "cyclicPairs" to pairs.size),
            "cyclicPair" to pairs.map { (a, b, _) -> listOf(a.value, b.value).sorted().joinToString(" ↔ ") },
        )
    }

    /**
     * A cycle is a whole strongly connected group of packages, which can be a dozen packages long — as an
     * identifier that would turn "one more package joined the cycle" into "one huge cycle gone, another huge
     * one appeared". The package-to-package edges that sit inside cycles are what actually come and go.
     */
    fun cycles(details: List<CycleDetail>): RunSnapshot = snapshot(
        counts("cycles" to details.size),
        "cycleEdge" to details.flatMap { cycle -> cycle.edges.map { "${it.from} -> ${it.to}" } },
    )

    fun rings(output: HexRingsOutput): RunSnapshot = snapshot(
        counts(
            "rings" to output.layering.ringCount,
            "adapters" to output.graph.ioClasses.size,
            "violations" to output.layering.violations.size,
            "domainServiceViolations" to output.domainServiceViolations.size,
            "portBypassViolations" to output.portBypassViolations.size,
        ),
        "violation" to output.layering.violations.map { "${it.sourceClass} -> ${it.targetClass}" },
        "domainServiceViolation" to output.domainServiceViolations.map { "${it.sourceClass} -> ${it.targetClass}" },
        "portBypassViolation" to output.portBypassViolations.map { "${it.sourceClass} -> ${it.targetClass}" },
    )

    fun balance(result: BalanceResult?): RunSnapshot {
        val entries = result?.entries.orEmpty()
        return snapshot(
            counts(
                "pairs" to entries.size,
                "danger" to entries.count { it.verdict == BalanceVerdict.DANGER },
                "overEngineered" to entries.count { it.verdict == BalanceVerdict.OVER_ENGINEERED },
            ),
            "imbalance" to entries.filter { it.verdict != BalanceVerdict.BALANCED }.map { "${it.source} -> ${it.target} [${it.verdict}]" },
        )
    }

    fun strength(result: StrengthResult?): RunSnapshot {
        val entries = result?.entries.orEmpty()
        return snapshot(
            counts(
                "pairs" to entries.size,
                "functional" to entries.count { it.strength == IntegrationStrength.FUNCTIONAL },
                "model" to entries.count { it.strength == IntegrationStrength.MODEL },
                "contract" to entries.count { it.strength == IntegrationStrength.CONTRACT },
            ),
            "coupling" to entries.filter { it.strength != IntegrationStrength.CONTRACT }.map { "${it.source} -> ${it.target} [${it.strength}]" },
        )
    }

    fun cohesion(result: CohesionResult?): RunSnapshot {
        val entries = result?.entries.orEmpty()
        return snapshot(
            counts(
                "packages" to entries.size,
                "review" to entries.count { it.verdict == CohesionVerdict.REVIEW },
                "thinLayer" to entries.count { it.verdict == CohesionVerdict.THIN_LAYER },
            ),
            "package" to entries.filter { it.verdict != CohesionVerdict.COHESIVE }.map { "${it.packageName} [${it.verdict}]" },
        )
    }

    fun metrics(result: MetricsResult): RunSnapshot = RunSnapshot(
        mapOf(
            "classes" to result.totalClasses.toDouble(),
            "packages" to result.packageCount.toDouble(),
            "cycles" to result.cycleCount.toDouble(),
            "deadClasses" to result.deadClassCount.toDouble(),
            "deadMethods" to result.deadMethodCount.toDouble(),
            "avgFanIn" to result.averageFanIn,
            "avgFanOut" to result.averageFanOut,
        ),
    )

    fun dead(items: List<DeadCode>): RunSnapshot = snapshot(
        counts(
            "dead" to items.size,
            "highConfidence" to items.count { it.confidence == DeadCodeConfidence.HIGH },
            "mediumConfidence" to items.count { it.confidence == DeadCodeConfidence.MEDIUM },
            "lowConfidence" to items.count { it.confidence == DeadCodeConfidence.LOW },
        ),
        "dead" to items.map { item -> item.memberName?.let { "${item.className}.$it" } ?: item.className.value },
    )

    fun testCoupling(result: TestCouplingResult?): RunSnapshot {
        val actionable = result?.actionableViolations.orEmpty()
        val adapter = actionable.filter { it.subjectKind == CouplingSubjectKind.ADAPTER }
        val test = actionable.filter { it.subjectKind == CouplingSubjectKind.TEST }
        fun label(v: no.f12.codenavigator.navigation.testcoupling.TestCouplingViolation) =
            "${v.testClass}.${v.testMethod} -> ${v.portInterface}.${v.portMethod.methodName}"
        return snapshot(
            counts("violations" to actionable.size, "adapterViolations" to adapter.size, "testViolations" to test.size),
            "adapterViolation" to adapter.map(::label),
            "testViolation" to test.map(::label),
        )
    }

    fun packageDeps(deps: PackageDependencies, packages: List<*>): RunSnapshot = RunSnapshot(
        counts(
            "packages" to packages.size,
            "dependencies" to deps.allPackages().sumOf { deps.dependenciesOf(it).size },
        ),
    )

    fun complexity(results: List<ClassComplexity>): RunSnapshot = RunSnapshot(
        counts(
            "classes" to results.size,
            "maxFanOut" to (results.maxOfOrNull { it.fanOut } ?: 0),
            "maxFanIn" to (results.maxOfOrNull { it.fanIn } ?: 0),
        ),
    )

    private fun snapshot(metrics: Map<String, Double>, vararg sets: Pair<String, List<String>>): RunSnapshot =
        RunSnapshot(metrics, sets.associate { (name, items) -> name to items.toSet() }.filterValues { it.isNotEmpty() })

    private fun counts(vararg pairs: Pair<String, Int>): Map<String, Double> =
        pairs.associate { (name, value) -> name to value.toDouble() }
}
