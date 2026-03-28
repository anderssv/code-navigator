package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun `defaults projectOnly to false`() {
        val config = PackageDepsConfig.parse(emptyMap())

        assertEquals(false, config.projectOnly)
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
}
