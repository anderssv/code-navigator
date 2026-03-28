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
                    append("  ${method.method.methodName} [${sortedAnnotations.joinToString(", ") { "@${it.simpleName()}" }}]")
                }
            }
        }
    }
}
