package no.f12.codenavigator.navigation

enum class CallDirection(
    val arrow: String,
    val emptyMessage: String,
    val resolve: (CallGraph, ClassName, String) -> Set<MethodRef>,
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
        filter: ((MethodRef) -> Boolean)? = null,
    ): String {
        val trees = CallTreeBuilder.build(graph, methods, maxDepth, direction, filter)
        return renderTrees(trees, direction)
    }

    fun renderTrees(
        trees: List<CallTreeNode>,
        direction: CallDirection,
    ): String = buildString {
        trees.forEachIndexed { index, tree ->
            if (index > 0) appendLine()
            appendLine(tree.method.qualifiedName)
            if (tree.children.isEmpty()) {
                append("  ${direction.emptyMessage}")
            } else {
                renderChildren(tree.children, direction, depth = 1)
            }
        }
    }.trimEnd()

    private fun StringBuilder.renderChildren(
        children: List<CallTreeNode>,
        direction: CallDirection,
        depth: Int,
    ) {
        val indent = "  ".repeat(depth)
        for (node in children) {
            val sourceFile = node.sourceFile ?: "<unknown>"
            appendLine("$indent${direction.arrow} ${node.method.qualifiedName} ($sourceFile)")
            if (node.children.isNotEmpty()) {
                renderChildren(node.children, direction, depth + 1)
            }
        }
    }
}
