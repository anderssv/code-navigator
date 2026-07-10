package no.f12.codenavigator.navigation.classinfo

import no.f12.codenavigator.formatting.JsonRaw
import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.jsonStringArray

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
        append("@${annotation.name.simpleName()}")
        if (annotation.parameters.isNotEmpty()) {
            val params = annotation.parameters.entries.joinToString(", ") { "${it.key}=\"${it.value}\"" }
            append("($params)")
        }
    }

    fun formatJson(details: List<ClassDetail>): String =
        jsonArray(details.sortedBy { it.className }) { d ->
            jsonObject(
                "className" to d.className.toString(),
                "sourceFile" to d.sourceFile,
                "superClass" to d.superClass?.toString(),
                "annotations" to if (d.annotations.isNotEmpty()) JsonRaw(renderAnnotationsJson(d.annotations)) else null,
                "interfaces" to JsonRaw(jsonStringArray(d.interfaces.map { it.toString() })),
                "fields" to JsonRaw(jsonArray(d.fields) { f ->
                    jsonObject(
                        "name" to f.name,
                        "type" to f.type,
                        "annotations" to if (f.annotations.isNotEmpty()) JsonRaw(renderAnnotationsJson(f.annotations)) else null,
                    )
                }),
                "methods" to JsonRaw(jsonArray(d.methods) { m ->
                    jsonObject(
                        "name" to m.name,
                        "parameters" to JsonRaw(jsonStringArray(m.parameterTypes)),
                        "returnType" to m.returnType,
                        "annotations" to if (m.annotations.isNotEmpty()) JsonRaw(renderAnnotationsJson(m.annotations)) else null,
                    )
                }),
            )
        }

    private fun renderAnnotationsJson(annotations: List<AnnotationDetail>): String =
        jsonArray(annotations) { a ->
            jsonObject(
                "name" to a.name.value,
                "parameters" to JsonRaw(jsonObject(*a.parameters.map { (k, v) -> k to v }.toTypedArray())),
            )
        }

    fun formatLlm(details: List<ClassDetail>): String =
        details.sortedBy { it.className }.joinToString("\n") { d ->
            buildString {
                append("${d.className} ${d.sourceFile}")
                if (d.annotations.isNotEmpty()) append(" annotations:${d.annotations.joinToString(",") { formatAnnotationCompact(it) }}")
                if (d.superClass != null) append(" extends:${d.superClass}")
                if (d.interfaces.isNotEmpty()) append(" implements:${d.interfaces.joinToString(",")}")
                if (d.fields.isNotEmpty()) append(" fields:${d.fields.joinToString(",") { formatFieldCompact(it) }}")
                if (d.methods.isNotEmpty()) append(" methods:${d.methods.joinToString(",") { formatMethodCompact(it) }}")
            }
        }

    private fun formatAnnotationCompact(annotation: AnnotationDetail): String = buildString {
        append("@${annotation.name.simpleName()}")
        if (annotation.parameters.isNotEmpty()) {
            val params = annotation.parameters.entries.joinToString(",") { "${it.key}=\"${it.value}\"" }
            append("($params)")
        }
    }

    private fun formatFieldCompact(field: FieldDetail): String {
        val prefix = field.annotations.joinToString("") { "${formatAnnotationCompact(it)}+" }
        return "$prefix${field.name}:${field.type}"
    }

    private fun formatMethodCompact(method: MethodDetail): String {
        val prefix = method.annotations.joinToString("") { "${formatAnnotationCompact(it)}+" }
        return "$prefix${method.name}(${method.parameterTypes.joinToString(",")}):${method.returnType}"
    }
}
