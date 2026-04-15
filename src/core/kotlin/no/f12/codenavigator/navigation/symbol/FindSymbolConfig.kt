package no.f12.codenavigator.navigation.symbol

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.core.Scope

data class FindSymbolConfig(
    val pattern: String,
    val scope: Scope,
    val format: OutputFormat,
    val jar: String?,
) {
    companion object {
        fun parse(properties: Map<String, String?>): FindSymbolConfig = FindSymbolConfig(
            pattern = TaskRegistry.PATTERN.parseRequiredFrom(properties),
            scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
            format = ParamDef.parseFormat(properties),
            jar = TaskRegistry.JAR.parseFrom(properties),
        )
    }
}
