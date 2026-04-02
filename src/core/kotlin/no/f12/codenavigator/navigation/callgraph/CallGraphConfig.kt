package no.f12.codenavigator.navigation.callgraph

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.SourceSet

data class CallGraphConfig(
    val method: String,
    val maxDepth: Int,
    val projectOnly: Boolean,
    val filterSynthetic: Boolean,
    val prodOnly: Boolean,
    val testOnly: Boolean,
    val format: OutputFormat,
) {
    fun buildFilter(graph: CallGraph): ((MethodRef) -> Boolean)? {
        val filters = buildList {
            if (projectOnly) add(graph.projectClassFilter())
            if (filterSynthetic) add { ref: MethodRef -> !ref.isGenerated() }
            if (prodOnly) add { ref: MethodRef -> graph.sourceSetOf(ref.className) == SourceSet.MAIN }
            if (testOnly) add { ref: MethodRef -> graph.sourceSetOf(ref.className) == SourceSet.TEST }
        }
        return if (filters.isEmpty()) null else { ref -> filters.all { it(ref) } }
    }

    companion object {
        private const val LEGACY_METHOD_KEY = "method"

        fun parse(properties: Map<String, String?>): CallGraphConfig {
            val pattern = properties[TaskRegistry.CALL_PATTERN.name]
                ?: properties[LEGACY_METHOD_KEY]
            return CallGraphConfig(
                method = pattern
                    ?: throw IllegalArgumentException("Missing required property '${TaskRegistry.CALL_PATTERN.name}'"),
                maxDepth = TaskRegistry.MAXDEPTH.parseFrom(properties),
                projectOnly = TaskRegistry.PROJECTONLY.parseFrom(properties),
                filterSynthetic = TaskRegistry.FILTER_SYNTHETIC.parseFrom(properties),
                prodOnly = TaskRegistry.PROD_ONLY.parseFrom(properties),
                testOnly = TaskRegistry.TEST_ONLY.parseFrom(properties),
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
