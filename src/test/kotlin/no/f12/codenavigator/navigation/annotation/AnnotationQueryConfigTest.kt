package no.f12.codenavigator.navigation.annotation

import no.f12.codenavigator.navigation.*

import no.f12.codenavigator.navigation.annotation.AnnotationQueryConfig
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
    fun `targets defaults to all three`() {
        val config = AnnotationQueryConfig.parse(mapOf("pattern" to "Service"))

        assertEquals(AnnotationTarget.ALL, config.targets)
    }

    @Test
    fun `parses a single target`() {
        val config = AnnotationQueryConfig.parse(mapOf("pattern" to "Transactional", "target" to "method"))

        assertEquals(setOf(AnnotationTarget.METHOD), config.targets)
    }

    @Test
    fun `parses multiple targets`() {
        val config = AnnotationQueryConfig.parse(mapOf("pattern" to "Inject", "target" to "class,field"))

        assertEquals(setOf(AnnotationTarget.CLASS, AnnotationTarget.FIELD), config.targets)
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
    fun `scope defaults to ALL`() {
        val config = AnnotationQueryConfig.parse(mapOf("pattern" to "Test"))

        assertEquals(Scope.ALL, config.scope)
    }

    @Test
    fun `parses scope prod`() {
        val config = AnnotationQueryConfig.parse(mapOf("pattern" to "Test", "scope" to "prod"))

        assertEquals(Scope.PROD, config.scope)
    }

    @Test
    fun `parses scope test`() {
        val config = AnnotationQueryConfig.parse(mapOf("pattern" to "Test", "scope" to "test"))

        assertEquals(Scope.TEST, config.scope)
    }
}
