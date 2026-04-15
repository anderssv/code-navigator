package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.changedsince.ChangedSinceConfig
import no.f12.codenavigator.navigation.core.Scope
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
    fun `scope defaults to ALL`() {
        val config = ChangedSinceConfig.parse(emptyMap())

        assertEquals(Scope.ALL, config.scope)
    }

    @Test
    fun `parses scope prod`() {
        val config = ChangedSinceConfig.parse(mapOf("scope" to "prod"))

        assertEquals(Scope.PROD, config.scope)
    }

    @Test
    fun `parses scope test`() {
        val config = ChangedSinceConfig.parse(mapOf("scope" to "test"))

        assertEquals(Scope.TEST, config.scope)
    }
}
