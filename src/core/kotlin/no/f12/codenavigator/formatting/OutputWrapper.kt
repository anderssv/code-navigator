package no.f12.codenavigator.formatting

import no.f12.codenavigator.config.OutputFormat

object OutputWrapper {
    fun wrap(output: String, format: OutputFormat): String =
        when (format) {
            OutputFormat.TEXT -> output
            OutputFormat.JSON, OutputFormat.LLM -> "---CNAV_BEGIN---\n$output\n---CNAV_END---"
        }

    fun emptyResult(format: OutputFormat, textMessage: String, hints: List<String> = emptyList()): String =
        when (format) {
            OutputFormat.TEXT -> if (hints.isEmpty()) textMessage else textMessage + "\n" + hints.joinToString("\n")
            OutputFormat.JSON, OutputFormat.LLM -> {
                val hintsJson = hints.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }
                wrap("{\"results\":[],\"hints\":[$hintsJson]}", format)
            }
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

    fun wrapWithGuidance(output: String, format: OutputFormat, guidance: TaskGuidance): String =
        when (format) {
            OutputFormat.TEXT -> output
            OutputFormat.JSON -> wrap(output, format)
            OutputFormat.LLM -> {
                val rendered = guidance.render()
                val combined = if (rendered.isBlank()) output else "$rendered\n\n$output"
                wrap(combined, format)
            }
        }
}
