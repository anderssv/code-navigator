package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat

data class PackageDistanceConfig(
    val packageFilter: String?,
    val includeExternal: Boolean,
    val depth: Int,
    val top: Int,
    val prodOnly: Boolean,
    val testOnly: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): PackageDistanceConfig = PackageDistanceConfig(
            packageFilter = TaskRegistry.PACKAGE_FILTER.parseFrom(properties),
            includeExternal = TaskRegistry.INCLUDE_EXTERNAL.parseFrom(properties),
            depth = TaskRegistry.DSM_DEPTH.parseFrom(properties),
            top = TaskRegistry.TOP.parseFrom(properties),
            prodOnly = TaskRegistry.PROD_ONLY.parseFrom(properties),
            testOnly = TaskRegistry.TEST_ONLY.parseFrom(properties),
            format = ParamDef.parseFormat(properties),
        )
    }
}
