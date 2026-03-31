package no.f12.codenavigator.navigation.interfaces

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FindInterfaceImplsConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "pattern" to "MyInterface",
            "prod-only" to "true",
            "format" to "json",
            "llm" to "false",
        )

        val config = FindInterfaceImplsConfig.parse(props)

        assertEquals("MyInterface", config.pattern)
        assertTrue(config.prodOnly)
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
    fun `prodOnly defaults to false`() {
        val config = FindInterfaceImplsConfig.parse(mapOf("pattern" to "MyInterface"))

        assertFalse(config.prodOnly)
    }

    @Test
    fun `testOnly defaults to false`() {
        val config = FindInterfaceImplsConfig.parse(mapOf("pattern" to "MyInterface"))

        assertFalse(config.testOnly)
    }

    @Test
    fun `parses prodOnly=true`() {
        val config = FindInterfaceImplsConfig.parse(mapOf("pattern" to "MyInterface", "prod-only" to "true"))

        assertTrue(config.prodOnly)
    }

    @Test
    fun `parses testOnly=true`() {
        val config = FindInterfaceImplsConfig.parse(mapOf("pattern" to "MyInterface", "test-only" to "true"))

        assertTrue(config.testOnly)
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
