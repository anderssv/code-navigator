package no.f12.codenavigator.navigation.ambient

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.types.Scope

data class AmbientTaskConfig(
    val domain: Regex?,
    val categories: Set<AmbientCategory>,
    val exclude: Regex?,
    val detail: Boolean,
    val scope: Scope,
    val format: OutputFormat,
    val failOnViolation: Boolean,
    val maxViolations: Int,
) {
    companion object {
        fun parse(properties: Map<String, String?>): AmbientTaskConfig {
            val domainString = TaskRegistry.DOMAIN.parseFrom(properties)
            val excludeString = TaskRegistry.EXCLUDE.parseFrom(properties)
            return AmbientTaskConfig(
                domain = domainString?.let { Regex(it) },
                categories = AmbientCategory.parse(TaskRegistry.AMBIENT_CATEGORIES.parseFrom(properties)),
                exclude = excludeString?.let { Regex(it) },
                detail = TaskRegistry.DETAIL.parseFrom(properties) ?: false,
                scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
                format = ParamDef.parseFormat(properties),
                failOnViolation = TaskRegistry.FAIL_ON_VIOLATION.parseFrom(properties),
                maxViolations = TaskRegistry.MAX_VIOLATIONS.parseFrom(properties),
            )
        }
    }
}
