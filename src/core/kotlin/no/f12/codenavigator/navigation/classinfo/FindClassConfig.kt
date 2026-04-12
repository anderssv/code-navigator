package no.f12.codenavigator.navigation.classinfo

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat

data class FindClassConfig(
    val pattern: String,
    val prodOnly: Boolean,
    val testOnly: Boolean,
    val format: OutputFormat,
    val jar: String?,
) {
    companion object {
        fun parse(properties: Map<String, String?>): FindClassConfig = FindClassConfig(
            pattern = TaskRegistry.PATTERN.parseRequiredFrom(properties),
            prodOnly = TaskRegistry.PROD_ONLY.parseFrom(properties),
            testOnly = TaskRegistry.TEST_ONLY.parseFrom(properties),
            format = ParamDef.parseFormat(properties),
            jar = TaskRegistry.JAR.parseFrom(properties),
        )
    }
}
