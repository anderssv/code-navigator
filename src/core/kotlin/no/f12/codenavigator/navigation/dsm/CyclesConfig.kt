package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.PackageName

data class CyclesConfig(
    val rootPackage: PackageName,
    val packageFilter: PackageName,
    val includeExternal: Boolean,
    val depth: Int,
    val format: OutputFormat,
) {
    fun deprecations(): List<String> = buildList {
        if (rootPackage.value.isNotEmpty() && packageFilter == rootPackage) {
            add("'root-package' is deprecated. Results are now automatically limited to classes in the project source sets. Use 'package-filter' to narrow further.")
        }
    }

    companion object {
        fun parse(properties: Map<String, String?>): CyclesConfig {
            val explicitFilter = properties["package-filter"]
            val legacyRoot = properties["root-package"]
            val resolvedFilter = explicitFilter ?: legacyRoot ?: ""

            return CyclesConfig(
                rootPackage = PackageName(properties["root-package"] ?: ""),
                packageFilter = PackageName(resolvedFilter),
                includeExternal = TaskRegistry.INCLUDE_EXTERNAL.parse(properties["include-external"]),
                depth = TaskRegistry.DSM_DEPTH.parse(properties["dsm-depth"]),
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
