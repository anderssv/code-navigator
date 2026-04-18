package no.f12.codenavigator.analysis

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.core.Scope
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry

data class DuplicateConfig(
    val minTokens: Int,
    val top: Int,
    val scope: Scope,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): DuplicateConfig = DuplicateConfig(
            minTokens = TaskRegistry.MIN_TOKENS.parseFrom(properties),
            top = TaskRegistry.TOP.parseFrom(properties),
            scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
            format = ParamDef.parseFormat(properties),
        )
    }
}
