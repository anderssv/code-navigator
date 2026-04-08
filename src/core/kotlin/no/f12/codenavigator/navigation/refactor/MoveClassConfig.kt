package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry

data class MoveClassConfig(
    val className: String,
    val newPackage: String,
    val preview: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): MoveClassConfig {
            val preview: Boolean = TaskRegistry.PREVIEW.parseFrom(properties)
            return MoveClassConfig(
                className = TaskRegistry.RENAME_CLASS.parseRequiredFrom(properties),
                newPackage = TaskRegistry.MOVE_NEW_PACKAGE.parseRequiredFrom(properties),
                preview = preview,
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
