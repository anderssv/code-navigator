package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry

data class WhyDependsConfig(
    val fromPackage: String,
    val toPackage: String,
    val scope: Scope,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): WhyDependsConfig = WhyDependsConfig(
            fromPackage = TaskRegistry.FROM_PACKAGE.parseFrom(properties)
                ?: error("from-package is required"),
            toPackage = TaskRegistry.TO_PACKAGE.parseFrom(properties)
                ?: error("to-package is required"),
            scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
            format = ParamDef.parseFormat(properties),
        )
    }
}
