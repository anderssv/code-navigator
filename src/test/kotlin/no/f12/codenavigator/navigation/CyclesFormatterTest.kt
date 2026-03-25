package no.f12.codenavigator.navigation

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
                packages = listOf("api", "service"),
                edges = listOf(
                    CycleEdge("api", "service", setOf("api.Controller" to "service.Service")),
                    CycleEdge("service", "api", setOf("service.Service" to "api.Controller")),
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
                packages = listOf("a", "b"),
                edges = listOf(
                    CycleEdge("a", "b", setOf("a.X" to "b.Y")),
                    CycleEdge("b", "a", setOf("b.Y" to "a.X")),
                ),
            ),
            CycleDetail(
                packages = listOf("x", "y", "z"),
                edges = listOf(
                    CycleEdge("x", "y", setOf("x.A" to "y.B")),
                    CycleEdge("y", "z", setOf("y.B" to "z.C")),
                    CycleEdge("z", "x", setOf("z.C" to "x.A")),
                ),
            ),
        )

        val output = CyclesFormatter.format(details)

        assertTrue(output.contains("CYCLE: a, b"))
        assertTrue(output.contains("CYCLE: x, y, z"))
        assertTrue(output.contains("\n\n"), "Cycles should be separated by blank line")
    }
}
