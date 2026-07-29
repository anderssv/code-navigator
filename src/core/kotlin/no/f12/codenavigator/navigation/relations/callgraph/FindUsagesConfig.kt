package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.types.GroupBy
import no.f12.codenavigator.navigation.bytecode.KotlinMethodFilter
import no.f12.codenavigator.navigation.types.Scope

data class FindUsagesConfig(
    val ownerClass: String?,
    val method: String?,
    val field: String?,
    val type: String?,
    val outsidePackage: String?,
    val filterSynthetic: Boolean,
    val scope: Scope,
    val groupBy: GroupBy,
    val raw: Boolean,
    val includeImpls: Boolean,
    val format: OutputFormat,
) {
    fun filterBySourceSet(usages: List<UsageSite>): List<UsageSite> {
        if (scope == Scope.ALL) return usages
        return usages.filter { usage -> usage.sourceSet == null || scope.matchesSourceSet(usage.sourceSet) }
    }

    fun filterSyntheticCallers(usages: List<UsageSite>): List<UsageSite> {
        if (!filterSynthetic) return usages
        // callerMethod is always in the caller role — a $lambda$-named caller (DSL block body)
        // is a real call site, not synthetic noise, so never treat it as generated here.
        return usages.filter { it.callerMethod == "<field>" || !KotlinMethodFilter.isGenerated(it.callerMethod, treatLambdaBodyAsGenerated = false) }
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
                raw = TaskRegistry.RAW.parseFrom(properties),
                includeImpls = TaskRegistry.INCLUDE_IMPLS.parseFrom(properties),
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
