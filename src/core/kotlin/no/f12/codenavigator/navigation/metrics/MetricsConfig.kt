package no.f12.codenavigator.navigation.metrics

import no.f12.codenavigator.navigation.PackageName
import no.f12.codenavigator.navigation.annotation.FrameworkPresets
import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import java.time.LocalDate

data class MetricsConfig(
    val after: LocalDate,
    val top: Int,
    val followRenames: Boolean,
    val rootPackage: PackageName,
    val packageFilter: PackageName,
    val includeExternal: Boolean,
    val excludeAnnotated: List<String>,
    val format: OutputFormat,
) {
    fun deprecations(): List<String> = buildList {
        if (rootPackage.value.isNotEmpty() && packageFilter == rootPackage) {
            add("'root-package' is deprecated. Results are now automatically limited to classes in the project source sets. Use 'package-filter' to narrow further.")
        }
    }

    companion object {
        fun parse(properties: Map<String, String?>): MetricsConfig {
            val explicit = TaskRegistry.EXCLUDE_ANNOTATED.parse(properties["exclude-annotated"])
            val excluded = TaskRegistry.EXCLUDE_FRAMEWORK.parse(properties["exclude-framework"])
            val entryPoints = FrameworkPresets.resolveAllEntryPointsExcept(excluded)
            val modifiers = FrameworkPresets.resolveAllModifiersExcept(excluded)
            val merged = (explicit + entryPoints.map { it.value } + modifiers.map { it.value }).distinct()

            val explicitFilter = properties["package-filter"]
            val legacyRoot = properties["root-package"]
            val resolvedFilter = explicitFilter ?: legacyRoot ?: ""

            return MetricsConfig(
                after = TaskRegistry.AFTER.parse(properties["after"]),
                top = TaskRegistry.METRICS_TOP.parse(properties["top"]),
                followRenames = !TaskRegistry.NO_FOLLOW.parseFrom(properties),
                rootPackage = PackageName(properties["root-package"] ?: ""),
                packageFilter = PackageName(resolvedFilter),
                includeExternal = TaskRegistry.INCLUDE_EXTERNAL.parse(properties["include-external"]),
                excludeAnnotated = merged,
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
