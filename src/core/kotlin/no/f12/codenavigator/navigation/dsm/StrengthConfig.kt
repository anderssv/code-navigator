package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat

data class StrengthConfig(
    val packageFilter: String?,
    val includeExternal: Boolean,
    val depth: Int,
    val top: Int,
    val prodOnly: Boolean,
    val testOnly: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): StrengthConfig {
            val top = parseTop(properties, defaultValue = Int.MAX_VALUE)
            return StrengthConfig(
                packageFilter = TaskRegistry.PACKAGE_FILTER.parseFrom(properties),
                includeExternal = TaskRegistry.INCLUDE_EXTERNAL.parseFrom(properties),
                depth = TaskRegistry.DSM_DEPTH.parseFrom(properties),
                top = top,
                prodOnly = TaskRegistry.PROD_ONLY.parseFrom(properties),
                testOnly = TaskRegistry.TEST_ONLY.parseFrom(properties),
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
