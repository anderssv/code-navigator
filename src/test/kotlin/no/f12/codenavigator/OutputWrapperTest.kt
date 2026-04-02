package no.f12.codenavigator

import no.f12.codenavigator.formatting.OutputWrapper
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
    fun `emptyResult returns wrapped JSON object with empty hints for JSON format`() {
        val result = OutputWrapper.emptyResult(OutputFormat.JSON, "No results found.")

        assertEquals("---CNAV_BEGIN---\n{\"results\":[],\"hints\":[]}\n---CNAV_END---", result)
    }

    @Test
    fun `emptyResult returns wrapped JSON object with empty hints for LLM format`() {
        val result = OutputWrapper.emptyResult(OutputFormat.LLM, "No results found.")

        assertEquals("---CNAV_BEGIN---\n{\"results\":[],\"hints\":[]}\n---CNAV_END---", result)
    }

    @Test
    fun `emptyResult with hints returns JSON object with results and hints for JSON format`() {
        val result = OutputWrapper.emptyResult(
            OutputFormat.JSON,
            "No annotations found.",
            hints = listOf("Use -Pmethods=true to search method-level annotations."),
        )

        assertEquals(
            "---CNAV_BEGIN---\n{\"results\":[],\"hints\":[\"Use -Pmethods=true to search method-level annotations.\"]}\n---CNAV_END---",
            result,
        )
    }

    @Test
    fun `emptyResult with hints returns JSON object with results and hints for LLM format`() {
        val result = OutputWrapper.emptyResult(
            OutputFormat.LLM,
            "No annotations found.",
            hints = listOf("Hint one.", "Hint two."),
        )

        assertEquals(
            "---CNAV_BEGIN---\n{\"results\":[],\"hints\":[\"Hint one.\",\"Hint two.\"]}\n---CNAV_END---",
            result,
        )
    }

    @Test
    fun `emptyResult with hints appends hints to text message for TEXT format`() {
        val result = OutputWrapper.emptyResult(
            OutputFormat.TEXT,
            "No annotations found.",
            hints = listOf("Use -Pmethods=true to search method-level annotations."),
        )

        assertEquals(
            "No annotations found.\nUse -Pmethods=true to search method-level annotations.",
            result,
        )
    }

    @Test
    fun `emptyResult with multiple hints joins them with newlines for TEXT format`() {
        val result = OutputWrapper.emptyResult(
            OutputFormat.TEXT,
            "No results.",
            hints = listOf("Hint one.", "Hint two."),
        )

        assertEquals("No results.\nHint one.\nHint two.", result)
    }
}
