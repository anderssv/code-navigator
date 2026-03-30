package no.f12.codenavigator.navigation.stringconstant

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat

data class StringConstantConfig(
    val pattern: Regex,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): StringConstantConfig = StringConstantConfig(
            pattern = Regex(TaskRegistry.STRING_PATTERN.parseRequiredFrom(properties), RegexOption.IGNORE_CASE),
            format = ParamDef.parseFormat(properties),
        )
    }
}
