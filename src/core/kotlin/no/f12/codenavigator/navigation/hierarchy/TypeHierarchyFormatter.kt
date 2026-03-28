package no.f12.codenavigator.navigation.hierarchy

object TypeHierarchyFormatter {

    fun format(results: List<TypeHierarchyResult>): String = buildString {
        results.forEachIndexed { index, result ->
            if (index > 0) appendLine()
            appendLine("=== ${result.className} (${result.sourceFile}) ===")

            if (result.supertypes.isNotEmpty()) {
                appendLine("Supertypes:")
                result.supertypes.forEach { supertype ->
                    renderSupertype(supertype, depth = 1)
                }
            }

            if (result.implementors.isNotEmpty()) {
                appendLine("Implementors:")
                result.implementors.forEach { impl ->
                    appendLine("  ${impl.className} (${impl.sourceFile})")
                }
            }
        }
    }.trimEnd()

    private fun StringBuilder.renderSupertype(supertype: SupertypeInfo, depth: Int) {
        val indent = "  ".repeat(depth)
        val kindLabel = when (supertype.kind) {
            SupertypeKind.CLASS -> "extends"
            SupertypeKind.INTERFACE -> "implements"
        }
        appendLine("$indent$kindLabel ${supertype.className}")
        supertype.supertypes.forEach { child ->
            renderSupertype(child, depth + 1)
        }
    }
}
