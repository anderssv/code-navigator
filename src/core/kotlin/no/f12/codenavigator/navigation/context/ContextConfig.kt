package no.f12.codenavigator.navigation.context

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.types.SourceSet
import no.f12.codenavigator.navigation.relations.callgraph.CallDirection
import no.f12.codenavigator.navigation.relations.callgraph.CallGraph
import no.f12.codenavigator.navigation.relations.callgraph.MethodRef

data class ContextConfig(
    val pattern: String,
    val maxDepth: Int,
    val projectOnly: Boolean,
    val filterSynthetic: Boolean,
    val scope: Scope,
    val format: OutputFormat,
) {
    fun buildFilter(graph: CallGraph, direction: CallDirection = CallDirection.CALLERS): ((MethodRef) -> Boolean)? {
        val filters = buildList {
            if (projectOnly) add(graph.projectClassFilter())
            if (filterSynthetic) {
                val treatLambdaBodyAsGenerated = direction == CallDirection.CALLEES
                add { ref: MethodRef -> !ref.isGenerated(treatLambdaBodyAsGenerated) }
            }
            if (scope != Scope.ALL) add { ref: MethodRef -> scope.matchesSourceSet(graph.sourceSetOf(ref.className) ?: SourceSet.MAIN) }
        }
        return if (filters.isEmpty()) null else { ref -> filters.all { it(ref) } }
    }

    companion object {
        fun parse(properties: Map<String, String?>): ContextConfig = ContextConfig(
            pattern = TaskRegistry.PATTERN.parseRequiredFrom(properties),
            maxDepth = TaskRegistry.CONTEXT_MAXDEPTH.parseFrom(properties),
            projectOnly = TaskRegistry.PROJECTONLY.parseFrom(properties),
            filterSynthetic = TaskRegistry.FILTER_SYNTHETIC.parseFrom(properties),
            scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
            format = ParamDef.parseFormat(properties),
        )
    }
}
