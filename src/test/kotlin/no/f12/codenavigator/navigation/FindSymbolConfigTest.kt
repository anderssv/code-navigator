package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.symbol.FindSymbolConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FindSymbolConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "pattern" to "myMethod",
            "format" to "json",
            "llm" to "false",
        )

        val config = FindSymbolConfig.parse(props)

        assertEquals("myMethod", config.pattern)
        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `throws when pattern is missing`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            FindSymbolConfig.parse(emptyMap())
        }

        assertEquals(true, exception.message?.contains("pattern"))
    }

    @Test
    fun `defaults to TEXT format`() {
        val config = FindSymbolConfig.parse(mapOf("pattern" to "myMethod"))

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses LLM format`() {
        val config = FindSymbolConfig.parse(mapOf("pattern" to "myMethod", "llm" to "true"))

        assertEquals(OutputFormat.LLM, config.format)
    }
}
