package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.SourceSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TestInvolvementTest {

    private val sourceSets = mapOf(
        ClassName("com.app.web.Controller") to SourceSet.MAIN,
        ClassName("com.app.domain.Order") to SourceSet.MAIN,
        ClassName("com.app.test.ControllerTest") to SourceSet.TEST,
        ClassName("com.app.test.Fixtures") to SourceSet.TEST,
    )
    private val lookup: (ClassName) -> SourceSet? = { sourceSets[it] }

    @Test
    fun `counts edges where either side is a test class`() {
        val edges = listOf(
            ClassName("com.app.web.Controller") to ClassName("com.app.domain.Order"), // prod-prod
            ClassName("com.app.test.ControllerTest") to ClassName("com.app.web.Controller"), // test source
            ClassName("com.app.web.Controller") to ClassName("com.app.test.Fixtures"), // test target
        )

        val counts = TestInvolvement.count(edges, lookup)

        assertEquals(2, counts.testInvolved)
        assertEquals(3, counts.total)
    }

    @Test
    fun `unknown source set is treated as non-test`() {
        val edges = listOf(ClassName("com.app.unknown.A") to ClassName("com.app.unknown.B"))

        val counts = TestInvolvement.count(edges, lookup)

        assertEquals(0, counts.testInvolved)
        assertEquals(1, counts.total)
    }

    @Test
    fun `notice renders ratio and re-run hint`() {
        val notice = TestInvolvement.notice(TestInvolvement.Counts(testInvolved = 23, total = 41), "violations")

        assertEquals(
            "test-involvement: 23 of 41 violations involve test sources. Re-run with --scope=prod for production-only architecture signal.",
            notice,
        )
    }

    @Test
    fun `notice is null when there are no edges`() {
        assertNull(TestInvolvement.notice(TestInvolvement.Counts(0, 0), "violations"))
    }

    @Test
    fun `notice shown even when zero test sources involved confirms prod-clean`() {
        val notice = TestInvolvement.notice(TestInvolvement.Counts(testInvolved = 0, total = 12), "cycle edges")

        assertEquals(
            "test-involvement: 0 of 12 cycle edges involve test sources. Re-run with --scope=prod for production-only architecture signal.",
            notice,
        )
    }
}
