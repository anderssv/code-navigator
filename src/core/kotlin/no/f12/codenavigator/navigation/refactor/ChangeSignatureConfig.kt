package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.registry.ParamDef
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
            val className = TaskRegistry.RENAME_CLASS.parseRequiredFrom(properties)
            val methodName = TaskRegistry.RENAME_METHOD.parseRequiredFrom(properties)
            val params = TaskRegistry.CHANGE_SIG_PARAMS.parseRequiredFrom(properties)
            val defaults = TaskRegistry.CHANGE_SIG_DEFAULTS.parseFrom(properties)
            val preview: Boolean = TaskRegistry.PREVIEW.parseFrom(properties)
            val format = ParamDef.parseFormat(properties)
            return ChangeSignatureConfig(className, methodName, params, defaults, preview, format)
        }
    }
}
