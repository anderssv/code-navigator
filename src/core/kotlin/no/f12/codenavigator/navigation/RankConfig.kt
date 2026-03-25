package no.f12.codenavigator.navigation

import no.f12.codenavigator.OutputFormat

data class RankConfig(
    val top: Int,
    val projectOnly: Boolean,
    val collapseLambdas: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): RankConfig = RankConfig(
            top = properties["top"]?.toIntOrNull() ?: 50,
            projectOnly = properties["projectonly"]?.toBoolean() ?: true,
            collapseLambdas = properties["collapse-lambdas"]?.toBoolean() ?: true,
            format = OutputFormat.from(
                format = properties["format"],
                llm = properties["llm"]?.toBoolean(),
            ),
        )
    }
}
