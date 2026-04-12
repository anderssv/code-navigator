package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.classinfo.FindClassConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun `prodOnly defaults to false`() {
        val config = FindClassConfig.parse(mapOf("pattern" to "MyClass"))

        assertFalse(config.prodOnly)
    }

    @Test
    fun `testOnly defaults to false`() {
        val config = FindClassConfig.parse(mapOf("pattern" to "MyClass"))

        assertFalse(config.testOnly)
    }

    @Test
    fun `parses prodOnly=true`() {
        val config = FindClassConfig.parse(mapOf("pattern" to "MyClass", "prod-only" to "true"))

        assertTrue(config.prodOnly)
    }

    @Test
    fun `parses testOnly=true`() {
        val config = FindClassConfig.parse(mapOf("pattern" to "MyClass", "test-only" to "true"))

        assertTrue(config.testOnly)
    }

    @Test
    fun `jar defaults to null`() {
        val config = FindClassConfig.parse(mapOf("pattern" to "MyClass"))

        assertEquals(null, config.jar)
    }

    @Test
    fun `parses jar parameter`() {
        val config = FindClassConfig.parse(mapOf("pattern" to "MyClass", "jar" to "/path/to/lib.jar"))

        assertEquals("/path/to/lib.jar", config.jar)
    }
}
