package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.stringconstant.StringConstantConfig
import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StringConstantConfigTest {

    @Test
    fun `parses pattern as case-insensitive regex`() {
        val config = StringConstantConfig.parse(mapOf("pattern" to "api/v1"))

        assertTrue(config.pattern.containsMatchIn("/API/V1/users"))
    }

    @Test
    fun `throws when pattern is missing`() {
        assertFailsWith<IllegalArgumentException> {
            StringConstantConfig.parse(emptyMap())
        }
    }

    @Test
    fun `defaults to TEXT format`() {
        val config = StringConstantConfig.parse(mapOf("pattern" to "test"))

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses JSON format`() {
        val config = StringConstantConfig.parse(mapOf("pattern" to "test", "format" to "json"))

        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `parses LLM format via llm property`() {
        val config = StringConstantConfig.parse(mapOf("pattern" to "test", "llm" to "true"))

        assertEquals(OutputFormat.LLM, config.format)
    }

    @Test
    fun `prodOnly defaults to false`() {
        val config = StringConstantConfig.parse(mapOf("pattern" to "test"))

        assertFalse(config.prodOnly)
    }

    @Test
    fun `testOnly defaults to false`() {
        val config = StringConstantConfig.parse(mapOf("pattern" to "test"))

        assertFalse(config.testOnly)
    }

    @Test
    fun `parses prodOnly=true`() {
        val config = StringConstantConfig.parse(mapOf("pattern" to "test", "prod-only" to "true"))

        assertTrue(config.prodOnly)
    }

    @Test
    fun `parses testOnly=true`() {
        val config = StringConstantConfig.parse(mapOf("pattern" to "test", "test-only" to "true"))

        assertTrue(config.testOnly)
    }
}
