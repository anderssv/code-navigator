package no.f12.codenavigator.navigation.annotation

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.core.Scope

data class AnnotationQueryConfig(
    val pattern: String,
    val methods: Boolean,
    val scope: Scope,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): AnnotationQueryConfig = AnnotationQueryConfig(
            pattern = TaskRegistry.PATTERN.parseRequiredFrom(properties),
            methods = TaskRegistry.METHODS.parseFrom(properties),
            scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
            format = ParamDef.parseFormat(properties),
        )
    }
}
