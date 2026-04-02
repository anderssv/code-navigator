package no.f12.codenavigator.navigation.deadcode

import no.f12.codenavigator.navigation.annotation.FrameworkPresets
import no.f12.codenavigator.navigation.ClassName
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat

data class DeadCodeConfig(
    val filter: Regex?,
    val exclude: Regex?,
    val classesOnly: Boolean,
    val excludeAnnotated: List<String>,
    val modifierAnnotated: List<String>,
    val supertypeEntryPoints: Set<ClassName>,
    val receiverTypeEntryPoints: Set<ClassName>,
    val prodOnly: Boolean,
    val testOnly: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): DeadCodeConfig {
            val explicit = TaskRegistry.EXCLUDE_ANNOTATED.parseFrom(properties)
            val excluded = TaskRegistry.TREAT_AS_DEAD.parseFrom(properties)
            val entryPoints = FrameworkPresets.resolveAllEntryPointsExcept(excluded)
            val modifiers = FrameworkPresets.resolveAllModifiersExcept(excluded)
            val supertypes = FrameworkPresets.resolveAllSupertypeEntryPointsExcept(excluded)
            val receiverTypes = FrameworkPresets.resolveAllReceiverTypeEntryPointsExcept(excluded)
            val mergedExclude = (explicit + entryPoints.map { it.value }).distinct()
            val mergedModifiers = modifiers.map { it.value }.distinct()

            return DeadCodeConfig(
                filter = TaskRegistry.FILTER.parseFrom(properties)?.let { Regex(it, RegexOption.IGNORE_CASE) },
                exclude = TaskRegistry.EXCLUDE.parseFrom(properties)?.let { Regex(it, RegexOption.IGNORE_CASE) },
                classesOnly = TaskRegistry.CLASSES_ONLY.parseFrom(properties),
                excludeAnnotated = mergedExclude,
                modifierAnnotated = mergedModifiers,
                supertypeEntryPoints = supertypes,
                receiverTypeEntryPoints = receiverTypes,
                prodOnly = TaskRegistry.PROD_ONLY.parseFrom(properties),
                testOnly = TaskRegistry.TEST_ONLY.parseFrom(properties),
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
