package no.f12.codenavigator.navigation

data class ClassComplexity(
    val className: String,
    val sourceFile: String,
    val fanOut: Int,
    val fanIn: Int,
    val distinctOutgoingClasses: Int,
    val distinctIncomingClasses: Int,
    val outgoingByClass: List<Pair<String, Int>>,
    val incomingByClass: List<Pair<String, Int>>,
)

object ClassComplexityAnalyzer {
    fun analyze(
        graph: CallGraph,
        classPattern: String,
        projectOnly: Boolean,
    ): List<ClassComplexity> {
        val regex = Regex(classPattern)
        val projectClasses = graph.projectClasses()
        val matchingClasses = projectClasses.filter { regex.containsMatchIn(it.value) && '$' !in it.value }

        return matchingClasses.map { className ->
            analyzeClass(graph, className, projectOnly, projectClasses)
        }
    }

    private fun analyzeClass(
        graph: CallGraph,
        className: ClassName,
        projectOnly: Boolean,
        projectClasses: Set<ClassName>,
    ): ClassComplexity {
        val outgoing = mutableListOf<Pair<String, String>>()
        val incoming = mutableListOf<Pair<String, String>>()

        graph.forEachEdge { caller, callee ->
            val callerClass = caller.className
            val calleeClass = callee.className

            if (callerClass == className && calleeClass != className) {
                if (!projectOnly || callee.className in projectClasses) {
                    outgoing.add(calleeClass.value to callee.methodName)
                }
            }
            if (calleeClass == className && callerClass != className) {
                if (!projectOnly || caller.className in projectClasses) {
                    incoming.add(callerClass.value to caller.methodName)
                }
            }
        }

        val outgoingByClass = outgoing
            .groupBy { it.first }
            .map { (cls, calls) -> cls to calls.size }
            .sortedByDescending { it.second }

        val incomingByClass = incoming
            .groupBy { it.first }
            .map { (cls, calls) -> cls to calls.size }
            .sortedByDescending { it.second }

        return ClassComplexity(
            className = className.value,
            sourceFile = graph.sourceFileOf(className),
            fanOut = outgoing.size,
            fanIn = incoming.size,
            distinctOutgoingClasses = outgoingByClass.size,
            distinctIncomingClasses = incomingByClass.size,
            outgoingByClass = outgoingByClass,
            incomingByClass = incomingByClass,
        )
    }
}
