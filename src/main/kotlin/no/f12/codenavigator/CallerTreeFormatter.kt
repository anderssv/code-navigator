package no.f12.codenavigator

object CallerTreeFormatter {
    fun format(
        graph: CallGraph,
        methods: List<MethodRef>,
        maxDepth: Int,
        filter: ((MethodRef) -> Boolean)? = null,
    ): String =
        CallTreeFormatter.format(graph, methods, maxDepth, CallDirection.CALLERS, filter)
}
