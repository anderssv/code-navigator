package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry

data class CallPathConfig(
    val from: String?,
    val fromAnnotation: String?,
    val to: String,
    val guard: String?,
    val guardDepth: Int,
    val maxDepth: Int,
    val top: Int,
    val projectOnly: Boolean,
    val scope: Scope,
    val format: OutputFormat,
    /** Show the conditions each step is called under, from Kotlin source. */
    val branches: Boolean = false,
) {
    fun guardRegex(): Regex? = guard?.let { Regex(it, RegexOption.IGNORE_CASE) }

    fun fromAnnotationRegex(): Regex? = fromAnnotation?.let { Regex(it, RegexOption.IGNORE_CASE) }

    companion object {
        fun parse(properties: Map<String, String?>): CallPathConfig {
            val from = TaskRegistry.PATH_FROM.parseFrom(properties)
            val fromAnnotation = TaskRegistry.PATH_FROM_ANNOTATION.parseFrom(properties)
            require(from != null || fromAnnotation != null) {
                "Missing entry points: give '${TaskRegistry.PATH_FROM.name}', '${TaskRegistry.PATH_FROM_ANNOTATION.name}', or both"
            }
            val guard = TaskRegistry.GUARD.parseFrom(properties)
            requireRegex(TaskRegistry.PATH_FROM_ANNOTATION.name, fromAnnotation)
            requireRegex(TaskRegistry.GUARD.name, guard)
            val guardDepth = TaskRegistry.GUARD_DEPTH.parseFrom(properties)
            require(guardDepth >= 0) { "'${TaskRegistry.GUARD_DEPTH.name}' must be 0 or more, got $guardDepth" }
            val maxDepth = TaskRegistry.PATH_MAXDEPTH.parseFrom(properties)
            require(maxDepth > 0) { "'${TaskRegistry.PATH_MAXDEPTH.name}' must be 1 or more, got $maxDepth" }
            return CallPathConfig(
                from = from,
                fromAnnotation = fromAnnotation,
                to = TaskRegistry.PATH_TO.parseRequiredFrom(properties),
                guard = guard,
                guardDepth = guardDepth,
                maxDepth = maxDepth,
                top = TaskRegistry.PATH_TOP.parseFrom(properties),
                projectOnly = TaskRegistry.PROJECTONLY.parseFrom(properties),
                scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
                format = ParamDef.parseFormat(properties),
                branches = TaskRegistry.BRANCHES.parseFrom(properties),
            )
        }

        private fun requireRegex(name: String, value: String?) {
            if (value == null) return
            try {
                Regex(value)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("'$name' is not a valid regex: ${e.message}", e)
            }
        }
    }
}
