package no.f12.codenavigator.navigation.annotation

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
}
