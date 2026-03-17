package no.f12.codenavigator

object CalleeTreeFormatter {
    fun format(graph: CallGraph, methods: List<MethodRef>, maxDepth: Int): String =
        CallTreeFormatter.format(graph, methods, maxDepth, CallDirection.CALLEES)
}
