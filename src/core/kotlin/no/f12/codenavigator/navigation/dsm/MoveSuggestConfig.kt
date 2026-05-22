package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry

data class MoveSuggestConfig(
    val packageFilter: String?,
    val top: Int,
    val maxFanIn: Int,
    val scope: Scope,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): MoveSuggestConfig {
            val top = parseTop(properties, defaultValue = Int.MAX_VALUE)
            return MoveSuggestConfig(
                packageFilter = TaskRegistry.PACKAGE_FILTER.parseFrom(properties),
                top = top,
                maxFanIn = TaskRegistry.MAX_FAN_IN.parseFrom(properties),
                scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
