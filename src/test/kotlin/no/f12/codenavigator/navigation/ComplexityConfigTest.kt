package no.f12.codenavigator.navigation

import no.f12.codenavigator.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals



class ComplexityConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "classname" to "AdminRoutes",
            "projectonly" to "false",
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
    fun `defaults classPattern to match-all when classname not provided`() {
        val config = ComplexityConfig.parse(emptyMap())

        assertEquals(".*", config.classPattern)
    }

    @Test
    fun `defaults projectOnly to true when absent`() {
        val config = ComplexityConfig.parse(mapOf("classname" to "Foo"))

        assertEquals(true, config.projectOnly)
    }

    @Test
    fun `defaults detail to false when absent`() {
        val config = ComplexityConfig.parse(mapOf("classname" to "Foo"))

        assertEquals(false, config.detail)
    }

    @Test
    fun `defaults to TEXT format`() {
        val config = ComplexityConfig.parse(mapOf("classname" to "Foo"))

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses LLM format`() {
        val config = ComplexityConfig.parse(mapOf("classname" to "Foo", "llm" to "true"))

        assertEquals(OutputFormat.LLM, config.format)
    }

    @Test
    fun `defaults collapseLambdas to true when absent`() {
        val config = ComplexityConfig.parse(mapOf("classname" to "Foo"))

        assertEquals(true, config.collapseLambdas)
    }

    @Test
    fun `parses collapse-lambdas false`() {
        val config = ComplexityConfig.parse(mapOf("classname" to "Foo", "collapse-lambdas" to "false"))

        assertEquals(false, config.collapseLambdas)
    }
}
