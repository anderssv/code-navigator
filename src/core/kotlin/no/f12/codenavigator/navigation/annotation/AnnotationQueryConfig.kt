package no.f12.codenavigator.navigation.annotation

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat

data class AnnotationQueryConfig(
    val pattern: String,
    val methods: Boolean,
    val prodOnly: Boolean,
    val testOnly: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): AnnotationQueryConfig = AnnotationQueryConfig(
            pattern = TaskRegistry.PATTERN.parseRequiredFrom(properties),
            methods = TaskRegistry.METHODS.parseFrom(properties),
            prodOnly = TaskRegistry.PROD_ONLY.parseFrom(properties),
            testOnly = TaskRegistry.TEST_ONLY.parseFrom(properties),
            format = ParamDef.parseFormat(properties),
        )
    }
}
