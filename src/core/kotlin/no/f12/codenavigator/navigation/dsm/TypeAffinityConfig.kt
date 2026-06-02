package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry

data class TypeAffinityConfig(
    val targetPackage: PackageName,
    val threshold: Int,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): TypeAffinityConfig {
            val format = ParamDef.parseFormat(properties)
            val pkg = TaskRegistry.PACKAGE.parseRequiredFrom(properties)
            val threshold = properties["threshold"]?.toIntOrNull() ?: 1

            return TypeAffinityConfig(
                targetPackage = PackageName(pkg),
                threshold = threshold,
                format = format,
            )
        }
    }
}
