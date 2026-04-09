package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry

data class MoveClassConfig(
    val className: String,
    val newPackage: String,
    val newName: String?,
    val preview: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): MoveClassConfig {
            val preview: Boolean = TaskRegistry.PREVIEW.parseFrom(properties)
            val className = TaskRegistry.RENAME_CLASS.parseRequiredFrom(properties)
            val newPackage: String? = TaskRegistry.MOVE_NEW_PACKAGE.parseFrom(properties)
            val newName: String? = TaskRegistry.RENAME_NEW_NAME.parseFrom(properties)
            val effectivePackage = newPackage ?: className.substringBeforeLast(".")
            require(newPackage != null || newName != null) {
                "At least one of '${TaskRegistry.MOVE_NEW_PACKAGE.name}' or '${TaskRegistry.RENAME_NEW_NAME.name}' must be specified"
            }
            return MoveClassConfig(
                className = className,
                newPackage = effectivePackage,
                newName = newName,
                preview = preview,
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
