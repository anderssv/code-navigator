package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RenameMethodConfigTest {

    // [TEST] Parses all required params from property map
    // [TEST] Preview flag sets preview to true
    // [TEST] Preview flag with null value sets preview to true
    // [TEST] Preview defaults to false when absent
    // [TEST] Parses LLM format
    // [TEST] Throws when class is missing
    // [TEST] Throws when method is missing
    // [TEST] Throws when new-name is missing

    @Test
    fun `parses all required params from property map`() {
        val props = mapOf(
            "target-class" to "com.example.UserService",
            "method" to "findUsers",
            "new-name" to "lookupUsers",
        )

        val config = RenameMethodConfig.parse(props)

        assertEquals("com.example.UserService", config.className)
        assertEquals("findUsers", config.methodName)
        assertEquals("lookupUsers", config.newName)
        assertEquals(false, config.preview)
        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `preview flag sets preview to true`() {
        val props = mapOf(
            "target-class" to "com.example.UserService",
            "method" to "findUsers",
            "new-name" to "lookupUsers",
            "preview" to "true",
        )

        val config = RenameMethodConfig.parse(props)

        assertEquals(true, config.preview)
    }

    @Test
    fun `preview flag with null value sets preview to true`() {
        val props: Map<String, String?> = mapOf(
            "target-class" to "com.example.UserService",
            "method" to "findUsers",
            "new-name" to "lookupUsers",
            "preview" to null,
        )

        val config = RenameMethodConfig.parse(props)

        assertEquals(true, config.preview)
    }

    @Test
    fun `preview defaults to false when absent`() {
        val props = mapOf(
            "target-class" to "com.example.UserService",
            "method" to "findUsers",
            "new-name" to "lookupUsers",
        )

        val config = RenameMethodConfig.parse(props)

        assertEquals(false, config.preview)
    }

    @Test
    fun `parses LLM format`() {
        val props = mapOf(
            "target-class" to "com.example.UserService",
            "method" to "findUsers",
            "new-name" to "lookupUsers",
            "llm" to "true",
        )

        val config = RenameMethodConfig.parse(props)

        assertEquals(OutputFormat.LLM, config.format)
    }

    @Test
    fun `throws when class is missing`() {
        val props = mapOf(
            "method" to "findUsers",
            "new-name" to "lookupUsers",
        )

        assertFailsWith<IllegalArgumentException> {
            RenameMethodConfig.parse(props)
        }
    }

    @Test
    fun `throws when method is missing`() {
        val props = mapOf(
            "target-class" to "com.example.UserService",
            "new-name" to "lookupUsers",
        )

        assertFailsWith<IllegalArgumentException> {
            RenameMethodConfig.parse(props)
        }
    }

    @Test
    fun `throws when new-name is missing`() {
        val props = mapOf(
            "target-class" to "com.example.UserService",
            "method" to "findUsers",
        )

        assertFailsWith<IllegalArgumentException> {
            RenameMethodConfig.parse(props)
        }
    }
}
