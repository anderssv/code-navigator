package no.f12.codenavigator.navigation

import no.f12.codenavigator.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class RankConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "top" to "10",
            "projectonly" to "false",
            "format" to "json",
        )

        val config = RankConfig.parse(props)

        assertEquals(10, config.top)
        assertEquals(false, config.projectOnly)
        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `defaults top to 50 when absent`() {
        val config = RankConfig.parse(emptyMap())

        assertEquals(50, config.top)
    }

    @Test
    fun `defaults projectOnly to true when absent`() {
        val config = RankConfig.parse(emptyMap())

        assertEquals(true, config.projectOnly)
    }

    @Test
    fun `defaults to TEXT format`() {
        val config = RankConfig.parse(emptyMap())

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses LLM format`() {
        val config = RankConfig.parse(mapOf("llm" to "true"))

        assertEquals(OutputFormat.LLM, config.format)
    }
}
