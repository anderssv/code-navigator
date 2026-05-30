package no.f12.codenavigator.config

enum class OutputFormat {
    TEXT, JSON, LLM, DIFF;

    companion object {
        fun from(format: String?, llm: Boolean?): OutputFormat = when {
            format == "diff" -> DIFF
            llm == true -> LLM
            format == "llm" -> LLM
            format == "json" -> JSON
            else -> TEXT
        }
    }
}
