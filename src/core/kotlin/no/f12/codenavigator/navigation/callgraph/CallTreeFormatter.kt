package no.f12.codenavigator.navigation.callgraph

import no.f12.codenavigator.navigation.ClassName

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
            appendLine("${tree.method.qualifiedName}${formatAnnotationTags(tree.annotations)}")
            if (tree.children.isEmpty()) {
                append("  ${direction.emptyMessage}")
                val hint = frameworkEntryPointHint(tree, direction)
                if (hint != null) append(" — $hint")
            } else {
                renderChildren(tree.children, direction, depth = 1)
            }
        }
    }.trimEnd()

    private fun formatAnnotationTags(annotations: List<AnnotationTag>): String =
        if (annotations.isEmpty()) "" else " [${annotations.joinToString(", ") { tag ->
            val params = if (tag.parameters.isNotEmpty()) {
                val paramStr = tag.parameters.entries.joinToString(", ") { "${it.key}=\"${it.value}\"" }
                "($paramStr)"
            } else {
                ""
            }
            val suffix = if (tag.framework != null) " [${tag.framework}]" else ""
            "@${tag.name.simpleName()}$params$suffix"
        }}]"

    private fun StringBuilder.renderChildren(
        children: List<CallTreeNode>,
        direction: CallDirection,
        depth: Int,
    ) {
        val indent = "  ".repeat(depth)
        for (node in children) {
            val sourceFile = node.sourceFile ?: "<unknown>"
            val lineRef = node.lineNumber?.let { ":$it" } ?: ""
            val sourceSetTag = node.sourceSet?.let { " [${it.label}]" } ?: ""
            appendLine("$indent${direction.arrow} ${node.method.qualifiedName} ($sourceFile$lineRef)${formatAnnotationTags(node.annotations)}$sourceSetTag")
            if (node.children.isNotEmpty()) {
                renderChildren(node.children, direction, depth + 1)
            }
        }
    }

    internal fun frameworkEntryPointHint(node: CallTreeNode, direction: CallDirection): String? {
        if (direction != CallDirection.CALLERS) return null
        val frameworkAnnotation = node.annotations.firstOrNull { it.framework != null } ?: return null
        return "@${frameworkAnnotation.name.simpleName()} is a ${frameworkAnnotation.framework} entry point; invoked by the framework at runtime."
    }
}
