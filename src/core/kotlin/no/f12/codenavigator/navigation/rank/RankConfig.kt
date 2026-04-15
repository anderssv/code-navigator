package no.f12.codenavigator.navigation.rank

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.core.Scope

data class RankConfig(
    val top: Int,
    val projectOnly: Boolean,
    val collapseLambdas: Boolean,
    val scope: Scope,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): RankConfig = RankConfig(
            top = TaskRegistry.TOP.parseFrom(properties),
            projectOnly = TaskRegistry.PROJECTONLY.parseFrom(properties),
            collapseLambdas = TaskRegistry.COLLAPSE_LAMBDAS.parseFrom(properties),
            scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
            format = ParamDef.parseFormat(properties),
        )
    }
}
