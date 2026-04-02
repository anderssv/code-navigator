package no.f12.codenavigator.navigation.interfaces

import no.f12.codenavigator.navigation.core.ClassName

import kotlin.test.Test
import kotlin.test.assertEquals

class InterfaceFormatterTest {

    @Test
    fun `formats interface with implementors`() {
        val registry = buildRegistry(
            "com.example.Repository" to listOf(
                ImplementorInfo(ClassName("com.example.UserRepo"), "UserRepo.kt"),
                ImplementorInfo(ClassName("com.example.OrderRepo"), "OrderRepo.kt"),
            ),
        )

        val output = InterfaceFormatter.format(registry, listOf(ClassName("com.example.Repository")))

        val expected = """
            |=== com.example.Repository (2 implementors) ===
            |  com.example.UserRepo (UserRepo.kt)
            |  com.example.OrderRepo (OrderRepo.kt)
        """.trimMargin()
        assertEquals(expected, output)
    }

    // [TEST] Formats interface with no implementors
    // [TEST] Formats multiple interfaces separated by blank line
    // [TEST] Shows count of implementors in header

    @Test
    fun `formats interface with no implementors`() {
        val registry = buildRegistry()

        val output = InterfaceFormatter.format(registry, listOf(ClassName("com.example.Missing")))

        val expected = "=== com.example.Missing (0 implementors) ==="
        assertEquals(expected, output)
    }

    @Test
    fun `formats multiple interfaces separated by blank line`() {
        val registry = buildRegistry(
            "com.example.Readable" to listOf(ImplementorInfo(ClassName("com.example.A"), "A.kt")),
            "com.example.Writable" to listOf(ImplementorInfo(ClassName("com.example.B"), "B.kt")),
        )

        val output = InterfaceFormatter.format(registry, listOf(ClassName("com.example.Readable"), ClassName("com.example.Writable")))

        val expected = """
            |=== com.example.Readable (1 implementors) ===
            |  com.example.A (A.kt)
            |
            |=== com.example.Writable (1 implementors) ===
            |  com.example.B (B.kt)
        """.trimMargin()
        assertEquals(expected, output)
    }

    private fun buildRegistry(vararg entries: Pair<String, List<ImplementorInfo>>): InterfaceRegistry {
        return InterfaceRegistry(entries.associate { (k, v) -> ClassName(k) to v })
    }
}
