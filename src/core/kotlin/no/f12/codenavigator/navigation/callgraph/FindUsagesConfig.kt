package no.f12.codenavigator.navigation.callgraph

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.core.SourceSet

data class FindUsagesConfig(
    val ownerClass: String?,
    val method: String?,
    val field: String?,
    val type: String?,
    val outsidePackage: String?,
    val prodOnly: Boolean,
    val testOnly: Boolean,
    val format: OutputFormat,
) {
    fun filterBySourceSet(usages: List<UsageSite>): List<UsageSite> {
        if (!prodOnly && !testOnly) return usages
        return usages.filter { usage ->
            if (prodOnly) usage.sourceSet == SourceSet.MAIN
            else usage.sourceSet == SourceSet.TEST
        }
    }

    companion object {
        fun parse(properties: Map<String, String?>): FindUsagesConfig {
            val ownerClass = TaskRegistry.OWNER_CLASS.parseFrom(properties)
            val type = TaskRegistry.TYPE.parseFrom(properties)
            val method = TaskRegistry.METHOD.parseFrom(properties)
            val field = TaskRegistry.FIELD.parseFrom(properties)
            if (ownerClass == null && type == null) {
                throw IllegalArgumentException(
                    "Missing required property. Provide either 'owner-class' or 'type'.",
                )
            }
            if (field != null && method != null) {
                throw IllegalArgumentException(
                    "Cannot specify both 'field' and 'method'. Use 'field' for property/field usages, 'method' for method call usages.",
                )
            }
            if (field != null && ownerClass == null) {
                throw IllegalArgumentException(
                    "The 'field' parameter requires 'owner-class' to identify which class owns the field.",
                )
            }
            return FindUsagesConfig(
                ownerClass = ownerClass,
                method = method,
                field = field,
                type = type,
                outsidePackage = TaskRegistry.OUTSIDE_PACKAGE.parseFrom(properties),
                prodOnly = TaskRegistry.PROD_ONLY.parseFrom(properties),
                testOnly = TaskRegistry.TEST_ONLY.parseFrom(properties),
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
