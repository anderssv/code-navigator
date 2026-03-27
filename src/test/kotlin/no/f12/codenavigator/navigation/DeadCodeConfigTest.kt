package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeadCodeConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "filter" to "Service",
            "exclude" to "Test",
            "format" to "json",
        )

        val config = DeadCodeConfig.parse(props)

        assertTrue(config.filter!!.containsMatchIn("MyService"))
        assertTrue(config.exclude!!.containsMatchIn("MyTest"))
        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `defaults filter to null when absent`() {
        val config = DeadCodeConfig.parse(emptyMap())

        assertNull(config.filter)
    }

    @Test
    fun `defaults exclude to null when absent`() {
        val config = DeadCodeConfig.parse(emptyMap())

        assertNull(config.exclude)
    }

    @Test
    fun `defaults to TEXT format`() {
        val config = DeadCodeConfig.parse(emptyMap())

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses LLM format`() {
        val config = DeadCodeConfig.parse(mapOf("llm" to "true"))

        assertEquals(OutputFormat.LLM, config.format)
    }

    @Test
    fun `filter regex is case-insensitive`() {
        val config = DeadCodeConfig.parse(mapOf("filter" to "service"))

        assertTrue(config.filter!!.containsMatchIn("MyService"))
    }

    @Test
    fun `exclude regex is case-insensitive`() {
        val config = DeadCodeConfig.parse(mapOf("exclude" to "test"))

        assertTrue(config.exclude!!.containsMatchIn("MyTest"))
    }

    @Test
    fun `defaults classesOnly to false when absent`() {
        val config = DeadCodeConfig.parse(emptyMap())

        assertFalse(config.classesOnly)
    }

    @Test
    fun `parses classesOnly as true`() {
        val config = DeadCodeConfig.parse(mapOf("classes-only" to "true"))

        assertTrue(config.classesOnly)
    }

    @Test
    fun `parses classesOnly as false`() {
        val config = DeadCodeConfig.parse(mapOf("classes-only" to "false"))

        assertFalse(config.classesOnly)
    }

    @Test
    fun `parses exclude-annotated from comma-separated string`() {
        val config = DeadCodeConfig.parse(mapOf("exclude-annotated" to "RestController,Scheduled,Component"))

        assertEquals(listOf("RestController", "Scheduled", "Component"), config.excludeAnnotated)
    }

    @Test
    fun `defaults exclude-annotated to empty list when absent`() {
        val config = DeadCodeConfig.parse(emptyMap())

        assertTrue(config.excludeAnnotated.isEmpty())
    }

    @Test
    fun `trims whitespace from exclude-annotated values`() {
        val config = DeadCodeConfig.parse(mapOf("exclude-annotated" to " RestController , Scheduled "))

        assertEquals(listOf("RestController", "Scheduled"), config.excludeAnnotated)
    }
}
