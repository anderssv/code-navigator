package no.f12.codenavigator.navigation.symbol

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat

data class FindSymbolConfig(
    val pattern: String,
    val includeTest: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): FindSymbolConfig = FindSymbolConfig(
            pattern = TaskRegistry.PATTERN.parseRequiredFrom(properties),
            includeTest = TaskRegistry.INCLUDETEST.parseFrom(properties),
            format = ParamDef.parseFormat(properties),
        )
    }
}
