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
        fun parse(properties: Map<String, String?>): SafeDeleteConfig {
            val className = TaskRegistry.RENAME_CLASS.parseRequiredFrom(properties)
            val methodName = TaskRegistry.RENAME_METHOD.parseFrom(properties)
            val preview: Boolean = TaskRegistry.PREVIEW.parseFrom(properties)
            val format = ParamDef.parseFormat(properties)
            return SafeDeleteConfig(className, methodName, preview, format)
        }
    }
}
