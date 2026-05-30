package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry

data class MoveFileConfig(
    val fromFile: String,
    val toPackage: String,
    val preview: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): MoveFileConfig {
            val fromFile = TaskRegistry.FROM_FILE.parseRequiredFrom(properties)
            val toPackage = TaskRegistry.TO_PACKAGE.parseRequiredFrom(properties)
            val preview: Boolean = TaskRegistry.PREVIEW.parseFrom(properties)
            return MoveFileConfig(
                fromFile = fromFile,
                toPackage = toPackage,
                preview = preview,
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
