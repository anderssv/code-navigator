package no.f12.codenavigator.navigation.complexity

import no.f12.codenavigator.navigation.core.ClassName

object ComplexityFormatter {

    fun format(results: List<ClassComplexity>): String {
        if (results.isEmpty()) return "No matching classes found."

        return results.joinToString("\n\n") { formatClass(it) }
    }

    private fun formatClass(c: ClassComplexity): String = buildString {
        appendLine("${c.className} (${c.sourceFile})")
        appendLine("  Fan-out: ${c.fanOut} calls to ${c.distinctOutgoingClasses} distinct classes")
        appendLine("  Fan-in:  ${c.fanIn} calls from ${c.distinctIncomingClasses} distinct classes")
        appendLine("  Top outgoing: ${formatByClass(c.outgoingByClass)}")
        append("  Top incoming: ${formatByClass(c.incomingByClass)}")
        val recommendation = recommend(c)
        if (recommendation != null) {
            append("\n  → $recommendation")
        }
    }

    private fun recommend(c: ClassComplexity): String? {
        val parts = mutableListOf<String>()
        if (c.distinctOutgoingClasses > 10) parts += "High fan-out — candidate for splitting."
        if (c.distinctIncomingClasses > 20) parts += "High fan-in — changes here ripple widely."
        return parts.joinToString(" ").ifEmpty { null }
    }

    private fun formatByClass(byClass: List<Pair<ClassName, Int>>): String =
        if (byClass.isEmpty()) "(none)"
        else byClass.joinToString(", ") { (cls, count) -> "$cls ($count)" }
}
