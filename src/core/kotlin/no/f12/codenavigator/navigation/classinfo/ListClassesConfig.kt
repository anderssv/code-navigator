package no.f12.codenavigator.navigation.classinfo

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.core.Scope

data class ListClassesConfig(
    val scope: Scope,
    val format: OutputFormat,
    val jar: String?,
    val pattern: String?,
) {
    companion object {
        fun parse(properties: Map<String, String?>): ListClassesConfig = ListClassesConfig(
            scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
            format = ParamDef.parseFormat(properties),
            jar = TaskRegistry.JAR.parseFrom(properties),
            pattern = TaskRegistry.PATTERN.parseFrom(properties),
        )
    }
}
