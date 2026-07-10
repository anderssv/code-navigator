package no.f12.codenavigator.navigation.annotation

import no.f12.codenavigator.formatting.JsonRaw
import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.jsonStringArray

object AnnotationQueryFormatter {

    fun format(matches: List<AnnotationMatch>): String {
        if (matches.isEmpty()) return "No matching annotations found."

        return matches.joinToString("\n") { match ->
            buildString {
                append(match.className.value)
                if (match.sourceFile != null) {
                    append(" (${match.sourceFile})")
                }
                if (match.classAnnotations.isNotEmpty()) {
                    val sorted = match.classAnnotations.sorted()
                    append(" [${sorted.joinToString(", ") { "@${it.simpleName()}" }}]")
                }
                for (method in match.matchedMethods) {
                    appendLine()
                    val sortedAnnotations = method.annotations.sorted()
                    append("  method ${method.method.methodName} [${sortedAnnotations.joinToString(", ") { "@${it.simpleName()}" }}]")
                }
                for (field in match.matchedFields) {
                    appendLine()
                    val sortedAnnotations = field.annotations.sorted()
                    append("  field ${field.field.fieldName} [${sortedAnnotations.joinToString(", ") { "@${it.simpleName()}" }}]")
                }
            }
        }
    }

    fun noResultsHints(pattern: String, targets: Set<AnnotationTarget> = AnnotationTarget.ALL): List<String> = buildList {
        if (targets != AnnotationTarget.ALL) {
            val searched = targets.sorted().joinToString(",") { it.name.lowercase() }
            add("Only searching target(s): $searched. Use --target=class,method,field to search everything.")
        }
        add("Only RUNTIME and CLASS retention annotations are visible in bytecode. SOURCE retention annotations (e.g. @Suppress) cannot be found.")
    }

    fun formatJson(matches: List<AnnotationMatch>): String =
        jsonArray(matches) { match ->
            jsonObject(
                "className" to match.className.value,
                "sourceFile" to match.sourceFile,
                "classAnnotations" to JsonRaw(jsonStringArray(match.classAnnotations.sorted().map { it.value })),
                "methods" to JsonRaw(jsonArray(match.matchedMethods) { method ->
                    jsonObject(
                        "method" to method.method.methodName,
                        "annotations" to JsonRaw(jsonStringArray(method.annotations.sorted().map { it.value })),
                    )
                }),
                "fields" to JsonRaw(jsonArray(match.matchedFields) { field ->
                    jsonObject(
                        "field" to field.field.fieldName,
                        "annotations" to JsonRaw(jsonStringArray(field.annotations.sorted().map { it.value })),
                    )
                }),
            )
        }

    fun formatLlm(matches: List<AnnotationMatch>): String {
        if (matches.isEmpty()) return "(no matches)"
        return matches.joinToString("\n") { match ->
            buildString {
                append("${match.className.value} ${match.sourceFile ?: "<unknown>"}")
                if (match.classAnnotations.isNotEmpty()) {
                    append(" ${match.classAnnotations.sorted().joinToString(",") { "@${it.simpleName()}" }}")
                }
                for (method in match.matchedMethods) {
                    appendLine()
                    append("  method ${method.method.methodName} ${method.annotations.sorted().joinToString(",") { "@${it.simpleName()}" }}")
                }
                for (field in match.matchedFields) {
                    appendLine()
                    append("  field ${field.field.fieldName} ${field.annotations.sorted().joinToString(",") { "@${it.simpleName()}" }}")
                }
            }
        }
    }
}
