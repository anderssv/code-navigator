package no.f12.codenavigator.navigation.interfaces

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.core.Scope

data class FindInterfaceImplsConfig(
    val pattern: String,
    val scope: Scope,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): FindInterfaceImplsConfig = FindInterfaceImplsConfig(
            pattern = TaskRegistry.PATTERN.parseRequiredFrom(properties),
            scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
            format = ParamDef.parseFormat(properties),
        )
    }
}
