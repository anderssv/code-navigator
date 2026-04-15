package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.core.Scope

data class PackageDistanceConfig(
    val packageFilter: String?,
    val includeExternal: Boolean,
    val depth: Int,
    val top: Int,
    val scope: Scope,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): PackageDistanceConfig {
            val top = parseTop(properties, defaultValue = Int.MAX_VALUE)
            return PackageDistanceConfig(
                packageFilter = TaskRegistry.PACKAGE_FILTER.parseFrom(properties),
                includeExternal = TaskRegistry.INCLUDE_EXTERNAL.parseFrom(properties),
                depth = TaskRegistry.DSM_DEPTH.parseFrom(properties),
                top = top,
                scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
