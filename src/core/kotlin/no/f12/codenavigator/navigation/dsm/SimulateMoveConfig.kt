package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.types.Scope

data class SimulateMoveConfig(
    val type: String,
    val toPackage: PackageName,
    val packageFilter: PackageName?,
    val depth: Int,
    val scope: Scope,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): SimulateMoveConfig {
            val type = TaskRegistry.TYPE.parseRequiredFrom(properties)
            val toPackage = TaskRegistry.TO_PACKAGE.parseRequiredFrom(properties)
            val explicitFilter = TaskRegistry.PACKAGE_FILTER.parseFrom(properties)

            return SimulateMoveConfig(
                type = type,
                toPackage = PackageName(toPackage),
                packageFilter = explicitFilter?.let { PackageName(it) },
                depth = TaskRegistry.DSM_DEPTH.parseFrom(properties),
                scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
