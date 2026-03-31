package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.dsm.PackageDepsConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PackageDepsConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "package" to "com.example.service",
            "project-only" to "true",
            "reverse" to "true",
            "format" to "json",
            "llm" to "false",
        )

        val config = PackageDepsConfig.parse(props)

        assertEquals("com.example.service", config.packagePattern)
        assertEquals(true, config.projectOnly)
        assertEquals(true, config.reverse)
        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `defaults packagePattern to null`() {
        val config = PackageDepsConfig.parse(emptyMap())

        assertNull(config.packagePattern)
    }

    @Test
    fun `defaults projectOnly to true`() {
        val config = PackageDepsConfig.parse(emptyMap())

        assertEquals(true, config.projectOnly)
    }

    @Test
    fun `defaults reverse to false`() {
        val config = PackageDepsConfig.parse(emptyMap())

        assertEquals(false, config.reverse)
    }

    @Test
    fun `defaults to TEXT format`() {
        val config = PackageDepsConfig.parse(emptyMap())

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses LLM format`() {
        val config = PackageDepsConfig.parse(mapOf("llm" to "true"))

        assertEquals(OutputFormat.LLM, config.format)
    }

    @Test
    fun `prodOnly defaults to false`() {
        val config = PackageDepsConfig.parse(emptyMap())

        assertFalse(config.prodOnly)
    }

    @Test
    fun `testOnly defaults to false`() {
        val config = PackageDepsConfig.parse(emptyMap())

        assertFalse(config.testOnly)
    }

    @Test
    fun `parses prodOnly=true`() {
        val config = PackageDepsConfig.parse(mapOf("prod-only" to "true"))

        assertTrue(config.prodOnly)
    }

    @Test
    fun `parses testOnly=true`() {
        val config = PackageDepsConfig.parse(mapOf("test-only" to "true"))

        assertTrue(config.testOnly)
    }
}
