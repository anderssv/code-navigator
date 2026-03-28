package no.f12.codenavigator.navigation

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat

data class PackageDepsConfig(
    val packagePattern: String?,
    val projectOnly: Boolean,
    val reverse: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): PackageDepsConfig = PackageDepsConfig(
            packagePattern = properties["package"],
            projectOnly = TaskRegistry.PROJECTONLY.parse(properties["project-only"]),
            reverse = TaskRegistry.REVERSE.parse(properties["reverse"]),
            format = ParamDef.parseFormat(properties),
        )
    }
}
