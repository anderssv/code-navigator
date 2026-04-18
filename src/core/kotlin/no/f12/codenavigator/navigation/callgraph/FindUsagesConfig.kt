package no.f12.codenavigator.navigation.callgraph

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.core.GroupBy
import no.f12.codenavigator.navigation.core.KotlinMethodFilter
import no.f12.codenavigator.navigation.core.Scope

data class FindUsagesConfig(
    val ownerClass: String?,
    val method: String?,
    val field: String?,
    val type: String?,
    val outsidePackage: String?,
    val filterSynthetic: Boolean,
    val scope: Scope,
    val groupBy: GroupBy,
    val format: OutputFormat,
) {
    fun filterBySourceSet(usages: List<UsageSite>): List<UsageSite> {
        if (scope == Scope.ALL) return usages
        return usages.filter { usage -> usage.sourceSet == null || scope.matchesSourceSet(usage.sourceSet) }
    }

    fun filterSyntheticCallers(usages: List<UsageSite>): List<UsageSite> {
        if (!filterSynthetic) return usages
        return usages.filter { it.callerMethod == "<field>" || !KotlinMethodFilter.isGenerated(it.callerMethod) }
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
                filterSynthetic = TaskRegistry.FILTER_SYNTHETIC.parseFrom(properties),
                scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
                groupBy = GroupBy.parse(TaskRegistry.GROUP_BY.parseFrom(properties)),
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
