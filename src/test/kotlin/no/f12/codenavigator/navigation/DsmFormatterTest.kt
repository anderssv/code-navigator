package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.PackageName
import no.f12.codenavigator.navigation.dsm.DsmFormatter
import no.f12.codenavigator.navigation.dsm.DsmMatrix
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
            packages = listOf(PackageName("api"), PackageName("model")),
            cells = mapOf((PackageName("api") to PackageName("model")) to 3),
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
            packages = listOf(PackageName("api"), PackageName("model")),
            cells = mapOf((PackageName("api") to PackageName("model")) to 1),
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
            packages = listOf(PackageName("api"), PackageName("model")),
            cells = mapOf((PackageName("api") to PackageName("model")) to 5),
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
            packages = listOf(PackageName("api"), PackageName("service")),
            cells = mapOf(
                (PackageName("api") to PackageName("service")) to 2,
                (PackageName("service") to PackageName("api")) to 1,
            ),
            classDependencies = mapOf(
                (PackageName("api") to PackageName("service")) to setOf(ClassName("com.example.api.Controller") to ClassName("com.example.service.Service")),
                (PackageName("service") to PackageName("api")) to setOf(ClassName("com.example.service.Service") to ClassName("com.example.api.Controller")),
            ),
        )

        val result = DsmFormatter.format(matrix)

        assertTrue(result.contains("Cyclic dependencies detected"))
        assertTrue(result.contains("api <-> service"))
    }

    @Test
    fun `no cyclic warning when dependencies are one-directional`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("model")),
            cells = mapOf((PackageName("api") to PackageName("model")) to 1),
            classDependencies = emptyMap(),
        )

        val result = DsmFormatter.format(matrix)

        assertTrue(!result.contains("Cyclic"), "Should not warn about cycles")
    }

    @Test
    fun `column headers are numeric indices`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("model"), PackageName("service")),
            cells = mapOf((PackageName("api") to PackageName("model")) to 1),
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
            packages = listOf(PackageName("api"), PackageName("service")),
            cells = mapOf(
                (PackageName("api") to PackageName("service")) to 2,
                (PackageName("service") to PackageName("api")) to 1,
            ),
            classDependencies = mapOf(
                (PackageName("api") to PackageName("service")) to setOf(
                    ClassName("com.example.api.Controller") to ClassName("com.example.service.Service"),
                    ClassName("com.example.api.Filter") to ClassName("com.example.service.Service"),
                ),
                (PackageName("service") to PackageName("api")) to setOf(
                    ClassName("com.example.service.Service") to ClassName("com.example.api.Controller"),
                ),
            ),
        )

        val result = DsmFormatter.formatCycles(matrix)

        assertTrue(result.contains("CYCLE: api <-> service (2 refs / 1 ref)"))
        assertTrue(result.contains("  api -> service:"))
        assertTrue(result.contains("    com.example.api.Controller -> com.example.service.Service"))
        assertTrue(result.contains("    com.example.api.Filter -> com.example.service.Service"))
        assertTrue(result.contains("  service -> api:"))
        assertTrue(result.contains("    com.example.service.Service -> com.example.api.Controller"))
    }

    @Test
    fun `formatCycles with one-directional deps shows no-cycles message`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("model")),
            cells = mapOf((PackageName("api") to PackageName("model")) to 3),
            classDependencies = mapOf(
                (PackageName("api") to PackageName("model")) to setOf(ClassName("com.example.api.Controller") to ClassName("com.example.model.User")),
            ),
        )

        val result = DsmFormatter.formatCycles(matrix)

        assertEquals("No cyclic dependencies found.", result)
    }

    @Test
    fun `formatCycles lists multiple cycles separately`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("model"), PackageName("service")),
            cells = mapOf(
                (PackageName("api") to PackageName("service")) to 1,
                (PackageName("service") to PackageName("api")) to 1,
                (PackageName("model") to PackageName("service")) to 1,
                (PackageName("service") to PackageName("model")) to 1,
            ),
            classDependencies = mapOf(
                (PackageName("api") to PackageName("service")) to setOf(ClassName("com.example.api.Controller") to ClassName("com.example.service.Service")),
                (PackageName("service") to PackageName("api")) to setOf(ClassName("com.example.service.Service") to ClassName("com.example.api.Controller")),
                (PackageName("model") to PackageName("service")) to setOf(ClassName("com.example.model.User") to ClassName("com.example.service.Service")),
                (PackageName("service") to PackageName("model")) to setOf(ClassName("com.example.service.Service") to ClassName("com.example.model.User")),
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
            packages = listOf(PackageName("api"), PackageName("model"), PackageName("service")),
            cells = mapOf(
                (PackageName("api") to PackageName("service")) to 1,
                (PackageName("service") to PackageName("api")) to 1,
                (PackageName("model") to PackageName("service")) to 1,
                (PackageName("service") to PackageName("model")) to 1,
            ),
            classDependencies = mapOf(
                (PackageName("api") to PackageName("service")) to setOf(ClassName("com.example.api.Controller") to ClassName("com.example.service.Service")),
                (PackageName("service") to PackageName("api")) to setOf(ClassName("com.example.service.Service") to ClassName("com.example.api.Controller")),
                (PackageName("model") to PackageName("service")) to setOf(ClassName("com.example.model.User") to ClassName("com.example.service.Service")),
                (PackageName("service") to PackageName("model")) to setOf(ClassName("com.example.service.Service") to ClassName("com.example.model.User")),
            ),
        )

        val result = DsmFormatter.formatCycles(matrix, cycleFilter = PackageName("api") to PackageName("service"))

        assertTrue(result.contains("CYCLE: api <-> service"))
        assertTrue(!result.contains("CYCLE: model <-> service"))
    }

    @Test
    fun `formatCycles with cycleFilter in reverse order still matches`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("service")),
            cells = mapOf(
                (PackageName("api") to PackageName("service")) to 2,
                (PackageName("service") to PackageName("api")) to 1,
            ),
            classDependencies = mapOf(
                (PackageName("api") to PackageName("service")) to setOf(ClassName("com.example.api.Controller") to ClassName("com.example.service.Service")),
                (PackageName("service") to PackageName("api")) to setOf(ClassName("com.example.service.Service") to ClassName("com.example.api.Controller")),
            ),
        )

        val result = DsmFormatter.formatCycles(matrix, cycleFilter = PackageName("service") to PackageName("api"))

        assertTrue(result.contains("CYCLE: api <-> service"))
    }

    @Test
    fun `formatCycles with cycleFilter that matches no cycle shows no-cycles message`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("service")),
            cells = mapOf(
                (PackageName("api") to PackageName("service")) to 1,
                (PackageName("service") to PackageName("api")) to 1,
            ),
            classDependencies = mapOf(
                (PackageName("api") to PackageName("service")) to setOf(ClassName("com.example.api.Controller") to ClassName("com.example.service.Service")),
                (PackageName("service") to PackageName("api")) to setOf(ClassName("com.example.service.Service") to ClassName("com.example.api.Controller")),
            ),
        )

        val result = DsmFormatter.formatCycles(matrix, cycleFilter = PackageName("model") to PackageName("service"))

        assertEquals("No cyclic dependencies found.", result)
    }

    // === noResultsHints tests ===

    @Test
    fun `format shows prefix header when displayPrefix is non-empty`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("model"), PackageName("web")),
            cells = mapOf((PackageName("web") to PackageName("model")) to 2),
            classDependencies = emptyMap(),
            displayPrefix = PackageName("org.springframework.samples.petclinic"),
        )

        val result = DsmFormatter.format(matrix)

        assertTrue(result.contains("Common prefix: org.springframework.samples.petclinic"), "Should show common prefix header, got:\n$result")
        assertTrue(result.contains("=== Dependency Structure Matrix (DSM) ==="), "Should still show DSM header")
    }

    @Test
    fun `format omits prefix header when displayPrefix is empty`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("model")),
            cells = mapOf((PackageName("api") to PackageName("model")) to 1),
            classDependencies = emptyMap(),
            displayPrefix = PackageName(""),
        )

        val result = DsmFormatter.format(matrix)

        assertTrue(!result.contains("Common prefix:"), "Should not show prefix header when empty")
    }

    @Test
    fun `formatCycles shows prefix header when displayPrefix is non-empty`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("service")),
            cells = mapOf(
                (PackageName("api") to PackageName("service")) to 1,
                (PackageName("service") to PackageName("api")) to 1,
            ),
            classDependencies = mapOf(
                (PackageName("api") to PackageName("service")) to setOf(ClassName("com.example.api.Controller") to ClassName("com.example.service.Service")),
                (PackageName("service") to PackageName("api")) to setOf(ClassName("com.example.service.Service") to ClassName("com.example.api.Controller")),
            ),
            displayPrefix = PackageName("com.example"),
        )

        val result = DsmFormatter.formatCycles(matrix)

        assertTrue(result.contains("Common prefix: com.example"), "Should show common prefix in cycles output, got:\n$result")
        assertTrue(result.contains("CYCLE:"), "Should still show cycle info")
    }

    // === class name stripping tests ===

    @Test
    fun `format strips class names in cyclic dependency details when displayPrefix is set`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("service")),
            cells = mapOf(
                (PackageName("api") to PackageName("service")) to 1,
                (PackageName("service") to PackageName("api")) to 1,
            ),
            classDependencies = mapOf(
                (PackageName("api") to PackageName("service")) to setOf(ClassName("com.example.api.Controller") to ClassName("com.example.service.Service")),
                (PackageName("service") to PackageName("api")) to setOf(ClassName("com.example.service.Service") to ClassName("com.example.api.Controller")),
            ),
            displayPrefix = PackageName("com.example"),
        )

        val result = DsmFormatter.format(matrix)

        assertTrue(result.contains("api.Controller"), "Should show stripped class name, got:\n$result")
        assertTrue(result.contains("service.Service"), "Should show stripped class name, got:\n$result")
        assertTrue(!result.contains("com.example.api.Controller"), "Should not show full class name, got:\n$result")
    }

    @Test
    fun `format shows full class names when displayPrefix is empty`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("service")),
            cells = mapOf(
                (PackageName("api") to PackageName("service")) to 1,
                (PackageName("service") to PackageName("api")) to 1,
            ),
            classDependencies = mapOf(
                (PackageName("api") to PackageName("service")) to setOf(ClassName("com.example.api.Controller") to ClassName("com.example.service.Service")),
                (PackageName("service") to PackageName("api")) to setOf(ClassName("com.example.service.Service") to ClassName("com.example.api.Controller")),
            ),
            displayPrefix = PackageName(""),
        )

        val result = DsmFormatter.format(matrix)

        assertTrue(result.contains("com.example.api.Controller"), "Should show full class name when no prefix, got:\n$result")
    }

    @Test
    fun `formatCycles strips class names when displayPrefix is set`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("service")),
            cells = mapOf(
                (PackageName("api") to PackageName("service")) to 1,
                (PackageName("service") to PackageName("api")) to 1,
            ),
            classDependencies = mapOf(
                (PackageName("api") to PackageName("service")) to setOf(ClassName("com.example.api.Controller") to ClassName("com.example.service.Service")),
                (PackageName("service") to PackageName("api")) to setOf(ClassName("com.example.service.Service") to ClassName("com.example.api.Controller")),
            ),
            displayPrefix = PackageName("com.example"),
        )

        val result = DsmFormatter.formatCycles(matrix)

        assertTrue(result.contains("api.Controller"), "Should show stripped class name, got:\n$result")
        assertTrue(result.contains("service.Service"), "Should show stripped class name, got:\n$result")
        assertTrue(!result.contains("com.example.api.Controller"), "Should not show full class name, got:\n$result")
    }

    @Test
    fun `noResultsHints mentions single-package when packageCount is 1`() {
        val hints = DsmFormatter.noResultsHints(packageCount = 1)

        assertTrue(hints.any { it.contains("single package") }, "Should mention single package: $hints")
    }

    @Test
    fun `noResultsHints suggests package-filter when packageCount is greater than 1`() {
        val hints = DsmFormatter.noResultsHints(packageCount = 3)

        assertTrue(hints.none { it.contains("single package") }, "Should not mention single package for multi-package: $hints")
    }
}
