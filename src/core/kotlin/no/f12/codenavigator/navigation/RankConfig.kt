package no.f12.codenavigator.navigation

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat

data class RankConfig(
    val top: Int,
    val projectOnly: Boolean,
    val collapseLambdas: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): RankConfig = RankConfig(
            top = TaskRegistry.TOP.parse(properties["top"]),
            projectOnly = TaskRegistry.PROJECTONLY_ON.parse(properties["projectonly"]),
            collapseLambdas = TaskRegistry.COLLAPSE_LAMBDAS.parse(properties["collapse-lambdas"]),
            format = ParamDef.parseFormat(properties),
        )
    }
}
