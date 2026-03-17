package no.f12.codenavigator

import kotlin.test.Test
import kotlin.test.assertEquals

class PackageDependencyFormatterTest {

    @Test
    fun `formats package with dependencies`() {
        val deps = PackageDependencies(
            mapOf(
                "com.example.services" to listOf("com.example.domain", "com.example.ra"),
            ),
        )

        val output = PackageDependencyFormatter.format(deps, listOf("com.example.services"))

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

        val output = PackageDependencyFormatter.format(deps, listOf("com.example.domain"))

        assertEquals("com.example.domain\n  (no outgoing dependencies)", output)
    }

    @Test
    fun `formats multiple packages separated by blank line`() {
        val deps = PackageDependencies(
            mapOf(
                "com.example.a" to listOf("com.example.b"),
                "com.example.b" to listOf("com.example.c"),
            ),
        )

        val output = PackageDependencyFormatter.format(deps, listOf("com.example.a", "com.example.b"))

        val expected = """
            |com.example.a
            |  → com.example.b
            |
            |com.example.b
            |  → com.example.c
        """.trimMargin()
        assertEquals(expected, output)
    }
}
