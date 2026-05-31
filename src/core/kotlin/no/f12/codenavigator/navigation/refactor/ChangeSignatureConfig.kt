package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.registry.TaskRegistry

data class ChangeSignatureConfig(
    val className: String,
    val methodName: String,
    val params: String,
    val defaults: String?,
    val preview: Boolean,
    val format: OutputFormat,
) {
    fun parsedDefaults(): Map<String, String> {
        if (defaults.isNullOrBlank()) return emptyMap()
        // Format: "name1=value1,name2=value2"
        return defaults.split(",").associate { entry ->
            val eqIdx = entry.indexOf('=')
            if (eqIdx < 0) error("Invalid default entry: $entry (expected 'name=value')")
            entry.substring(0, eqIdx).trim() to entry.substring(eqIdx + 1).trim()
        }
    }

    companion object {
        fun parse(properties: Map<String, String?>): ChangeSignatureConfig {
            val className = properties[TaskRegistry.RENAME_CLASS.name]
                ?: error("Missing required parameter: ${TaskRegistry.RENAME_CLASS.name}")
            val methodName = properties[TaskRegistry.RENAME_METHOD.name]
                ?: error("Missing required parameter: ${TaskRegistry.RENAME_METHOD.name}")
            val params = properties[TaskRegistry.CHANGE_SIG_PARAMS.name]
                ?: error("Missing required parameter: ${TaskRegistry.CHANGE_SIG_PARAMS.name}")
            val defaults = properties[TaskRegistry.CHANGE_SIG_DEFAULTS.name]
            val preview = properties.containsKey(TaskRegistry.PREVIEW.name)
            val format = OutputFormat.from(properties["format"], properties.containsKey("llm").takeIf { it })
            return ChangeSignatureConfig(className, methodName, params, defaults, preview, format)
        }
    }
}
