package no.f12.codenavigator

object CalleeTreeFormatter {
    fun format(
        graph: CallGraph,
        methods: List<MethodRef>,
        maxDepth: Int,
        filter: ((MethodRef) -> Boolean)? = null,
    ): String =
        CallTreeFormatter.format(graph, methods, maxDepth, CallDirection.CALLEES, filter)
}
