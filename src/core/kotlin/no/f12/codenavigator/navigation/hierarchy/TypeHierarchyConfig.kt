package no.f12.codenavigator.navigation.hierarchy

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat

data class TypeHierarchyConfig(
    val pattern: String,
    val projectOnly: Boolean,
    val prodOnly: Boolean,
    val testOnly: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): TypeHierarchyConfig = TypeHierarchyConfig(
            pattern = TaskRegistry.PATTERN.parseRequiredFrom(properties),
            projectOnly = TaskRegistry.PROJECTONLY.parseFrom(properties),
            prodOnly = TaskRegistry.PROD_ONLY.parseFrom(properties),
            testOnly = TaskRegistry.TEST_ONLY.parseFrom(properties),
            format = ParamDef.parseFormat(properties),
        )
    }
}
