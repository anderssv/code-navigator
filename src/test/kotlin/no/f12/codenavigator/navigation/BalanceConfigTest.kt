package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.core.Scope
import no.f12.codenavigator.navigation.dsm.BalanceConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BalanceConfigTest {

    @Test
    fun `parses all parameters`() {
        val config = BalanceConfig.parse(
            mapOf(
                "package-filter" to "com.example",
                "include-external" to "true",
                "dsm-depth" to "5",
                "top" to "10",
                "scope" to "prod",
                "format" to "json",
                "after" to "2024-01-01",
                "min-revs" to "3",
                "no-follow" to "true",
            ),
        )

        assertEquals("com.example", config.packageFilter)
        assertTrue(config.includeExternal)
        assertEquals(5, config.depth)
        assertEquals(10, config.top)
        assertEquals(Scope.PROD, config.scope)
        assertEquals(OutputFormat.JSON, config.format)
        assertEquals(LocalDate.of(2024, 1, 1), config.after)
        assertEquals(3, config.minRevs)
        assertEquals(false, config.followRenames)
    }

    @Test
    fun `defaults when no parameters given`() {
        val config = BalanceConfig.parse(emptyMap())

        assertNull(config.packageFilter)
        assertEquals(false, config.includeExternal)
        assertEquals(Int.MAX_VALUE, config.top)
        assertEquals(Scope.ALL, config.scope)
        assertEquals(OutputFormat.TEXT, config.format)
        assertTrue(config.followRenames)
    }
}
