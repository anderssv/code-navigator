package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.core.PackageName
import no.f12.codenavigator.navigation.core.Scope

data class CyclesConfig(
    val rootPackage: PackageName,
    val packageFilter: PackageName,
    val includeExternal: Boolean,
    val depth: Int,
    val scope: Scope,
    val format: OutputFormat,
) {
    fun deprecations(): List<String> = buildList {
        if (rootPackage.value.isNotEmpty() && packageFilter == rootPackage) {
            add("'root-package' is deprecated. Results are now automatically limited to classes in the project source sets. Use 'package-filter' to narrow further.")
        }
    }

    companion object {
        fun parse(properties: Map<String, String?>): CyclesConfig {
            val explicitFilter = TaskRegistry.PACKAGE_FILTER.parseFrom(properties)
            val legacyRoot = TaskRegistry.ROOT_PACKAGE.parseFrom(properties)
            val resolvedFilter = explicitFilter ?: legacyRoot ?: ""

            return CyclesConfig(
                rootPackage = PackageName(legacyRoot ?: ""),
                packageFilter = PackageName(resolvedFilter),
                includeExternal = TaskRegistry.INCLUDE_EXTERNAL.parseFrom(properties),
                depth = TaskRegistry.DSM_DEPTH.parseFrom(properties),
                scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
