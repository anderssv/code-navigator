package no.f12.codenavigator.navigation.interfaces

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat

data class FindInterfaceImplsConfig(
    val pattern: String,
    val includeTest: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): FindInterfaceImplsConfig = FindInterfaceImplsConfig(
            pattern = TaskRegistry.PATTERN.parseRequiredFrom(properties),
            includeTest = TaskRegistry.INCLUDETEST.parseFrom(properties),
            format = ParamDef.parseFormat(properties),
        )
    }
}
