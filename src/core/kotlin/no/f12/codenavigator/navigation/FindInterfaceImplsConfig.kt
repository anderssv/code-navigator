package no.f12.codenavigator.navigation

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
            pattern = properties["pattern"]
                ?: throw IllegalArgumentException("Missing required property 'pattern'"),
            includeTest = TaskRegistry.INCLUDETEST.parse(properties["includetest"]),
            format = ParamDef.parseFormat(properties),
        )
    }
}
