package no.f12.codenavigator.navigation

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.config.OutputFormat

data class AnnotationQueryConfig(
    val pattern: String,
    val methods: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): AnnotationQueryConfig = AnnotationQueryConfig(
            pattern = properties["pattern"]
                ?: throw IllegalArgumentException("Missing required property 'pattern'"),
            methods = properties["methods"]?.toBoolean() ?: false,
            format = ParamDef.parseFormat(properties),
        )
    }
}
