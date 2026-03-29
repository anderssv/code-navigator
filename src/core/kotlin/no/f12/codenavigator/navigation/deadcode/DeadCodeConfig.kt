package no.f12.codenavigator.navigation.deadcode

import no.f12.codenavigator.navigation.annotation.FrameworkPresets
import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat

data class DeadCodeConfig(
    val filter: Regex?,
    val exclude: Regex?,
    val classesOnly: Boolean,
    val excludeAnnotated: List<String>,
    val modifierAnnotated: List<String>,
    val prodOnly: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): DeadCodeConfig {
            val explicit = TaskRegistry.EXCLUDE_ANNOTATED.parse(properties["exclude-annotated"])
            val excluded = TaskRegistry.EXCLUDE_FRAMEWORK.parse(properties["exclude-framework"])
            val entryPoints = FrameworkPresets.resolveAllEntryPointsExcept(excluded)
            val modifiers = FrameworkPresets.resolveAllModifiersExcept(excluded)
            val mergedExclude = (explicit + entryPoints.map { it.value }).distinct()
            val mergedModifiers = modifiers.map { it.value }.distinct()

            return DeadCodeConfig(
                filter = properties["filter"]?.let { Regex(it, RegexOption.IGNORE_CASE) },
                exclude = properties["exclude"]?.let { Regex(it, RegexOption.IGNORE_CASE) },
                classesOnly = TaskRegistry.CLASSES_ONLY.parse(properties["classes-only"]),
                excludeAnnotated = mergedExclude,
                modifierAnnotated = mergedModifiers,
                prodOnly = TaskRegistry.PROD_ONLY.parse(properties["prod-only"]),
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
