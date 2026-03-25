package no.f12.codenavigator.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

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
