package no.f12.codenavigator.navigation

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat

data class CallGraphConfig(
    val method: String,
    val maxDepth: Int,
    val projectOnly: Boolean,
    val filterSynthetic: Boolean,
    val format: OutputFormat,
) {
    fun buildFilter(graph: CallGraph): ((MethodRef) -> Boolean)? {
        val filters = buildList {
            if (projectOnly) add(graph.projectClassFilter())
            if (filterSynthetic) add { ref: MethodRef -> !ref.isGenerated() }
        }
        return if (filters.isEmpty()) null else { ref -> filters.all { it(ref) } }
    }

    companion object {
        fun parse(properties: Map<String, String?>): CallGraphConfig = CallGraphConfig(
            method = properties["pattern"]
                ?: throw IllegalArgumentException("Missing required property 'pattern'"),
            maxDepth = TaskRegistry.MAXDEPTH.parse(properties["maxdepth"]),
            projectOnly = TaskRegistry.PROJECTONLY.parse(properties["project-only"]),
            filterSynthetic = TaskRegistry.FILTER_SYNTHETIC.parse(properties["filter-synthetic"]),
            format = ParamDef.parseFormat(properties),
        )
    }
}
