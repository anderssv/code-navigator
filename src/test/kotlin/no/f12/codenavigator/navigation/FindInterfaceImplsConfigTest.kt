package no.f12.codenavigator.navigation.interfaces

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.core.Scope
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
            "scope" to "prod",
            "format" to "json",
            "llm" to "false",
        )

        val config = FindInterfaceImplsConfig.parse(props)

        assertEquals("MyInterface", config.pattern)
        assertEquals(Scope.PROD, config.scope)
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
    fun `scope defaults to ALL`() {
        val config = FindInterfaceImplsConfig.parse(mapOf("pattern" to "MyInterface"))

        assertEquals(Scope.ALL, config.scope)
    }

    @Test
    fun `parses scope prod`() {
        val config = FindInterfaceImplsConfig.parse(mapOf("pattern" to "MyInterface", "scope" to "prod"))

        assertEquals(Scope.PROD, config.scope)
    }

    @Test
    fun `parses scope test`() {
        val config = FindInterfaceImplsConfig.parse(mapOf("pattern" to "MyInterface", "scope" to "test"))

        assertEquals(Scope.TEST, config.scope)
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
