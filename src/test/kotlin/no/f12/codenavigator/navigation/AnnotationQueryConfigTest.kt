package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.annotation.AnnotationQueryConfig
import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnnotationQueryConfigTest {

    @Test
    fun `parses pattern from properties`() {
        val config = AnnotationQueryConfig.parse(mapOf("pattern" to "GetMapping"))

        assertEquals("GetMapping", config.pattern)
    }

    @Test
    fun `throws when pattern is missing`() {
        assertFailsWith<IllegalArgumentException> {
            AnnotationQueryConfig.parse(emptyMap())
        }
    }

    @Test
    fun `methods defaults to false`() {
        val config = AnnotationQueryConfig.parse(mapOf("pattern" to "Service"))

        assertFalse(config.methods)
    }

    @Test
    fun `parses methods=true`() {
        val config = AnnotationQueryConfig.parse(mapOf("pattern" to "Transactional", "methods" to "true"))

        assertTrue(config.methods)
    }

    @Test
    fun `parses format from properties`() {
        val config = AnnotationQueryConfig.parse(mapOf("pattern" to "Service", "format" to "json"))

        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `defaults format to TEXT`() {
        val config = AnnotationQueryConfig.parse(mapOf("pattern" to "Service"))

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `includeTest defaults to false`() {
        val config = AnnotationQueryConfig.parse(mapOf("pattern" to "Test"))

        assertFalse(config.includeTest)
    }

    @Test
    fun `parses includeTest=true`() {
        val config = AnnotationQueryConfig.parse(mapOf("pattern" to "Test", "include-test" to "true"))

        assertTrue(config.includeTest)
    }
}
