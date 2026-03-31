package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat

data class PackageDepsConfig(
    val packagePattern: String?,
    val projectOnly: Boolean,
    val reverse: Boolean,
    val prodOnly: Boolean,
    val testOnly: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): PackageDepsConfig = PackageDepsConfig(
            packagePattern = TaskRegistry.PACKAGE.parseFrom(properties),
            projectOnly = TaskRegistry.PROJECTONLY.parseFrom(properties),
            reverse = TaskRegistry.REVERSE.parseFrom(properties),
            prodOnly = TaskRegistry.PROD_ONLY.parseFrom(properties),
            testOnly = TaskRegistry.TEST_ONLY.parseFrom(properties),
            format = ParamDef.parseFormat(properties),
        )
    }
}
