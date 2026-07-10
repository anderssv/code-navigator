package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.formatting.JsonRaw
import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.navigation.types.ClassName

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
            val collapsedTag = collapsedImplementorsTag(node)
            appendLine("$indent${direction.arrow} ${node.method.qualifiedName} ($sourceFile$lineRef)${formatAnnotationTags(node.annotations)}$sourceSetTag$collapsedTag")
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

    /** " (+N more implementors, use --max-implementors=N to see all)" or "" when nothing was collapsed. */
    internal fun collapsedImplementorsTag(node: CallTreeNode): String =
        if (node.collapsedImplementorCount > 0) {
            " (+${node.collapsedImplementorCount} more implementor${if (node.collapsedImplementorCount == 1) "" else "s"}, use --max-implementors to see all)"
        } else {
            ""
        }

    /**
     * When a pattern matches multiple methods all in the same class, and the pattern
     * matches the class name (not any method name individually), suggest cnavFindUsages.
     */
    fun classMatchHint(pattern: String, methods: List<MethodRef>): String? {
        if (methods.size < 2) return null
        val distinctClasses = methods.map { it.className }.distinct()
        if (distinctClasses.size != 1) return null

        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        val className = distinctClasses.first()
        val classSimpleName = className.simpleName()

        if (!regex.containsMatchIn(classSimpleName)) return null

        val anyMethodMatchesDirectly = methods.any { regex.containsMatchIn(it.methodName) }
        if (anyMethodMatchesDirectly) return null

        return "Hint: Pattern '$pattern' matched all methods in $className. " +
            "If you want type-level references instead, use: cnavFindUsages --type=$classSimpleName"
    }

    fun formatJson(trees: List<CallTreeNode>, direction: CallDirection? = null): String =
        jsonArray(trees) { node -> renderCallNodeJson(node, direction, isRoot = true) }

    private fun renderCallNodeJson(node: CallTreeNode, direction: CallDirection? = null, isRoot: Boolean = false): String {
        val children = jsonArray(node.children) { child -> renderCallNodeJson(child) }
        val hint = if (isRoot && direction != null && node.children.isEmpty()) {
            frameworkEntryPointHint(node, direction)
        } else {
            null
        }
        return jsonObject(
            "method" to node.method.qualifiedName,
            "sourceFile" to node.sourceFile,
            "lineNumber" to node.lineNumber,
            "sourceSet" to node.sourceSet?.label,
            "annotations" to if (node.annotations.isNotEmpty()) JsonRaw(renderAnnotationTagsJson(node.annotations)) else null,
            "children" to JsonRaw(children),
            "frameworkEntryPointHint" to hint,
            "collapsedImplementorCount" to if (node.collapsedImplementorCount > 0) node.collapsedImplementorCount else null,
        )
    }

    private fun renderAnnotationTagsJson(tags: List<AnnotationTag>): String =
        tags.joinToString(",", "[", "]") { tag ->
            val params = if (tag.parameters.isNotEmpty()) {
                JsonRaw(jsonObject(*tag.parameters.map { (k, v) -> k to v }.toTypedArray()))
            } else {
                null
            }
            jsonObject(
                "name" to tag.name.value,
                "framework" to tag.framework,
                "parameters" to params,
            )
        }

    fun formatLlm(trees: List<CallTreeNode>, direction: CallDirection): String = buildString {
        trees.forEachIndexed { index, tree ->
            if (index > 0) appendLine()
            val lineRef = tree.lineNumber?.let { ":$it" } ?: ""
            append("${tree.method.qualifiedName} ${tree.sourceFile ?: "<unknown>"}$lineRef${formatAnnotationTagsLlm(tree.annotations)}")
            if (tree.children.isNotEmpty()) {
                renderChildrenLlm(tree.children, direction, 1)
            } else {
                appendLine()
                append("  ${direction.emptyMessage}")
                val hint = frameworkEntryPointHint(tree, direction)
                if (hint != null) append(" — $hint")
            }
        }
    }.trimEnd()

    private fun StringBuilder.renderChildrenLlm(children: List<CallTreeNode>, direction: CallDirection, depth: Int) {
        val indent = "  ".repeat(depth)
        for (node in children) {
            val lineRef = node.lineNumber?.let { ":$it" } ?: ""
            val sourceSetTag = node.sourceSet?.let { " [${it.label}]" } ?: ""
            val collapsedTag = collapsedImplementorsTag(node)
            appendLine()
            append("$indent${direction.arrow} ${node.method.qualifiedName} ${node.sourceFile ?: "<unknown>"}$lineRef${formatAnnotationTagsLlm(node.annotations)}$sourceSetTag$collapsedTag")
            if (node.children.isNotEmpty()) {
                renderChildrenLlm(node.children, direction, depth + 1)
            }
        }
    }

    private fun formatAnnotationTagsLlm(annotations: List<AnnotationTag>): String =
        if (annotations.isEmpty()) "" else " [${annotations.joinToString(", ") { tag ->
            val params = if (tag.parameters.isNotEmpty()) {
                val paramStr = tag.parameters.entries.joinToString(",") { "${it.key}=\"${it.value}\"" }
                "($paramStr)"
            } else {
                ""
            }
            val suffix = if (tag.framework != null) " [${tag.framework}]" else ""
            "@${tag.name.simpleName()}$params$suffix"
        }}]"
}
