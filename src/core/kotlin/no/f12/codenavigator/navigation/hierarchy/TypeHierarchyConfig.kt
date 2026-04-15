package no.f12.codenavigator.navigation.hierarchy

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.core.Scope

data class TypeHierarchyConfig(
    val pattern: String,
    val projectOnly: Boolean,
    val scope: Scope,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): TypeHierarchyConfig = TypeHierarchyConfig(
            pattern = TaskRegistry.PATTERN.parseRequiredFrom(properties),
            projectOnly = TaskRegistry.PROJECTONLY.parseFrom(properties),
            scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
            format = ParamDef.parseFormat(properties),
        )
    }
}
