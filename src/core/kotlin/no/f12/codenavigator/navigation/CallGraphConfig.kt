package no.f12.codenavigator.navigation

import no.f12.codenavigator.OutputFormat

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
            method = properties["method"]
                ?: throw IllegalArgumentException("Missing required property 'method'"),
            maxDepth = properties["maxdepth"]?.toIntOrNull() ?: 3,
            projectOnly = properties["projectonly"]?.toBoolean() ?: false,
            filterSynthetic = properties["filter-synthetic"]?.toBoolean() ?: true,
            format = OutputFormat.from(
                format = properties["format"],
                llm = properties["llm"]?.toBoolean(),
            ),
        )
    }
}
