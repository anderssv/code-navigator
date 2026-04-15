package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.classinfo.ListClassesConfig
import no.f12.codenavigator.navigation.core.Scope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListClassesConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "format" to "json",
            "llm" to "false",
        )

        val config = ListClassesConfig.parse(props)

        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `defaults to TEXT format`() {
        val config = ListClassesConfig.parse(emptyMap())

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses JSON format`() {
        val config = ListClassesConfig.parse(mapOf("format" to "json"))

        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `parses LLM format`() {
        val config = ListClassesConfig.parse(mapOf("llm" to "true"))

        assertEquals(OutputFormat.LLM, config.format)
    }

    @Test
    fun `scope defaults to ALL`() {
        val config = ListClassesConfig.parse(emptyMap())

        assertEquals(Scope.ALL, config.scope)
    }

    @Test
    fun `parses scope prod`() {
        val config = ListClassesConfig.parse(mapOf("scope" to "prod"))

        assertEquals(Scope.PROD, config.scope)
    }

    @Test
    fun `parses scope test`() {
        val config = ListClassesConfig.parse(mapOf("scope" to "test"))

        assertEquals(Scope.TEST, config.scope)
    }

    @Test
    fun `jar defaults to null`() {
        val config = ListClassesConfig.parse(emptyMap())

        assertEquals(null, config.jar)
    }

    @Test
    fun `parses jar parameter`() {
        val config = ListClassesConfig.parse(mapOf("jar" to "/path/to/lib.jar"))

        assertEquals("/path/to/lib.jar", config.jar)
    }

    @Test
    fun `pattern defaults to null`() {
        val config = ListClassesConfig.parse(emptyMap())

        assertEquals(null, config.pattern)
    }

    @Test
    fun `parses pattern parameter`() {
        val config = ListClassesConfig.parse(mapOf("pattern" to "MyClass"))

        assertEquals("MyClass", config.pattern)
    }
}
