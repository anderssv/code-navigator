package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.hierarchy.TypeHierarchyConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun `defaults projectOnly to true`() {
        val config = TypeHierarchyConfig.parse(mapOf("pattern" to "MyService"))

        assertEquals(true, config.projectOnly)
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

    @Test
    fun `prodOnly defaults to false`() {
        val config = TypeHierarchyConfig.parse(mapOf("pattern" to "MyService"))

        assertFalse(config.prodOnly)
    }

    @Test
    fun `testOnly defaults to false`() {
        val config = TypeHierarchyConfig.parse(mapOf("pattern" to "MyService"))

        assertFalse(config.testOnly)
    }

    @Test
    fun `parses prodOnly=true`() {
        val config = TypeHierarchyConfig.parse(mapOf("pattern" to "MyService", "prod-only" to "true"))

        assertTrue(config.prodOnly)
    }

    @Test
    fun `parses testOnly=true`() {
        val config = TypeHierarchyConfig.parse(mapOf("pattern" to "MyService", "test-only" to "true"))

        assertTrue(config.testOnly)
    }
}
