package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.PackageName
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

    @Test
    fun `shows prefix header when displayPrefix is non-empty`() {
        val details = listOf(
            CycleDetail(
                packages = listOf(PackageName("api"), PackageName("service")),
                edges = listOf(
                    CycleEdge(PackageName("api"), PackageName("service"), setOf(ClassName("api.Controller") to ClassName("service.Service"))),
                    CycleEdge(PackageName("service"), PackageName("api"), setOf(ClassName("service.Service") to ClassName("api.Controller"))),
                ),
            ),
        )

        val output = CyclesFormatter.format(details, displayPrefix = PackageName("com.example"))

        assertTrue(output.contains("Common prefix: com.example"), "Should show common prefix header, got:\n$output")
        assertTrue(output.contains("CYCLE:"), "Should still show cycle info")
    }

    @Test
    fun `omits prefix header when displayPrefix is empty`() {
        val details = listOf(
            CycleDetail(
                packages = listOf(PackageName("api"), PackageName("service")),
                edges = listOf(
                    CycleEdge(PackageName("api"), PackageName("service"), setOf(ClassName("api.Controller") to ClassName("service.Service"))),
                    CycleEdge(PackageName("service"), PackageName("api"), setOf(ClassName("service.Service") to ClassName("api.Controller"))),
                ),
            ),
        )

        val output = CyclesFormatter.format(details, displayPrefix = PackageName(""))

        assertTrue(!output.contains("Common prefix:"), "Should not show prefix when empty")
    }

    @Test
    fun `strips class names when displayPrefix is set`() {
        val details = listOf(
            CycleDetail(
                packages = listOf(PackageName("api"), PackageName("service")),
                edges = listOf(
                    CycleEdge(PackageName("api"), PackageName("service"), setOf(ClassName("com.example.api.Controller") to ClassName("com.example.service.Service"))),
                    CycleEdge(PackageName("service"), PackageName("api"), setOf(ClassName("com.example.service.Service") to ClassName("com.example.api.Controller"))),
                ),
            ),
        )

        val output = CyclesFormatter.format(details, displayPrefix = PackageName("com.example"))

        assertTrue(output.contains("api.Controller -> service.Service"), "Should show stripped class names, got:\n$output")
        assertTrue(!output.contains("com.example.api.Controller"), "Should not show full class names, got:\n$output")
    }

    @Test
    fun `shows full class names when displayPrefix is empty`() {
        val details = listOf(
            CycleDetail(
                packages = listOf(PackageName("api"), PackageName("service")),
                edges = listOf(
                    CycleEdge(PackageName("api"), PackageName("service"), setOf(ClassName("com.example.api.Controller") to ClassName("com.example.service.Service"))),
                    CycleEdge(PackageName("service"), PackageName("api"), setOf(ClassName("com.example.service.Service") to ClassName("com.example.api.Controller"))),
                ),
            ),
        )

        val output = CyclesFormatter.format(details, displayPrefix = PackageName(""))

        assertTrue(output.contains("com.example.api.Controller -> com.example.service.Service"), "Should show full class names when no prefix, got:\n$output")
    }
}
