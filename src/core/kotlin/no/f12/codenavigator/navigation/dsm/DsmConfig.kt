package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.PackageName

data class DsmConfig(
    val rootPackage: PackageName,
    val packageFilter: PackageName,
    val includeExternal: Boolean,
    val depth: Int,
    val htmlPath: String?,
    val format: OutputFormat,
    val cyclesOnly: Boolean,
    val cycleFilter: Pair<PackageName, PackageName>?,
) {
    fun deprecations(): List<String> = buildList {
        if (rootPackage.value.isNotEmpty() && packageFilter == rootPackage) {
            add("'root-package' is deprecated. Use 'package-filter' instead.")
        }
    }

    companion object {
        fun parse(properties: Map<String, String?>): DsmConfig {
            val explicitFilter = properties["package-filter"]
            val legacyRoot = properties["root-package"]
            val resolvedFilter = explicitFilter ?: legacyRoot ?: ""

            return DsmConfig(
                rootPackage = PackageName(properties["root-package"] ?: ""),
                packageFilter = PackageName(resolvedFilter),
                includeExternal = TaskRegistry.INCLUDE_EXTERNAL.parse(properties["include-external"]),
                depth = TaskRegistry.DSM_DEPTH.parse(properties["dsm-depth"]),
                htmlPath = properties["dsm-html"],
                format = ParamDef.parseFormat(properties),
                cyclesOnly = TaskRegistry.CYCLES.parse(properties["cycles"]),
                cycleFilter = parseCycleFilter(properties["cycle"]),
            )
        }

        fun parseCycleFilter(value: String?): Pair<PackageName, PackageName>? {
            if (value == null) return null
            val parts = value.split(",").map { it.trim() }
            if (parts.size != 2 || parts.any { it.isBlank() }) return null
            return PackageName(parts[0]) to PackageName(parts[1])
        }
    }
}
