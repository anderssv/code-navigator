package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat

data class StringConstantConfig(
    val pattern: Regex,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): StringConstantConfig {
            val patternStr = properties["pattern"]
                ?: throw IllegalArgumentException("Missing required property: pattern")
            return StringConstantConfig(
                pattern = Regex(patternStr, RegexOption.IGNORE_CASE),
                format = OutputFormat.from(
                    format = properties["format"],
                    llm = properties["llm"]?.toBoolean(),
                ),
            )
        }
    }
}
