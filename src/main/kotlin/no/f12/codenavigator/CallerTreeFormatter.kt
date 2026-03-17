package no.f12.codenavigator

object CallerTreeFormatter {
    fun format(graph: CallGraph, methods: List<MethodRef>, maxDepth: Int): String =
        CallTreeFormatter.format(graph, methods, maxDepth, CallDirection.CALLERS)
}
