package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.core.Scope

data class PackageDepsConfig(
    val packagePattern: String?,
    val projectOnly: Boolean,
    val reverse: Boolean,
    val scope: Scope,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): PackageDepsConfig = PackageDepsConfig(
            packagePattern = TaskRegistry.PACKAGE.parseFrom(properties),
            projectOnly = TaskRegistry.PROJECTONLY.parseFrom(properties),
            reverse = TaskRegistry.REVERSE.parseFrom(properties),
            scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
            format = ParamDef.parseFormat(properties),
        )
    }
}
