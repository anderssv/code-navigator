package no.f12.codenavigator.navigation.hierarchy

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat

data class TypeHierarchyConfig(
    val pattern: String,
    val projectOnly: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): TypeHierarchyConfig = TypeHierarchyConfig(
            pattern = properties["pattern"]
                ?: throw IllegalArgumentException("Missing required property 'pattern'"),
            projectOnly = TaskRegistry.PROJECTONLY.parseFrom(properties),
            format = ParamDef.parseFormat(properties),
        )
    }
}
