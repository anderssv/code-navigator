package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.core.PackageName

data class DsmConfig(
    val rootPackage: PackageName,
    val packageFilter: PackageName,
    val includeExternal: Boolean,
    val depth: Int,
    val htmlPath: String?,
    val format: OutputFormat,
    val cyclesOnly: Boolean,
    val cycleFilter: Pair<PackageName, PackageName>?,
    val prodOnly: Boolean,
    val testOnly: Boolean,
) {
    fun deprecations(): List<String> = buildList {
        if (rootPackage.value.isNotEmpty() && packageFilter == rootPackage) {
            add("'root-package' is deprecated. Results are now automatically limited to classes in the project source sets. Use 'package-filter' to narrow further.")
        }
    }

    companion object {
        fun parse(properties: Map<String, String?>): DsmConfig {
            val explicitFilter = TaskRegistry.PACKAGE_FILTER.parseFrom(properties)
            val legacyRoot = TaskRegistry.ROOT_PACKAGE.parseFrom(properties)
            val resolvedFilter = explicitFilter ?: legacyRoot ?: ""

            return DsmConfig(
                rootPackage = PackageName(legacyRoot ?: ""),
                packageFilter = PackageName(resolvedFilter),
                includeExternal = TaskRegistry.INCLUDE_EXTERNAL.parseFrom(properties),
                depth = TaskRegistry.DSM_DEPTH.parseFrom(properties),
                htmlPath = TaskRegistry.DSM_HTML.parseFrom(properties),
                format = ParamDef.parseFormat(properties),
                cyclesOnly = TaskRegistry.CYCLES.parseFrom(properties),
                cycleFilter = parseCycleFilter(TaskRegistry.CYCLE.parseFrom(properties)),
                prodOnly = TaskRegistry.PROD_ONLY.parseFrom(properties),
                testOnly = TaskRegistry.TEST_ONLY.parseFrom(properties),
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
