package no.f12.codenavigator.navigation

import no.f12.codenavigator.OutputFormat

data class DeadCodeConfig(
    val filter: Regex?,
    val exclude: Regex?,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): DeadCodeConfig = DeadCodeConfig(
            filter = properties["filter"]?.let { Regex(it, RegexOption.IGNORE_CASE) },
            exclude = properties["exclude"]?.let { Regex(it, RegexOption.IGNORE_CASE) },
            format = OutputFormat.from(
                format = properties["format"],
                llm = properties["llm"]?.toBoolean(),
            ),
        )
    }
}
