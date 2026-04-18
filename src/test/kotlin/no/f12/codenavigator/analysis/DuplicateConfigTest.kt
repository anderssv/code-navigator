package no.f12.codenavigator.analysis

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.core.Scope
import kotlin.test.Test
import kotlin.test.assertEquals

class DuplicateConfigTest {

    @Test
    fun `parses default values`() {
        val config = DuplicateConfig.parse(emptyMap())

        assertEquals(50, config.minTokens)
        assertEquals(50, config.top)
        assertEquals(Scope.ALL, config.scope)
        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses custom values`() {
        val config = DuplicateConfig.parse(mapOf(
            "min-tokens" to "100",
            "top" to "20",
            "scope" to "prod",
            "format" to "json",
        ))

        assertEquals(100, config.minTokens)
        assertEquals(20, config.top)
        assertEquals(Scope.PROD, config.scope)
        assertEquals(OutputFormat.JSON, config.format)
    }
}
