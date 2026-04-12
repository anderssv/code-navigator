package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.classinfo.FindClassDetailConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FindClassDetailConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "pattern" to "MyService",
            "format" to "json",
            "llm" to "false",
        )

        val config = FindClassDetailConfig.parse(props)

        assertEquals("MyService", config.pattern)
        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `throws when pattern is missing`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            FindClassDetailConfig.parse(emptyMap())
        }

        assertEquals(true, exception.message?.contains("pattern"))
    }

    @Test
    fun `defaults to TEXT format`() {
        val config = FindClassDetailConfig.parse(mapOf("pattern" to "MyService"))

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses LLM format`() {
        val config = FindClassDetailConfig.parse(mapOf("pattern" to "MyService", "llm" to "true"))

        assertEquals(OutputFormat.LLM, config.format)
    }

    @Test
    fun `prodOnly defaults to false`() {
        val config = FindClassDetailConfig.parse(mapOf("pattern" to "MyService"))

        assertFalse(config.prodOnly)
    }

    @Test
    fun `testOnly defaults to false`() {
        val config = FindClassDetailConfig.parse(mapOf("pattern" to "MyService"))

        assertFalse(config.testOnly)
    }

    @Test
    fun `parses prodOnly=true`() {
        val config = FindClassDetailConfig.parse(mapOf("pattern" to "MyService", "prod-only" to "true"))

        assertTrue(config.prodOnly)
    }

    @Test
    fun `parses testOnly=true`() {
        val config = FindClassDetailConfig.parse(mapOf("pattern" to "MyService", "test-only" to "true"))

        assertTrue(config.testOnly)
    }

    @Test
    fun `jar defaults to null`() {
        val config = FindClassDetailConfig.parse(mapOf("pattern" to "MyService"))

        assertEquals(null, config.jar)
    }

    @Test
    fun `parses jar parameter`() {
        val config = FindClassDetailConfig.parse(mapOf("pattern" to "MyService", "jar" to "/path/to/lib.jar"))

        assertEquals("/path/to/lib.jar", config.jar)
    }
}
