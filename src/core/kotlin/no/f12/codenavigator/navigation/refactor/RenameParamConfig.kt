package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry

data class RenameParamConfig(
    val className: String,
    val methodName: String,
    val paramName: String,
    val newName: String,
    val preview: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>, sourceRoots: List<java.io.File> = emptyList()): RenameParamConfig {
            val preview: Boolean = TaskRegistry.PREVIEW.parseFrom(properties)
            return RenameParamConfig(
                className = TargetClassResolver.resolve(sourceRoots, TargetPattern.className(properties)),
                methodName = TargetPattern.requiredMethodName(properties),
                paramName = TaskRegistry.RENAME_PARAM.parseRequiredFrom(properties),
                newName = TaskRegistry.RENAME_NEW_NAME.parseRequiredFrom(properties),
                preview = preview,
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
