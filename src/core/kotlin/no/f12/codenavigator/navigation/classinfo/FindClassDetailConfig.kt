package no.f12.codenavigator.navigation.classinfo

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat

data class FindClassDetailConfig(
    val pattern: String,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): FindClassDetailConfig = FindClassDetailConfig(
            pattern = TaskRegistry.PATTERN.parseRequiredFrom(properties),
            format = ParamDef.parseFormat(properties),
        )
    }
}
