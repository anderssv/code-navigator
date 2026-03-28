package no.f12.codenavigator.navigation.complexity

import no.f12.codenavigator.navigation.ClassName

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
    }

    private fun formatByClass(byClass: List<Pair<ClassName, Int>>): String =
        if (byClass.isEmpty()) "(none)"
        else byClass.joinToString(", ") { (cls, count) -> "$cls ($count)" }
}
