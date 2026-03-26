package no.f12.codenavigator.navigation

data class CallTreeNode(
    val method: MethodRef,
    val sourceFile: String?,
    val lineNumber: Int?,
    val children: List<CallTreeNode>,
)

object CallTreeBuilder {

    fun build(
        graph: CallGraph,
        roots: List<MethodRef>,
        maxDepth: Int,
        direction: CallDirection,
        filter: ((MethodRef) -> Boolean)? = null,
    ): List<CallTreeNode> {
        return roots.map { method ->
            buildNode(graph, method, maxDepth, direction, depth = 0, visited = mutableSetOf(), filter = filter)
        }
    }

    private fun buildNode(
        graph: CallGraph,
        method: MethodRef,
        maxDepth: Int,
        direction: CallDirection,
        depth: Int,
        visited: MutableSet<MethodRef>,
        filter: ((MethodRef) -> Boolean)?,
    ): CallTreeNode {
        val sourceFile = graph.sourceFileOf(method.className)
        val lineNumber = graph.lineNumberOf(method)
        val depthCheck = depth < maxDepth
        val visitedCheck = method !in visited
        val children = if (depthCheck && visitedCheck) {
            visited.add(method)
            val related = direction.resolve(graph, method.className, method.methodName)
                .let { refs -> if (filter != null) refs.filter(filter).toSet() else refs }
            related.sortedBy { it.qualifiedName }.map { child ->
                buildNode(graph, child, maxDepth, direction, depth + 1, visited, filter)
            }
        } else {
            emptyList()
        }
        return CallTreeNode(method, sourceFile, lineNumber, children)
    }
}
