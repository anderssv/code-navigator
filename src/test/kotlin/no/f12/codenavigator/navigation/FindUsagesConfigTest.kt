package no.f12.codenavigator.navigation

import no.f12.codenavigator.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FindUsagesConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "ownerClass" to "com.example.MyService",
            "method" to "doStuff",
            "type" to "com.example.MyType",
            "outside-package" to "com.example.other",
            "format" to "json",
        )

        val config = FindUsagesConfig.parse(props)

        assertEquals("com.example.MyService", config.ownerClass)
        assertEquals("doStuff", config.method)
        assertEquals("com.example.MyType", config.type)
        assertEquals("com.example.other", config.outsidePackage)
        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `throws when both ownerClass and type are absent`() {
        assertFailsWith<IllegalArgumentException> {
            FindUsagesConfig.parse(emptyMap())
        }
    }

    @Test
    fun `accepts ownerClass without type`() {
        val config = FindUsagesConfig.parse(mapOf("ownerClass" to "com.example.Foo"))

        assertEquals("com.example.Foo", config.ownerClass)
        assertNull(config.type)
    }

    @Test
    fun `accepts type without ownerClass`() {
        val config = FindUsagesConfig.parse(mapOf("type" to "com.example.Bar"))

        assertNull(config.ownerClass)
        assertEquals("com.example.Bar", config.type)
    }

    @Test
    fun `defaults method to null when absent`() {
        val config = FindUsagesConfig.parse(mapOf("ownerClass" to "com.example.Foo"))

        assertNull(config.method)
    }

    @Test
    fun `defaults outsidePackage to null when absent`() {
        val config = FindUsagesConfig.parse(mapOf("ownerClass" to "com.example.Foo"))

        assertNull(config.outsidePackage)
    }

    @Test
    fun `defaults to TEXT format`() {
        val config = FindUsagesConfig.parse(mapOf("ownerClass" to "com.example.Foo"))

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses LLM format`() {
        val config = FindUsagesConfig.parse(
            mapOf(
                "ownerClass" to "com.example.Foo",
                "llm" to "true",
            ),
        )

        assertEquals(OutputFormat.LLM, config.format)
    }
}
