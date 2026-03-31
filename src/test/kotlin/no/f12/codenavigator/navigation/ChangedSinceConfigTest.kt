package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.changedsince.ChangedSinceConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChangedSinceConfigTest {

    @Test
    fun `parses ref from properties`() {
        val config = ChangedSinceConfig.parse(mapOf("ref" to "main"))

        assertEquals("main", config.ref)
    }

    @Test
    fun `ref defaults to null when absent`() {
        val config = ChangedSinceConfig.parse(emptyMap())

        assertNull(config.ref)
    }

    @Test
    fun `defaults project-only to true`() {
        val config = ChangedSinceConfig.parse(emptyMap())

        assertTrue(config.projectOnly)
    }

    @Test
    fun `parses project-only as false`() {
        val config = ChangedSinceConfig.parse(mapOf("project-only" to "false"))

        assertEquals(false, config.projectOnly)
    }

    @Test
    fun `defaults to TEXT format`() {
        val config = ChangedSinceConfig.parse(emptyMap())

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses LLM format`() {
        val config = ChangedSinceConfig.parse(mapOf("llm" to "true"))

        assertEquals(OutputFormat.LLM, config.format)
    }

    @Test
    fun `parses JSON format`() {
        val config = ChangedSinceConfig.parse(mapOf("format" to "json"))

        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `prodOnly defaults to false`() {
        val config = ChangedSinceConfig.parse(emptyMap())

        assertFalse(config.prodOnly)
    }

    @Test
    fun `testOnly defaults to false`() {
        val config = ChangedSinceConfig.parse(emptyMap())

        assertFalse(config.testOnly)
    }

    @Test
    fun `parses prodOnly=true`() {
        val config = ChangedSinceConfig.parse(mapOf("prod-only" to "true"))

        assertTrue(config.prodOnly)
    }

    @Test
    fun `parses testOnly=true`() {
        val config = ChangedSinceConfig.parse(mapOf("test-only" to "true"))

        assertTrue(config.testOnly)
    }
}
