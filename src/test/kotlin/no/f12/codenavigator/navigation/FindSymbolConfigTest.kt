package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.core.Scope
import no.f12.codenavigator.navigation.symbol.FindSymbolConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun `scope defaults to ALL`() {
        val config = FindSymbolConfig.parse(mapOf("pattern" to "myMethod"))

        assertEquals(Scope.ALL, config.scope)
    }

    @Test
    fun `parses scope prod`() {
        val config = FindSymbolConfig.parse(mapOf("pattern" to "myMethod", "scope" to "prod"))

        assertEquals(Scope.PROD, config.scope)
    }

    @Test
    fun `parses scope test`() {
        val config = FindSymbolConfig.parse(mapOf("pattern" to "myMethod", "scope" to "test"))

        assertEquals(Scope.TEST, config.scope)
    }

    @Test
    fun `jar defaults to null`() {
        val config = FindSymbolConfig.parse(mapOf("pattern" to "myMethod"))

        assertEquals(null, config.jar)
    }

    @Test
    fun `parses jar parameter`() {
        val config = FindSymbolConfig.parse(mapOf("pattern" to "myMethod", "jar" to "/path/to/lib.jar"))

        assertEquals("/path/to/lib.jar", config.jar)
    }
}
