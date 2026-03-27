package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FindInterfaceImplsConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "pattern" to "MyInterface",
            "includetest" to "true",
            "format" to "json",
            "llm" to "false",
        )

        val config = FindInterfaceImplsConfig.parse(props)

        assertEquals("MyInterface", config.pattern)
        assertEquals(true, config.includeTest)
        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `throws when pattern is missing`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            FindInterfaceImplsConfig.parse(emptyMap())
        }

        assertEquals(true, exception.message?.contains("pattern"))
    }

    @Test
    fun `defaults includeTest to false`() {
        val config = FindInterfaceImplsConfig.parse(mapOf("pattern" to "MyInterface"))

        assertEquals(false, config.includeTest)
    }

    @Test
    fun `defaults to TEXT format`() {
        val config = FindInterfaceImplsConfig.parse(mapOf("pattern" to "MyInterface"))

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses LLM format`() {
        val config = FindInterfaceImplsConfig.parse(mapOf("pattern" to "MyInterface", "llm" to "true"))

        assertEquals(OutputFormat.LLM, config.format)
    }
}
