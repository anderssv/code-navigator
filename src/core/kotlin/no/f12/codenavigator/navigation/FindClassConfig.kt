package no.f12.codenavigator.navigation

import no.f12.codenavigator.OutputFormat

data class FindClassConfig(
    val pattern: String,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): FindClassConfig = FindClassConfig(
            pattern = PatternEnhancer.enhance(
                properties["pattern"]
                    ?: throw IllegalArgumentException("Missing required property 'pattern'"),
            ),
            format = OutputFormat.from(
                format = properties["format"],
                llm = properties["llm"]?.toBoolean(),
            ),
        )
    }
}
