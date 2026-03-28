package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.hierarchy.TypeHierarchyConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TypeHierarchyConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "pattern" to "MyService",
            "project-only" to "true",
            "format" to "json",
            "llm" to "false",
        )

        val config = TypeHierarchyConfig.parse(props)

        assertEquals("MyService", config.pattern)
        assertEquals(true, config.projectOnly)
        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `throws when pattern is missing`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            TypeHierarchyConfig.parse(emptyMap())
        }

        assertEquals(true, exception.message?.contains("pattern"))
    }

    @Test
    fun `defaults projectOnly to false`() {
        val config = TypeHierarchyConfig.parse(mapOf("pattern" to "MyService"))

        assertEquals(false, config.projectOnly)
    }

    @Test
    fun `defaults to TEXT format`() {
        val config = TypeHierarchyConfig.parse(mapOf("pattern" to "MyService"))

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses LLM format`() {
        val config = TypeHierarchyConfig.parse(mapOf("pattern" to "MyService", "llm" to "true"))

        assertEquals(OutputFormat.LLM, config.format)
    }
}
