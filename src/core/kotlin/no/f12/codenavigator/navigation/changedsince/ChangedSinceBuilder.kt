package no.f12.codenavigator.navigation.changedsince

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.callgraph.CallGraph
import no.f12.codenavigator.navigation.callgraph.MethodRef

data class ChangedClassImpact(
    val className: ClassName,
    val sourceFile: String,
    val callers: Set<MethodRef>,
)

object ChangedSinceBuilder {

    fun build(
        changedClasses: Set<ClassName>,
        graph: CallGraph,
        projectOnly: Boolean,
    ): List<ChangedClassImpact> {
        val projectClassFilter = if (projectOnly) graph.projectClassFilter() else { _ -> true }

        return changedClasses.map { className ->
            val callers = graph.callersOfClass(className)
                .filter { it.className != className }
                .filter(projectClassFilter)
                .toSet()

            ChangedClassImpact(
                className = className,
                sourceFile = graph.sourceFileOf(className),
                callers = callers,
            )
        }.sortedByDescending { it.callers.size }
    }
}
