package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.dsm.PackageDependencies
import no.f12.codenavigator.navigation.dsm.PackageDependencyFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackageDependencyFormatterTest {

    @Test
    fun `formats package with dependencies`() {
        val deps = PackageDependencies(
            mapOf(
                PackageName("com.example.services") to listOf(PackageName("com.example.domain"), PackageName("com.example.ra")),
            ),
        )

        val output = PackageDependencyFormatter.format(deps, listOf(PackageName("com.example.services")))

        val expected = """
            |com.example.services
            |  → com.example.domain
            |  → com.example.ra
        """.trimMargin()
        assertEquals(expected, output)
    }

    @Test
    fun `formats package with no dependencies`() {
        val deps = PackageDependencies(emptyMap())

        val output = PackageDependencyFormatter.format(deps, listOf(PackageName("com.example.domain")))

        assertEquals("com.example.domain\n  (no outgoing dependencies)", output)
    }

    @Test
    fun `formats multiple packages separated by blank line`() {
        val deps = PackageDependencies(
            mapOf(
                PackageName("com.example.a") to listOf(PackageName("com.example.b")),
                PackageName("com.example.b") to listOf(PackageName("com.example.c")),
            ),
        )

        val output = PackageDependencyFormatter.format(deps, listOf(PackageName("com.example.a"), PackageName("com.example.b")))

        val expected = """
            |com.example.a
            |  → com.example.b
            |
            |com.example.b
            |  → com.example.c
        """.trimMargin()
        assertEquals(expected, output)
    }

    @Test
    fun `reverse format shows who depends on package`() {
        val deps = PackageDependencies(
            mapOf(
                PackageName("com.example.services") to listOf(PackageName("com.example.domain")),
                PackageName("com.example.ktor") to listOf(PackageName("com.example.domain")),
            ),
        )

        val output = PackageDependencyFormatter.format(
            deps,
            listOf(PackageName("com.example.domain")),
            reverse = true,
        )

        val expected = """
            |com.example.domain
            |  ← com.example.ktor
            |  ← com.example.services
        """.trimMargin()
        assertEquals(expected, output)
    }

    // === noResultsHints tests ===

    @Test
    fun `noResultsHints mentions single-package when packageCount is 1`() {
        val hints = PackageDependencyFormatter.noResultsHints(packageCount = 1)

        assertTrue(hints.any { it.contains("single package") }, "Should mention single package: $hints")
    }

    @Test
    fun `noResultsHints returns empty for multi-package projects`() {
        val hints = PackageDependencyFormatter.noResultsHints(packageCount = 3)

        assertTrue(hints.none { it.contains("single package") }, "Should not mention single package for multi-package: $hints")
    }

    @Test
    fun `reverse format shows no incoming dependencies message`() {
        val deps = PackageDependencies(
            mapOf(
                PackageName("com.example.services") to listOf(PackageName("com.example.domain")),
            ),
        )

        val output = PackageDependencyFormatter.format(
            deps,
            listOf(PackageName("com.example.services")),
            reverse = true,
        )

        assertEquals("com.example.services\n  (no incoming dependencies)", output)
    }
}
