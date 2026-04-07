package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RenameParamConfigTest {

    @Test
    fun `parses all required params from property map`() {
        val props = mapOf(
            "target-class" to "com.example.UserService",
            "method" to "findUsers",
            "param" to "limit",
            "new-name" to "maxResults",
        )

        val config = RenameParamConfig.parse(props)

        assertEquals("com.example.UserService", config.className)
        assertEquals("findUsers", config.methodName)
        assertEquals("limit", config.paramName)
        assertEquals("maxResults", config.newName)
        assertEquals(true, config.apply)
        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `preview flag sets apply to false`() {
        val props = mapOf(
            "target-class" to "com.example.UserService",
            "method" to "findUsers",
            "param" to "limit",
            "new-name" to "maxResults",
            "preview" to "true",
        )

        val config = RenameParamConfig.parse(props)

        assertEquals(false, config.apply)
    }

    @Test
    fun `preview flag with null value sets apply to false`() {
        val props: Map<String, String?> = mapOf(
            "target-class" to "com.example.UserService",
            "method" to "findUsers",
            "param" to "limit",
            "new-name" to "maxResults",
            "preview" to null,
        )

        val config = RenameParamConfig.parse(props)

        assertEquals(false, config.apply)
    }

    @Test
    fun `apply defaults to true when preview absent`() {
        val props = mapOf(
            "target-class" to "com.example.UserService",
            "method" to "findUsers",
            "param" to "limit",
            "new-name" to "maxResults",
        )

        val config = RenameParamConfig.parse(props)

        assertEquals(true, config.apply)
    }

    @Test
    fun `parses LLM format`() {
        val props = mapOf(
            "target-class" to "com.example.UserService",
            "method" to "findUsers",
            "param" to "limit",
            "new-name" to "maxResults",
            "llm" to "true",
        )

        val config = RenameParamConfig.parse(props)

        assertEquals(OutputFormat.LLM, config.format)
    }

    @Test
    fun `throws when class is missing`() {
        val props = mapOf(
            "method" to "findUsers",
            "param" to "limit",
            "new-name" to "maxResults",
        )

        assertFailsWith<IllegalArgumentException> {
            RenameParamConfig.parse(props)
        }
    }

    @Test
    fun `throws when method is missing`() {
        val props = mapOf(
            "target-class" to "com.example.UserService",
            "param" to "limit",
            "new-name" to "maxResults",
        )

        assertFailsWith<IllegalArgumentException> {
            RenameParamConfig.parse(props)
        }
    }

    @Test
    fun `throws when param is missing`() {
        val props = mapOf(
            "target-class" to "com.example.UserService",
            "method" to "findUsers",
            "new-name" to "maxResults",
        )

        assertFailsWith<IllegalArgumentException> {
            RenameParamConfig.parse(props)
        }
    }

    @Test
    fun `throws when new-name is missing`() {
        val props = mapOf(
            "target-class" to "com.example.UserService",
            "method" to "findUsers",
            "param" to "limit",
        )

        assertFailsWith<IllegalArgumentException> {
            RenameParamConfig.parse(props)
        }
    }
}
