package no.f12.codenavigator.navigation

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import java.time.LocalDate

data class MetricsConfig(
    val after: LocalDate,
    val top: Int,
    val followRenames: Boolean,
    val rootPackage: PackageName,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): MetricsConfig = MetricsConfig(
            after = TaskRegistry.AFTER.parse(properties["after"]),
            top = TaskRegistry.METRICS_TOP.parse(properties["top"]),
            followRenames = !TaskRegistry.NO_FOLLOW.parseFrom(properties),
            rootPackage = PackageName(properties["root-package"] ?: ""),
            format = ParamDef.parseFormat(properties),
        )
    }
}
