package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.types.Scope

data class CohesionConfig(
    val packageFilter: String?,
    val top: Int,
    val minEdges: Int,
    val scope: Scope,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): CohesionConfig {
            val top = parseTop(properties, defaultValue = Int.MAX_VALUE)
            return CohesionConfig(
                packageFilter = TaskRegistry.PACKAGE_FILTER.parseFrom(properties),
                top = top,
                minEdges = TaskRegistry.MIN_EDGES.parseFrom(properties),
                scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
