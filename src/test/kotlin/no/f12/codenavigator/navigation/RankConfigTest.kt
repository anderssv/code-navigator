package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class RankConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "top" to "10",
            "project-only" to "false",
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

    @Test
    fun `defaults collapseLambdas to true when absent`() {
        val config = RankConfig.parse(emptyMap())

        assertEquals(true, config.collapseLambdas)
    }

    @Test
    fun `parses collapse-lambdas false`() {
        val config = RankConfig.parse(mapOf("collapse-lambdas" to "false"))

        assertEquals(false, config.collapseLambdas)
    }
}
