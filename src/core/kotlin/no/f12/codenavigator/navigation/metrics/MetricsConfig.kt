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
    val excludeAnnotated: List<String>,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): MetricsConfig {
            val explicit = TaskRegistry.EXCLUDE_ANNOTATED.parse(properties["exclude-annotated"])
            val frameworks = TaskRegistry.FRAMEWORK.parse(properties["framework"])
            val frameworkAnnotations = FrameworkPresets.resolveAll(frameworks)
            val merged = (explicit + frameworkAnnotations.map { it.value }).distinct()

            return MetricsConfig(
                after = TaskRegistry.AFTER.parse(properties["after"]),
                top = TaskRegistry.METRICS_TOP.parse(properties["top"]),
                followRenames = !TaskRegistry.NO_FOLLOW.parseFrom(properties),
                rootPackage = PackageName(properties["root-package"] ?: ""),
                excludeAnnotated = merged,
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
