package no.f12.codenavigator.navigation.ambient

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.types.Scope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AmbientTaskConfigTest {

    @Test
    fun `defaults to all categories and ring-derived subjects`() {
        val config = AmbientTaskConfig.parse(emptyMap())

        assertNull(config.domain)
        assertEquals(AmbientCategory.entries.toSet(), config.categories)
        assertEquals(Scope.ALL, config.scope)
        assertEquals(OutputFormat.TEXT, config.format)
        assertEquals(false, config.detail)
        assertEquals(false, config.failOnViolation)
    }

    @Test
    fun `parses categories domain and exclude`() {
        val config = AmbientTaskConfig.parse(
            mapOf("categories" to "clock,random", "domain" to "\\.domain\\.", "exclude" to "Generated"),
        )

        assertEquals(setOf(AmbientCategory.CLOCK, AmbientCategory.RANDOM), config.categories)
        assertTrue(config.domain!!.containsMatchIn("com.example.domain.Poll"))
        assertTrue(config.exclude!!.containsMatchIn("GeneratedThing"))
    }

    @Test
    fun `rejects an unknown category`() {
        val failure = assertFailsWith<IllegalStateException> {
            AmbientTaskConfig.parse(mapOf("categories" to "clock,weather"))
        }

        assertTrue(failure.message!!.contains("weather"))
    }

    @Test
    fun `parses the CI gate parameters`() {
        val config = AmbientTaskConfig.parse(mapOf("fail-on-violation" to "true", "max-violations" to "3"))

        assertEquals(true, config.failOnViolation)
        assertEquals(3, config.maxViolations)
    }
}
