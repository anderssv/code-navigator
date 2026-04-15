package no.f12.codenavigator.navigation.complexity

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.core.Scope

data class ComplexityConfig(
    val classPattern: String,
    val projectOnly: Boolean,
    val detail: Boolean,
    val collapseLambdas: Boolean,
    val top: Int,
    val scope: Scope,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): ComplexityConfig = ComplexityConfig(
            classPattern = properties["pattern"] ?: ".*",
            projectOnly = TaskRegistry.PROJECTONLY.parseFrom(properties),
            detail = TaskRegistry.DETAIL.parseFrom(properties),
            collapseLambdas = TaskRegistry.COLLAPSE_LAMBDAS.parseFrom(properties),
            top = TaskRegistry.TOP.parseFrom(properties),
            scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
            format = ParamDef.parseFormat(properties),
        )
    }
}
