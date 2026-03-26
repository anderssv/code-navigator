package no.f12.codenavigator.navigation

data class ClassComplexity(
    val className: ClassName,
    val sourceFile: String,
    val fanOut: Int,
    val fanIn: Int,
    val distinctOutgoingClasses: Int,
    val distinctIncomingClasses: Int,
    val outgoingByClass: List<Pair<ClassName, Int>>,
    val incomingByClass: List<Pair<ClassName, Int>>,
)

object ClassComplexityAnalyzer {
    fun analyze(
        graph: CallGraph,
        classPattern: String,
        projectOnly: Boolean,
    ): List<ClassComplexity> {
        val regex = Regex(classPattern)
        val projectClasses = graph.projectClasses()
        val matchingClasses = projectClasses.filter { it.matches(regex) && !it.isGenerated() }

        return matchingClasses.map { className ->
            analyzeClass(graph, className, projectOnly, projectClasses)
        }.sortedByDescending { it.fanOut }
    }

    private fun analyzeClass(
        graph: CallGraph,
        className: ClassName,
        projectOnly: Boolean,
        projectClasses: Set<ClassName>,
    ): ClassComplexity {
        val outgoing = mutableListOf<Pair<ClassName, String>>()
        val incoming = mutableListOf<Pair<ClassName, String>>()

        graph.forEachEdge { caller, callee ->
            val callerClass = caller.className
            val calleeClass = callee.className

            if (callerClass == className && calleeClass != className) {
                if (!projectOnly || callee.className in projectClasses) {
                    outgoing.add(calleeClass to callee.methodName)
                }
            }
            if (calleeClass == className && callerClass != className) {
                if (!projectOnly || caller.className in projectClasses) {
                    incoming.add(callerClass to caller.methodName)
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
            className = className,
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
