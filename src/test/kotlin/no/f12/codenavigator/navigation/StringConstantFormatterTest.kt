package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.stringconstant.StringConstantFormatter
import no.f12.codenavigator.navigation.stringconstant.StringConstantMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringConstantFormatterTest {

    @Test
    fun `empty list produces no-results message`() {
        val output = StringConstantFormatter.format(emptyList())

        assertEquals("No string constant matches found.", output)
    }

    @Test
    fun `formats matches as columnar table`() {
        val matches = listOf(
            StringConstantMatch(ClassName("com.example.Routes"), "getUsers", "/api/v1/users", "Routes.kt"),
            StringConstantMatch(ClassName("com.example.Config"), "setup", "application/json", "Config.kt"),
        )

        val output = StringConstantFormatter.format(matches)

        assertTrue(output.contains("Class"), "Should contain Class header")
        assertTrue(output.contains("Method"), "Should contain Method header")
        assertTrue(output.contains("Value"), "Should contain Value header")
        assertTrue(output.contains("Source"), "Should contain Source header")
        assertTrue(output.contains("com.example.Routes"))
        assertTrue(output.contains("getUsers"))
        assertTrue(output.contains("/api/v1/users"))
        assertTrue(output.contains("com.example.Config"))
        assertTrue(output.contains("setup"))
        assertTrue(output.contains("application/json"))
    }
}
