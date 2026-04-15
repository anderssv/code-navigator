package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.core.Scope
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import java.time.LocalDate

data class BalanceConfig(
    val packageFilter: String?,
    val includeExternal: Boolean,
    val depth: Int,
    val top: Int,
    val scope: Scope,
    val after: LocalDate,
    val minRevs: Int,
    val followRenames: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): BalanceConfig {
            val top = parseTop(properties, defaultValue = Int.MAX_VALUE)
            return BalanceConfig(
                packageFilter = TaskRegistry.PACKAGE_FILTER.parseFrom(properties),
                includeExternal = TaskRegistry.INCLUDE_EXTERNAL.parseFrom(properties),
                depth = TaskRegistry.DSM_DEPTH.parseFrom(properties),
                top = top,
                scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
                after = TaskRegistry.AFTER.parseFrom(properties),
                minRevs = TaskRegistry.MIN_REVS.parseFrom(properties),
                followRenames = !TaskRegistry.NO_FOLLOW.parseFrom(properties),
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
