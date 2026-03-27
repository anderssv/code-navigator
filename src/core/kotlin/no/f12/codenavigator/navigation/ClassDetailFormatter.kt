package no.f12.codenavigator.navigation

object ClassDetailFormatter {

    fun format(details: List<ClassDetail>): String = buildString {
        details.forEachIndexed { index, detail ->
            if (index > 0) appendLine()
            appendLine("=== ${detail.className} (${detail.sourceFile}) ===")

            detail.annotations.forEach { annotation ->
                appendLine(formatAnnotation(annotation))
            }

            detail.superClass?.let { appendLine("Extends: $it") }
            if (detail.interfaces.isNotEmpty()) {
                appendLine("Implements: ${detail.interfaces.joinToString(", ")}")
            }

            if (detail.fields.isNotEmpty()) {
                appendLine()
                appendLine("Fields:")
                detail.fields.forEach { field ->
                    field.annotations.forEach { annotation ->
                        appendLine("  ${formatAnnotation(annotation)}")
                    }
                    appendLine("  ${field.name}: ${field.type}")
                }
            }

            if (detail.methods.isNotEmpty()) {
                appendLine()
                appendLine("Methods:")
                detail.methods.forEach { method ->
                    method.annotations.forEach { annotation ->
                        appendLine("  ${formatAnnotation(annotation)}")
                    }
                    val params = method.parameterTypes.joinToString(", ")
                    appendLine("  ${method.name}($params): ${method.returnType}")
                }
            }
        }
    }.trimEnd()

    private fun formatAnnotation(annotation: AnnotationDetail): String = buildString {
        append("@${annotation.name}")
        if (annotation.parameters.isNotEmpty()) {
            val params = annotation.parameters.entries.joinToString(", ") { "${it.key}=\"${it.value}\"" }
            append("($params)")
        }
    }
}
