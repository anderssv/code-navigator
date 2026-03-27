package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat

data class FindInterfaceImplsConfig(
    val pattern: String,
    val includeTest: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): FindInterfaceImplsConfig = FindInterfaceImplsConfig(
            pattern = properties["pattern"]
                ?: throw IllegalArgumentException("Missing required property 'pattern'"),
            includeTest = properties["includetest"]?.toBoolean() ?: false,
            format = OutputFormat.from(
                format = properties["format"],
                llm = properties["llm"]?.toBoolean(),
            ),
        )
    }
}
