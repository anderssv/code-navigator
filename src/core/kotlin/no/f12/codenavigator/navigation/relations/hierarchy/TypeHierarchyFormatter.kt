package no.f12.codenavigator.navigation.relations.hierarchy

import no.f12.codenavigator.formatting.JsonRaw
import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject

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

    fun formatJson(results: List<TypeHierarchyResult>): String =
        jsonArray(results.sortedBy { it.className }) { result ->
            jsonObject(
                "className" to result.className.toString(),
                "sourceFile" to result.sourceFile,
                "supertypes" to JsonRaw(renderSupertypesJson(result.supertypes)),
                "implementors" to JsonRaw(jsonArray(result.implementors.sortedBy { it.className }) { impl ->
                    jsonObject("className" to impl.className.toString(), "sourceFile" to impl.sourceFile)
                }),
            )
        }

    private fun renderSupertypesJson(supertypes: List<SupertypeInfo>): String =
        jsonArray(supertypes) { st ->
            jsonObject(
                "className" to st.className.toString(),
                "kind" to st.kind.name.lowercase(),
                "supertypes" to JsonRaw(renderSupertypesJson(st.supertypes)),
            )
        }

    fun formatLlm(results: List<TypeHierarchyResult>): String =
        results.sortedBy { it.className }.joinToString("\n\n") { result ->
            buildString {
                append("${result.className} ${result.sourceFile}")
                if (result.supertypes.isNotEmpty()) {
                    renderSupertypesLlm(result.supertypes, 1)
                }
                if (result.implementors.isNotEmpty()) {
                    appendLine()
                    append("  implementors: ${result.implementors.sortedBy { it.className }.joinToString(",") { "${it.className}(${it.sourceFile})" }}")
                }
            }
        }

    private fun StringBuilder.renderSupertypesLlm(supertypes: List<SupertypeInfo>, depth: Int) {
        val indent = "  ".repeat(depth)
        for (st in supertypes) {
            val kindLabel = when (st.kind) {
                SupertypeKind.CLASS -> "extends"
                SupertypeKind.INTERFACE -> "implements"
            }
            appendLine()
            append("$indent$kindLabel ${st.className}")
            if (st.supertypes.isNotEmpty()) {
                renderSupertypesLlm(st.supertypes, depth + 1)
            }
        }
    }
}
