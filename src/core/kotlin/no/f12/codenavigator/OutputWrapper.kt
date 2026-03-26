package no.f12.codenavigator

enum class OutputFormat {
    TEXT, JSON, LLM;

    companion object {
        fun from(format: String?, llm: Boolean?): OutputFormat = when {
            llm == true -> LLM
            format == "llm" -> LLM
            format == "json" -> JSON
            else -> TEXT
        }
    }
}

object OutputWrapper {
    fun wrap(output: String, format: OutputFormat): String =
        when (format) {
            OutputFormat.TEXT -> output
            OutputFormat.JSON, OutputFormat.LLM -> "---CNAV_BEGIN---\n$output\n---CNAV_END---"
        }
}
