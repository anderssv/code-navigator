package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry

data class ExecutePlanConfig(
    val planFile: String,
    val preview: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): ExecutePlanConfig {
            val planFile = TaskRegistry.PLAN_FILE.parseRequiredFrom(properties)
            val preview: Boolean = TaskRegistry.PREVIEW.parseFrom(properties)
            return ExecutePlanConfig(
                planFile = planFile,
                preview = preview,
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
