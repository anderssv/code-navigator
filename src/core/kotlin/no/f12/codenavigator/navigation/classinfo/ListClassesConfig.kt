package no.f12.codenavigator.navigation.classinfo

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat

data class ListClassesConfig(
    val prodOnly: Boolean,
    val testOnly: Boolean,
    val format: OutputFormat,
    val jar: String?,
    val pattern: String?,
) {
    companion object {
        fun parse(properties: Map<String, String?>): ListClassesConfig = ListClassesConfig(
            prodOnly = TaskRegistry.PROD_ONLY.parseFrom(properties),
            testOnly = TaskRegistry.TEST_ONLY.parseFrom(properties),
            format = ParamDef.parseFormat(properties),
            jar = TaskRegistry.JAR.parseFrom(properties),
            pattern = TaskRegistry.PATTERN.parseFrom(properties),
        )
    }
}
