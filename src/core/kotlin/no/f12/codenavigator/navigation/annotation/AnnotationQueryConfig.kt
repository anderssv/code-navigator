package no.f12.codenavigator.navigation.annotation

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
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
            methods = TaskRegistry.METHODS.parse(properties["methods"]),
            format = ParamDef.parseFormat(properties),
        )
    }
}
