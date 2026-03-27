package no.f12.codenavigator.navigation

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.config.OutputFormat

data class DeadCodeConfig(
    val filter: Regex?,
    val exclude: Regex?,
    val classesOnly: Boolean,
    val excludeAnnotated: List<String>,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): DeadCodeConfig = DeadCodeConfig(
            filter = properties["filter"]?.let { Regex(it, RegexOption.IGNORE_CASE) },
            exclude = properties["exclude"]?.let { Regex(it, RegexOption.IGNORE_CASE) },
            classesOnly = properties["classes-only"]?.toBoolean() ?: false,
            excludeAnnotated = ParamDef.parseList(properties["exclude-annotated"]),
            format = OutputFormat.from(
                format = properties["format"],
                llm = properties["llm"]?.toBoolean(),
            ),
        )
    }
}
