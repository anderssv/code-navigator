package no.f12.codenavigator

enum class CallDirection(
    val arrow: String,
    val emptyMessage: String,
    val resolve: (CallGraph, String, String) -> Set<MethodRef>,
) {
    CALLERS("←", "(no callers)", { graph, cls, method -> graph.callersOf(cls, method) }),
    CALLEES("→", "(no callees)", { graph, cls, method -> graph.calleesOf(cls, method) }),
}

object CallTreeFormatter {
    fun format(
        graph: CallGraph,
        methods: List<MethodRef>,
        maxDepth: Int,
        direction: CallDirection,
    ): String = buildString {
        methods.forEachIndexed { index, method ->
            if (index > 0) appendLine()
            appendLine(method.qualifiedName)
            val related = direction.resolve(graph, method.className, method.methodName)
            if (related.isEmpty()) {
                append("  ${direction.emptyMessage}")
            } else {
                renderTree(graph, related, maxDepth, direction, depth = 1, visited = mutableSetOf(method))
            }
        }
    }.trimEnd()

    private fun StringBuilder.renderTree(
        graph: CallGraph,
        methods: Set<MethodRef>,
        maxDepth: Int,
        direction: CallDirection,
        depth: Int,
        visited: MutableSet<MethodRef>,
    ) {
        val indent = "  ".repeat(depth)
        val sorted = methods.sortedBy { it.qualifiedName }
        for (method in sorted) {
            val sourceFile = graph.sourceFileOf(method.className)
            appendLine("$indent${direction.arrow} ${method.qualifiedName} ($sourceFile)")
            if (depth < maxDepth && method !in visited) {
                visited.add(method)
                val next = direction.resolve(graph, method.className, method.methodName)
                if (next.isNotEmpty()) {
                    renderTree(graph, next, maxDepth, direction, depth + 1, visited)
                }
            }
        }
    }
}
