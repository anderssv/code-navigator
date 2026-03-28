package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals



class ComplexityConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "pattern" to "AdminRoutes",
            "project-only" to "false",
            "detail" to "true",
            "format" to "json",
        )

        val config = ComplexityConfig.parse(props)

        assertEquals("AdminRoutes", config.classPattern)
        assertEquals(false, config.projectOnly)
        assertEquals(true, config.detail)
        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `defaults classPattern to match-all when pattern not provided`() {
        val config = ComplexityConfig.parse(emptyMap())

        assertEquals(".*", config.classPattern)
    }

    @Test
    fun `defaults projectOnly to true when absent`() {
        val config = ComplexityConfig.parse(mapOf("pattern" to "Foo"))

        assertEquals(true, config.projectOnly)
    }

    @Test
    fun `defaults detail to false when absent`() {
        val config = ComplexityConfig.parse(mapOf("pattern" to "Foo"))

        assertEquals(false, config.detail)
    }

    @Test
    fun `defaults to TEXT format`() {
        val config = ComplexityConfig.parse(mapOf("pattern" to "Foo"))

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses LLM format`() {
        val config = ComplexityConfig.parse(mapOf("pattern" to "Foo", "llm" to "true"))

        assertEquals(OutputFormat.LLM, config.format)
    }

    @Test
    fun `defaults collapseLambdas to true when absent`() {
        val config = ComplexityConfig.parse(mapOf("pattern" to "Foo"))

        assertEquals(true, config.collapseLambdas)
    }

    @Test
    fun `parses collapse-lambdas false`() {
        val config = ComplexityConfig.parse(mapOf("pattern" to "Foo", "collapse-lambdas" to "false"))

        assertEquals(false, config.collapseLambdas)
    }

    @Test
    fun `defaults top to 50 when absent`() {
        val config = ComplexityConfig.parse(mapOf("pattern" to "Foo"))

        assertEquals(50, config.top)
    }

    @Test
    fun `parses top from properties`() {
        val config = ComplexityConfig.parse(mapOf("pattern" to "Foo", "top" to "10"))

        assertEquals(10, config.top)
    }
}
