package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.registry.TaskRegistry

data class SafeDeleteConfig(
    val className: String,
    val methodName: String?,
    val preview: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): SafeDeleteConfig {
            val className = properties[TaskRegistry.RENAME_CLASS.name]
                ?: error("Missing required parameter: ${TaskRegistry.RENAME_CLASS.name}")
            val methodName = properties[TaskRegistry.RENAME_METHOD.name]
            val preview = properties.containsKey(TaskRegistry.PREVIEW.name)
            val format = OutputFormat.from(properties["format"], properties.containsKey("llm").takeIf { it })
            return SafeDeleteConfig(className, methodName, preview, format)
        }
    }
}
