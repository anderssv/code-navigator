package no.f12.codenavigator.navigation

import no.f12.codenavigator.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DsmConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "root-package" to "com.example",
            "dsm-depth" to "4",
            "dsm-html" to "/tmp/dsm.html",
            "format" to "json",
            "llm" to "false",
        )

        val config = DsmConfig.parse(props)

        assertEquals("com.example", config.rootPackage)
        assertEquals(4, config.depth)
        assertEquals("/tmp/dsm.html", config.htmlPath)
        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `defaults rootPackage to empty string`() {
        val config = DsmConfig.parse(emptyMap())

        assertEquals("", config.rootPackage)
    }

    @Test
    fun `defaults depth to 2`() {
        val config = DsmConfig.parse(emptyMap())

        assertEquals(2, config.depth)
    }

    @Test
    fun `defaults htmlPath to null`() {
        val config = DsmConfig.parse(emptyMap())

        assertNull(config.htmlPath)
    }

    @Test
    fun `defaults to TEXT format`() {
        val config = DsmConfig.parse(emptyMap())

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses LLM format`() {
        val config = DsmConfig.parse(mapOf("llm" to "true"))

        assertEquals(OutputFormat.LLM, config.format)
    }

    @Test
    fun `parses cycles true as cyclesOnly true`() {
        val config = DsmConfig.parse(mapOf("cycles" to "true"))

        assertEquals(true, config.cyclesOnly)
    }

    @Test
    fun `defaults cyclesOnly to false`() {
        val config = DsmConfig.parse(emptyMap())

        assertEquals(false, config.cyclesOnly)
    }

    @Test
    fun `parses cycles false as cyclesOnly false`() {
        val config = DsmConfig.parse(mapOf("cycles" to "false"))

        assertEquals(false, config.cyclesOnly)
    }

    // === parseCycleFilter ===

    @Test
    fun `parseCycleFilter parses comma-separated packages`() {
        val result = DsmConfig.parseCycleFilter("api,service")

        assertEquals("api" to "service", result)
    }

    @Test
    fun `parseCycleFilter trims whitespace around package names`() {
        val result = DsmConfig.parseCycleFilter(" api , service ")

        assertEquals("api" to "service", result)
    }

    @Test
    fun `parseCycleFilter returns null for null input`() {
        val result = DsmConfig.parseCycleFilter(null)

        assertNull(result)
    }

    @Test
    fun `parseCycleFilter returns null for single package`() {
        val result = DsmConfig.parseCycleFilter("api")

        assertNull(result)
    }

    @Test
    fun `parseCycleFilter returns null for empty string`() {
        val result = DsmConfig.parseCycleFilter("")

        assertNull(result)
    }

    @Test
    fun `parse includes cycleFilter from cycle property`() {
        val config = DsmConfig.parse(mapOf("cycle" to "api,service"))

        assertEquals("api" to "service", config.cycleFilter)
    }

    @Test
    fun `parse defaults cycleFilter to null`() {
        val config = DsmConfig.parse(emptyMap())

        assertNull(config.cycleFilter)
    }
}
