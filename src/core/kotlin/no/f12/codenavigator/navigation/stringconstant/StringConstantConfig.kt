package no.f12.codenavigator.navigation.stringconstant

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.core.Scope

data class StringConstantConfig(
    val pattern: Regex,
    val scope: Scope,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): StringConstantConfig = StringConstantConfig(
            pattern = Regex(TaskRegistry.STRING_PATTERN.parseRequiredFrom(properties), RegexOption.IGNORE_CASE),
            scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
            format = ParamDef.parseFormat(properties),
        )
    }
}
