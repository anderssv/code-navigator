package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MoveClassConfigTest {

    @Test
    fun `parses all required properties`() {
        val config = MoveClassConfig.parse(mapOf(
            "target-class" to "com.example.MyService",
            "new-package" to "com.example.newpkg",
        ))

        assertEquals("com.example.MyService", config.className)
        assertEquals("com.example.newpkg", config.newPackage)
        assertEquals(false, config.preview)
        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses preview flag`() {
        val config = MoveClassConfig.parse(mapOf(
            "target-class" to "com.example.MyService",
            "new-package" to "com.example.newpkg",
            "preview" to "true",
        ))

        assertTrue(config.preview)
    }

    @Test
    fun `parses LLM format`() {
        val config = MoveClassConfig.parse(mapOf(
            "target-class" to "com.example.MyService",
            "new-package" to "com.example.newpkg",
            "llm" to "true",
        ))

        assertEquals(OutputFormat.LLM, config.format)
    }

    @Test
    fun `rejects missing target-class`() {
        assertFailsWith<IllegalArgumentException> {
            MoveClassConfig.parse(mapOf(
                "new-package" to "com.example.newpkg",
            ))
        }
    }

    @Test
    fun `rejects missing new-package`() {
        assertFailsWith<IllegalArgumentException> {
            MoveClassConfig.parse(mapOf(
                "target-class" to "com.example.MyService",
            ))
        }
    }
}
