package no.f12.codenavigator.analysis

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class FileSizeConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "over" to "100",
            "top" to "20",
            "format" to "json",
        )

        val config = FileSizeConfig.parse(props)

        assertEquals(100, config.over)
        assertEquals(20, config.top)
        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `defaults over to 0 when absent`() {
        val config = FileSizeConfig.parse(emptyMap())

        assertEquals(0, config.over)
    }

    @Test
    fun `defaults top to 50 when absent`() {
        val config = FileSizeConfig.parse(emptyMap())

        assertEquals(50, config.top)
    }

    @Test
    fun `defaults to TEXT format`() {
        val config = FileSizeConfig.parse(emptyMap())

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses LLM format`() {
        val config = FileSizeConfig.parse(mapOf("llm" to "true"))

        assertEquals(OutputFormat.LLM, config.format)
    }
}
