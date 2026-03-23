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
}
