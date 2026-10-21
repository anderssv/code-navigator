package no.f12.codenavigator.history

import no.f12.codenavigator.navigation.dsm.CycleDetail
import no.f12.codenavigator.navigation.dsm.CycleEdge
import no.f12.codenavigator.navigation.dsm.DsmMatrix
import no.f12.codenavigator.navigation.testcoupling.CouplingSubjectKind
import no.f12.codenavigator.navigation.testcoupling.TestCouplingResult
import no.f12.codenavigator.navigation.testcoupling.TestCouplingViolation
import no.f12.codenavigator.navigation.relations.callgraph.MethodRef
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import kotlin.test.Test
import kotlin.test.assertEquals

class RunMetricsTest {

    @Test
    fun `cycles are identified by the package edges inside them, so a growing cycle is a small diff`() {
        val ab = CycleEdge(PackageName("a"), PackageName("b"), emptySet())
        val ba = CycleEdge(PackageName("b"), PackageName("a"), emptySet())
        val bc = CycleEdge(PackageName("b"), PackageName("c"), emptySet())
        val small = CycleDetail(listOf(PackageName("a"), PackageName("b")), listOf(ab, ba))
        val grown = CycleDetail(listOf(PackageName("a"), PackageName("b"), PackageName("c")), listOf(ab, ba, bc))

        val before = RunMetrics.cycles(listOf(small)).items.getValue("cycleEdge")
        val after = RunMetrics.cycles(listOf(grown)).items.getValue("cycleEdge")

        assertEquals(setOf("a -> b", "b -> a"), before)
        assertEquals(setOf("b -> c"), after - before)
    }

    @Test
    fun `cycles counts the detected cycles and is zero when there are none`() {
        val cycle = CycleDetail(listOf(PackageName("a"), PackageName("b")), emptyList<CycleEdge>())

        assertEquals(mapOf("cycles" to 2.0), RunMetrics.cycles(listOf(cycle, cycle)).metrics)
        assertEquals(mapOf("cycles" to 0.0), RunMetrics.cycles(emptyList()).metrics)
    }

    @Test
    fun `dsm counts packages, package-pair dependencies and mutual pairs`() {
        val a = PackageName("a")
        val b = PackageName("b")
        val matrix = DsmMatrix(listOf(a, b), mapOf((a to b) to 2, (b to a) to 1), emptyMap())

        val snapshot = RunMetrics.dsm(matrix)
        assertEquals(mapOf("packages" to 2.0, "dependencies" to 2.0, "cyclicPairs" to 1.0), snapshot.metrics)
        assertEquals(setOf("a ↔ b"), snapshot.items["cyclicPair"])
    }

    @Test
    fun `test coupling splits adapter and test violations and tolerates a missing result`() {
        fun violation(kind: CouplingSubjectKind) = TestCouplingViolation(
            ClassName("com.app.Caller"), "m", ClassName("com.app.Repo"), MethodRef(ClassName("com.app.Repo"), "update"), kind,
        )
        val result = TestCouplingResult(
            violations = listOf(violation(CouplingSubjectKind.ADAPTER), violation(CouplingSubjectKind.ADAPTER)),
            testClassNonPortCalls = emptyMap(),
        )

        assertEquals(mapOf("violations" to 2.0, "adapterViolations" to 2.0, "testViolations" to 0.0), RunMetrics.testCoupling(result).metrics)
        assertEquals(setOf("com.app.Caller.m -> com.app.Repo.update"), RunMetrics.testCoupling(result).items["adapterViolation"])
        assertEquals(mapOf("violations" to 0.0, "adapterViolations" to 0.0, "testViolations" to 0.0), RunMetrics.testCoupling(null).metrics)
    }
}
