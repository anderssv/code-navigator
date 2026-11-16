package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry

data class RenameMethodConfig(
    val className: String,
    val methodName: String,
    val newName: String,
    val preview: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>, sourceRoots: List<java.io.File> = emptyList()): RenameMethodConfig {
            val preview: Boolean = TaskRegistry.PREVIEW.parseFrom(properties)
            return RenameMethodConfig(
                className = TargetClassResolver.resolve(sourceRoots, TargetPattern.className(properties)),
                methodName = TargetPattern.requiredMethodName(properties),
                newName = TaskRegistry.RENAME_NEW_NAME.parseRequiredFrom(properties),
                preview = preview,
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
