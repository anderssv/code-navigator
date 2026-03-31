package no.f12.codenavigator

import no.f12.codenavigator.config.OutputFormat

object OutputWrapper {
    fun wrap(output: String, format: OutputFormat): String =
        when (format) {
            OutputFormat.TEXT -> output
            OutputFormat.JSON, OutputFormat.LLM -> "---CNAV_BEGIN---\n$output\n---CNAV_END---"
        }

    fun emptyResult(format: OutputFormat, textMessage: String): String =
        when (format) {
            OutputFormat.TEXT -> textMessage
            OutputFormat.JSON, OutputFormat.LLM -> wrap("[]", format)
        }

    fun formatAndWrap(
        format: OutputFormat,
        text: () -> String,
        json: () -> String,
        llm: () -> String,
    ): String {
        val output = when (format) {
            OutputFormat.TEXT -> text()
            OutputFormat.JSON -> json()
            OutputFormat.LLM -> llm()
        }
        return wrap(output, format)
    }
}
