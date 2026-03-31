package no.f12.codenavigator

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class OutputWrapperTest {

    @Test
    fun `text output is returned as-is`() {
        val output = "some table output"

        val result = OutputWrapper.wrap(output, OutputFormat.TEXT)

        assertEquals("some table output", result)
    }

    @Test
    fun `JSON output is wrapped with begin and end markers`() {
        val output = "[{\"className\":\"Foo\"}]"

        val result = OutputWrapper.wrap(output, OutputFormat.JSON)

        assertEquals("---CNAV_BEGIN---\n[{\"className\":\"Foo\"}]\n---CNAV_END---", result)
    }

    @Test
    fun `LLM output is wrapped with begin and end markers`() {
        val output = "com.example.Foo Foo.kt"

        val result = OutputWrapper.wrap(output, OutputFormat.LLM)

        assertEquals("---CNAV_BEGIN---\ncom.example.Foo Foo.kt\n---CNAV_END---", result)
    }

    @Test
    fun `OutputFormat defaults to TEXT when both are null`() {
        assertEquals(OutputFormat.TEXT, OutputFormat.from(format = null, llm = null))
    }

    @Test
    fun `OutputFormat returns JSON when format is json`() {
        assertEquals(OutputFormat.JSON, OutputFormat.from(format = "json", llm = null))
    }

    @Test
    fun `OutputFormat returns LLM when llm is true`() {
        assertEquals(OutputFormat.LLM, OutputFormat.from(format = null, llm = true))
    }

    @Test
    fun `OutputFormat LLM takes precedence over JSON`() {
        assertEquals(OutputFormat.LLM, OutputFormat.from(format = "json", llm = true))
    }

    @Test
    fun `OutputFormat returns TEXT when llm is false`() {
        assertEquals(OutputFormat.TEXT, OutputFormat.from(format = null, llm = false))
    }

    @Test
    fun `OutputFormat returns LLM when format string is llm`() {
        assertEquals(OutputFormat.LLM, OutputFormat.from(format = "llm", llm = null))
    }

    @Test
    fun `emptyResult returns text message for TEXT format`() {
        val result = OutputWrapper.emptyResult(OutputFormat.TEXT, "No results found.")

        assertEquals("No results found.", result)
    }

    @Test
    fun `emptyResult returns wrapped empty JSON array for JSON format`() {
        val result = OutputWrapper.emptyResult(OutputFormat.JSON, "No results found.")

        assertEquals("---CNAV_BEGIN---\n[]\n---CNAV_END---", result)
    }

    @Test
    fun `emptyResult returns wrapped empty JSON array for LLM format`() {
        val result = OutputWrapper.emptyResult(OutputFormat.LLM, "No results found.")

        assertEquals("---CNAV_BEGIN---\n[]\n---CNAV_END---", result)
    }
}
