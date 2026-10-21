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
    fun `cycles counts the detected cycles and is zero when there are none`() {
        val cycle = CycleDetail(listOf(PackageName("a"), PackageName("b")), emptyList<CycleEdge>())

        assertEquals(mapOf("cycles" to 2.0), RunMetrics.cycles(listOf(cycle, cycle)))
        assertEquals(mapOf("cycles" to 0.0), RunMetrics.cycles(emptyList()))
    }

    @Test
    fun `dsm counts packages, package-pair dependencies and mutual pairs`() {
        val a = PackageName("a")
        val b = PackageName("b")
        val matrix = DsmMatrix(listOf(a, b), mapOf((a to b) to 2, (b to a) to 1), emptyMap())

        assertEquals(mapOf("packages" to 2.0, "dependencies" to 2.0, "cyclicPairs" to 1.0), RunMetrics.dsm(matrix))
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

        assertEquals(mapOf("violations" to 2.0, "adapterViolations" to 2.0, "testViolations" to 0.0), RunMetrics.testCoupling(result))
        assertEquals(mapOf("violations" to 0.0, "adapterViolations" to 0.0, "testViolations" to 0.0), RunMetrics.testCoupling(null))
    }
}
