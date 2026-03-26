package no.f12.codenavigator.navigation

object ClassDetailFormatter {

    fun format(details: List<ClassDetail>): String = buildString {
        details.forEachIndexed { index, detail ->
            if (index > 0) appendLine()
            appendLine("=== ${detail.className} (${detail.sourceFile}) ===")

            detail.superClass?.let { appendLine("Extends: $it") }
            if (detail.interfaces.isNotEmpty()) {
                appendLine("Implements: ${detail.interfaces.joinToString(", ")}")
            }

            if (detail.fields.isNotEmpty()) {
                appendLine()
                appendLine("Fields:")
                detail.fields.forEach { field ->
                    appendLine("  ${field.name}: ${field.type}")
                }
            }

            if (detail.methods.isNotEmpty()) {
                appendLine()
                appendLine("Methods:")
                detail.methods.forEach { method ->
                    val params = method.parameterTypes.joinToString(", ")
                    appendLine("  ${method.name}($params): ${method.returnType}")
                }
            }
        }
    }.trimEnd()
}
