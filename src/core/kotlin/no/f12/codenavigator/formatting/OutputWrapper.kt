package no.f12.codenavigator.formatting

import no.f12.codenavigator.config.OutputFormat

object OutputWrapper {
    fun wrap(output: String, format: OutputFormat): String =
        when (format) {
            OutputFormat.TEXT, OutputFormat.DIFF -> output
            OutputFormat.JSON, OutputFormat.LLM -> "---CNAV_BEGIN---\n$output\n---CNAV_END---"
        }

    fun emptyResult(format: OutputFormat, textMessage: String, hints: List<String> = emptyList()): String =
        when (format) {
            OutputFormat.TEXT, OutputFormat.DIFF -> if (hints.isEmpty()) textMessage else textMessage + "\n" + hints.joinToString("\n")
            OutputFormat.JSON, OutputFormat.LLM -> {
                // textMessage is often a dynamic, task-specific reason (e.g. SafeDelete's "Cannot
                // delete: N usage(s) found") — dropping it here (as this used to) leaves a JSON/LLM
                // consumer with no way to tell WHY there were no results. Escaping with the naive
                // quote-only replace it used to use (not escapeJson) was also silently producing
                // invalid JSON whenever textMessage/hints contained a backslash, e.g. a Windows path
                // or a regex pattern.
                val messageJson = "\"message\":\"${escapeJson(textMessage)}\""
                wrap("{\"results\":[],$messageJson,\"hints\":${jsonStringArray(hints)}}", format)
            }
        }

    fun formatAndWrap(
        format: OutputFormat,
        produce: (OutputFormat) -> String,
    ): String {
        val output = produce(format)
        return wrap(output, format)
    }

    fun wrapWithGuidance(output: String, format: OutputFormat, guidance: TaskGuidance): String =
        when (format) {
            OutputFormat.TEXT, OutputFormat.DIFF -> output
            OutputFormat.JSON -> wrap(output, format)
            OutputFormat.LLM -> {
                val rendered = guidance.render()
                val combined = if (rendered.isBlank()) output else "$rendered\n\n$output"
                wrap(combined, format)
            }
        }
}
