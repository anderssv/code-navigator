package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry

data class SuggestStructureConfig(
    val packageFilter: String?,
    val top: Int,
    val maxFanIn: Int,
    val minGroupSize: Int,
    val scope: Scope,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): SuggestStructureConfig {
            val top = parseTop(properties, defaultValue = Int.MAX_VALUE)
            return SuggestStructureConfig(
                packageFilter = TaskRegistry.PACKAGE_FILTER.parseFrom(properties),
                top = top,
                maxFanIn = TaskRegistry.MAX_FAN_IN.parseFrom(properties),
                minGroupSize = TaskRegistry.MIN_GROUP_SIZE.parseFrom(properties),
                scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
