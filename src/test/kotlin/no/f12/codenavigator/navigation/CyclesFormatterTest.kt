package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.dsm.CycleDetail
import no.f12.codenavigator.navigation.dsm.CycleEdge
import no.f12.codenavigator.navigation.dsm.CyclesFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CyclesFormatterTest {

    @Test
    fun `no cycles produces message`() {
        val output = CyclesFormatter.format(emptyList())

        assertEquals("No dependency cycles found.", output)
    }

    @Test
    fun `formats a single cycle with class-level edges`() {
        val details = listOf(
            CycleDetail(
                packages = listOf(PackageName("api"), PackageName("service")),
                edges = listOf(
                    CycleEdge(PackageName("api"), PackageName("service"), setOf(ClassName("api.Controller") to ClassName("service.Service"))),
                    CycleEdge(PackageName("service"), PackageName("api"), setOf(ClassName("service.Service") to ClassName("api.Controller"))),
                ),
            ),
        )

        val output = CyclesFormatter.format(details)

        assertTrue(output.contains("CYCLE: api, service"))
        assertTrue(output.contains("api -> service:"))
        assertTrue(output.contains("api.Controller -> service.Service"))
        assertTrue(output.contains("service -> api:"))
        assertTrue(output.contains("service.Service -> api.Controller"))
    }

    @Test
    fun `formats multiple cycles separated by blank lines`() {
        val details = listOf(
            CycleDetail(
                packages = listOf(PackageName("a"), PackageName("b")),
                edges = listOf(
                    CycleEdge(PackageName("a"), PackageName("b"), setOf(ClassName("a.X") to ClassName("b.Y"))),
                    CycleEdge(PackageName("b"), PackageName("a"), setOf(ClassName("b.Y") to ClassName("a.X"))),
                ),
            ),
            CycleDetail(
                packages = listOf(PackageName("x"), PackageName("y"), PackageName("z")),
                edges = listOf(
                    CycleEdge(PackageName("x"), PackageName("y"), setOf(ClassName("x.A") to ClassName("y.B"))),
                    CycleEdge(PackageName("y"), PackageName("z"), setOf(ClassName("y.B") to ClassName("z.C"))),
                    CycleEdge(PackageName("z"), PackageName("x"), setOf(ClassName("z.C") to ClassName("x.A"))),
                ),
            ),
        )

        val output = CyclesFormatter.format(details)

        assertTrue(output.contains("CYCLE: a, b"))
        assertTrue(output.contains("CYCLE: x, y, z"))
        assertTrue(output.contains("\n\n"), "Cycles should be separated by blank line")
    }
}
