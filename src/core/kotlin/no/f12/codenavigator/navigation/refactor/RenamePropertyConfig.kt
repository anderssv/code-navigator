package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry

data class RenamePropertyConfig(
    val className: String,
    val propertyName: String,
    val newName: String,
    val preview: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): RenamePropertyConfig {
            val preview: Boolean = TaskRegistry.PREVIEW.parseFrom(properties)
            return RenamePropertyConfig(
                className = TaskRegistry.RENAME_CLASS.parseRequiredFrom(properties),
                propertyName = TaskRegistry.RENAME_PROPERTY.parseRequiredFrom(properties),
                newName = TaskRegistry.RENAME_NEW_NAME.parseRequiredFrom(properties),
                preview = preview,
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
