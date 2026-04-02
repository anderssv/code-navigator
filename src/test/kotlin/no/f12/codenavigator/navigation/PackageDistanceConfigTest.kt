package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.dsm.PackageDistanceConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PackageDistanceConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "package-filter" to "com.example",
            "include-external" to "true",
            "dsm-depth" to "3",
            "top" to "10",
            "prod-only" to "true",
            "test-only" to "false",
            "format" to "json",
            "llm" to "false",
        )

        val config = PackageDistanceConfig.parse(props)

        assertEquals("com.example", config.packageFilter)
        assertTrue(config.includeExternal)
        assertEquals(3, config.depth)
        assertEquals(10, config.top)
        assertTrue(config.prodOnly)
        assertFalse(config.testOnly)
        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `defaults packageFilter to null`() {
        val config = PackageDistanceConfig.parse(emptyMap())

        assertNull(config.packageFilter)
    }

    @Test
    fun `defaults includeExternal to false`() {
        val config = PackageDistanceConfig.parse(emptyMap())

        assertFalse(config.includeExternal)
    }

    @Test
    fun `defaults depth to 2`() {
        val config = PackageDistanceConfig.parse(emptyMap())

        assertEquals(2, config.depth)
    }

    @Test
    fun `defaults top to 50`() {
        val config = PackageDistanceConfig.parse(emptyMap())

        assertEquals(50, config.top)
    }

    @Test
    fun `defaults prodOnly to false`() {
        val config = PackageDistanceConfig.parse(emptyMap())

        assertFalse(config.prodOnly)
    }

    @Test
    fun `defaults testOnly to false`() {
        val config = PackageDistanceConfig.parse(emptyMap())

        assertFalse(config.testOnly)
    }

    @Test
    fun `defaults to TEXT format`() {
        val config = PackageDistanceConfig.parse(emptyMap())

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses LLM format`() {
        val config = PackageDistanceConfig.parse(mapOf("llm" to "true"))

        assertEquals(OutputFormat.LLM, config.format)
    }

    @Test
    fun `top zero throws with clear message`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            PackageDistanceConfig.parse(mapOf("top" to "0"))
        }

        assertTrue(exception.message!!.contains("top"))
        assertTrue(exception.message!!.contains("Omit"))
    }

    @Test
    fun `top unlimited parses to max int`() {
        val config = PackageDistanceConfig.parse(mapOf("top" to "unlimited"))

        assertEquals(Int.MAX_VALUE, config.top)
    }

    @Test
    fun `top negative throws with clear message`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            PackageDistanceConfig.parse(mapOf("top" to "-1"))
        }

        assertTrue(exception.message!!.contains("top"))
    }
}
