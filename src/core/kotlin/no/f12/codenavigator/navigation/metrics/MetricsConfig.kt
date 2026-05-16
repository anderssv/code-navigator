package no.f12.codenavigator.navigation.metrics

import no.f12.codenavigator.navigation.core.PackageName
import no.f12.codenavigator.navigation.core.Scope
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import java.time.LocalDate

data class MetricsConfig(
    val after: LocalDate,
    val top: Int,
    val followRenames: Boolean,
    val rootPackage: PackageName,
    val packageFilter: PackageName,
    val includeExternal: Boolean,
    val format: OutputFormat,
    val scope: Scope,
) {
    fun deprecations(): List<String> = buildList {
        if (rootPackage.value.isNotEmpty() && packageFilter == rootPackage) {
            add("'root-package' is deprecated. Results are now automatically limited to classes in the project source sets. Use 'package-filter' to narrow further.")
        }
    }

    companion object {
        fun parse(properties: Map<String, String?>): MetricsConfig {
            val explicitFilter = TaskRegistry.PACKAGE_FILTER.parseFrom(properties)
            val legacyRoot = TaskRegistry.ROOT_PACKAGE.parseFrom(properties)
            val resolvedFilter = explicitFilter ?: legacyRoot ?: ""

            return MetricsConfig(
                after = TaskRegistry.AFTER.parseFrom(properties),
                top = TaskRegistry.METRICS_TOP.parseFrom(properties),
                followRenames = !TaskRegistry.NO_FOLLOW.parseFrom(properties),
                rootPackage = PackageName(legacyRoot ?: ""),
                packageFilter = PackageName(resolvedFilter),
                includeExternal = TaskRegistry.INCLUDE_EXTERNAL.parseFrom(properties),
                format = ParamDef.parseFormat(properties),
                scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
            )
        }
    }
}
