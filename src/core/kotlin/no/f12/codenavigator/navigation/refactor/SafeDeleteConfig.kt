package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry

data class SafeDeleteConfig(
    val className: String,
    val methodName: String?,
    val preview: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>, sourceRoots: List<java.io.File> = emptyList()): SafeDeleteConfig {
            val className = TargetClassResolver.resolve(sourceRoots, TargetPattern.className(properties))
            val methodName = TargetPattern.methodName(properties)
            val preview: Boolean = TaskRegistry.PREVIEW.parseFrom(properties)
            val format = ParamDef.parseFormat(properties)
            return SafeDeleteConfig(className, methodName, preview, format)
        }
    }
}
