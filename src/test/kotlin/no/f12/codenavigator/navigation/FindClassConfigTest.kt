package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FindClassConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "pattern" to "MyClass",
            "format" to "json",
            "llm" to "false",
        )

        val config = FindClassConfig.parse(props)

        assertEquals("MyClass", config.pattern)
        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `throws when pattern is missing`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            FindClassConfig.parse(emptyMap())
        }

        assertEquals(true, exception.message?.contains("pattern"))
    }

    @Test
    fun `defaults to TEXT format`() {
        val config = FindClassConfig.parse(mapOf("pattern" to "MyClass"))

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses LLM format`() {
        val config = FindClassConfig.parse(mapOf("pattern" to "MyClass", "llm" to "true"))

        assertEquals(OutputFormat.LLM, config.format)
    }
}
