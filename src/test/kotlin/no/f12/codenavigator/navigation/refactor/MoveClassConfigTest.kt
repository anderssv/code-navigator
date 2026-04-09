package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MoveClassConfigTest {

    @Test
    fun `parses from and to properties`() {
        val config = MoveClassConfig.parse(mapOf(
            "from" to "com.example.MyService",
            "to" to "com.example.newpkg.MyService",
        ))

        assertEquals("com.example.MyService", config.from)
        assertEquals("com.example.newpkg.MyService", config.to)
        assertEquals(false, config.preview)
        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `derives package and simple name from FQN`() {
        val config = MoveClassConfig.parse(mapOf(
            "from" to "com.example.old.MyService",
            "to" to "com.example.new.RenamedService",
        ))

        assertEquals("com.example.old", config.fromPackage)
        assertEquals("MyService", config.fromSimpleName)
        assertEquals("com.example.new", config.toPackage)
        assertEquals("RenamedService", config.toSimpleName)
    }

    @Test
    fun `parses preview flag`() {
        val config = MoveClassConfig.parse(mapOf(
            "from" to "com.example.MyService",
            "to" to "com.example.newpkg.MyService",
            "preview" to "true",
        ))

        assertTrue(config.preview)
    }

    @Test
    fun `parses LLM format`() {
        val config = MoveClassConfig.parse(mapOf(
            "from" to "com.example.MyService",
            "to" to "com.example.newpkg.MyService",
            "llm" to "true",
        ))

        assertEquals(OutputFormat.LLM, config.format)
    }

    @Test
    fun `rejects missing from`() {
        assertFailsWith<IllegalArgumentException> {
            MoveClassConfig.parse(mapOf(
                "to" to "com.example.newpkg.MyService",
            ))
        }
    }

    @Test
    fun `rejects missing to`() {
        assertFailsWith<IllegalArgumentException> {
            MoveClassConfig.parse(mapOf(
                "from" to "com.example.MyService",
            ))
        }
    }
}
