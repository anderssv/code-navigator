package no.f12.codenavigator.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DsmFormatterTest {

    @Test
    fun `empty matrix produces no-dependencies message`() {
        val matrix = DsmMatrix(emptyList(), emptyMap(), emptyMap())

        val result = DsmFormatter.format(matrix)

        assertEquals("No inter-package dependencies found.", result)
    }

    @Test
    fun `formats two-package matrix with numbered legend`() {
        val matrix = DsmMatrix(
            packages = listOf("api", "model"),
            cells = mapOf("api" to "model" to 3),
            classDependencies = emptyMap(),
        )

        val result = DsmFormatter.format(matrix)

        assertTrue(result.contains("=== Dependency Structure Matrix (DSM) ==="))
        assertTrue(result.contains("1: api"))
        assertTrue(result.contains("2: model"))
    }

    @Test
    fun `diagonal cells show dot`() {
        val matrix = DsmMatrix(
            packages = listOf("api", "model"),
            cells = mapOf("api" to "model" to 1),
            classDependencies = emptyMap(),
        )

        val result = DsmFormatter.format(matrix)
        val lines = result.lines()

        val apiRow = lines.find { it.contains("1.") && it.contains("api") }
        assertTrue(apiRow != null, "Should have a row for api")
        assertTrue(apiRow.contains("."), "Diagonal should show dot")
    }

    @Test
    fun `non-zero cells show count`() {
        val matrix = DsmMatrix(
            packages = listOf("api", "model"),
            cells = mapOf("api" to "model" to 5),
            classDependencies = emptyMap(),
        )

        val result = DsmFormatter.format(matrix)
        val lines = result.lines()

        val apiRow = lines.find { it.contains("1.") && it.contains("api") }
        assertTrue(apiRow != null)
        assertTrue(apiRow.contains("5"), "Cell should show count 5")
    }

    @Test
    fun `detects and warns about cyclic dependencies`() {
        val matrix = DsmMatrix(
            packages = listOf("api", "service"),
            cells = mapOf(
                "api" to "service" to 2,
                "service" to "api" to 1,
            ),
            classDependencies = mapOf(
                ("api" to "service") to setOf("Controller" to "Service"),
                ("service" to "api") to setOf("Service" to "Controller"),
            ),
        )

        val result = DsmFormatter.format(matrix)

        assertTrue(result.contains("Cyclic dependencies detected"))
        assertTrue(result.contains("api <-> service"))
    }

    @Test
    fun `no cyclic warning when dependencies are one-directional`() {
        val matrix = DsmMatrix(
            packages = listOf("api", "model"),
            cells = mapOf("api" to "model" to 1),
            classDependencies = emptyMap(),
        )

        val result = DsmFormatter.format(matrix)

        assertTrue(!result.contains("Cyclic"), "Should not warn about cycles")
    }

    @Test
    fun `column headers are numeric indices`() {
        val matrix = DsmMatrix(
            packages = listOf("api", "model", "service"),
            cells = mapOf("api" to "model" to 1),
            classDependencies = emptyMap(),
        )

        val result = DsmFormatter.format(matrix)
        val lines = result.lines()

        val headerLine = lines.find { it.contains("1") && it.contains("2") && it.contains("3") && !it.contains(":") }
        assertTrue(headerLine != null, "Should have a numeric header line")
    }

    // === formatCycles tests ===

    @Test
    fun `formatCycles with no cycles produces no-cycles message`() {
        val matrix = DsmMatrix(emptyList(), emptyMap(), emptyMap())

        val result = DsmFormatter.formatCycles(matrix)

        assertEquals("No cyclic dependencies found.", result)
    }

    @Test
    fun `formatCycles shows cycle with ref counts and class edges`() {
        val matrix = DsmMatrix(
            packages = listOf("api", "service"),
            cells = mapOf(
                "api" to "service" to 2,
                "service" to "api" to 1,
            ),
            classDependencies = mapOf(
                ("api" to "service") to setOf("Controller" to "Service", "Filter" to "Service"),
                ("service" to "api") to setOf("Service" to "Controller"),
            ),
        )

        val result = DsmFormatter.formatCycles(matrix)

        assertTrue(result.contains("CYCLE: api <-> service (2 refs / 1 ref)"))
        assertTrue(result.contains("  api -> service:"))
        assertTrue(result.contains("    api.Controller -> service.Service"))
        assertTrue(result.contains("    api.Filter -> service.Service"))
        assertTrue(result.contains("  service -> api:"))
        assertTrue(result.contains("    service.Service -> api.Controller"))
    }

    @Test
    fun `formatCycles with one-directional deps shows no-cycles message`() {
        val matrix = DsmMatrix(
            packages = listOf("api", "model"),
            cells = mapOf("api" to "model" to 3),
            classDependencies = mapOf(
                ("api" to "model") to setOf("Controller" to "User"),
            ),
        )

        val result = DsmFormatter.formatCycles(matrix)

        assertEquals("No cyclic dependencies found.", result)
    }

    @Test
    fun `formatCycles lists multiple cycles separately`() {
        val matrix = DsmMatrix(
            packages = listOf("api", "model", "service"),
            cells = mapOf(
                "api" to "service" to 1,
                "service" to "api" to 1,
                "model" to "service" to 1,
                "service" to "model" to 1,
            ),
            classDependencies = mapOf(
                ("api" to "service") to setOf("Controller" to "Service"),
                ("service" to "api") to setOf("Service" to "Controller"),
                ("model" to "service") to setOf("User" to "Service"),
                ("service" to "model") to setOf("Service" to "User"),
            ),
        )

        val result = DsmFormatter.formatCycles(matrix)

        assertTrue(result.contains("CYCLE: api <-> service"))
        assertTrue(result.contains("CYCLE: model <-> service"))
    }

    // === cycleFilter tests ===

    @Test
    fun `formatCycles with cycleFilter shows only the matching cycle`() {
        val matrix = DsmMatrix(
            packages = listOf("api", "model", "service"),
            cells = mapOf(
                "api" to "service" to 1,
                "service" to "api" to 1,
                "model" to "service" to 1,
                "service" to "model" to 1,
            ),
            classDependencies = mapOf(
                ("api" to "service") to setOf("Controller" to "Service"),
                ("service" to "api") to setOf("Service" to "Controller"),
                ("model" to "service") to setOf("User" to "Service"),
                ("service" to "model") to setOf("Service" to "User"),
            ),
        )

        val result = DsmFormatter.formatCycles(matrix, cycleFilter = "api" to "service")

        assertTrue(result.contains("CYCLE: api <-> service"))
        assertTrue(!result.contains("CYCLE: model <-> service"))
    }

    @Test
    fun `formatCycles with cycleFilter in reverse order still matches`() {
        val matrix = DsmMatrix(
            packages = listOf("api", "service"),
            cells = mapOf(
                "api" to "service" to 2,
                "service" to "api" to 1,
            ),
            classDependencies = mapOf(
                ("api" to "service") to setOf("Controller" to "Service"),
                ("service" to "api") to setOf("Service" to "Controller"),
            ),
        )

        val result = DsmFormatter.formatCycles(matrix, cycleFilter = "service" to "api")

        assertTrue(result.contains("CYCLE: api <-> service"))
    }

    @Test
    fun `formatCycles with cycleFilter that matches no cycle shows no-cycles message`() {
        val matrix = DsmMatrix(
            packages = listOf("api", "service"),
            cells = mapOf(
                "api" to "service" to 1,
                "service" to "api" to 1,
            ),
            classDependencies = mapOf(
                ("api" to "service") to setOf("Controller" to "Service"),
                ("service" to "api") to setOf("Service" to "Controller"),
            ),
        )

        val result = DsmFormatter.formatCycles(matrix, cycleFilter = "model" to "service")

        assertEquals("No cyclic dependencies found.", result)
    }
}
