package no.f12.codenavigator.navigation.complexity

import no.f12.codenavigator.formatting.JsonRaw
import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.withInterpretation
import no.f12.codenavigator.navigation.types.ClassName

object ComplexityFormatter {

    internal const val COMPLEXITY_INTERPRETATION = "Interpretation: fan-out = total outgoing references (distinct classes). High fan-out means the class knows too much. fan-in = total incoming references. High fan-in means many classes depend on it — changes are risky. Classes with both high fan-in and high fan-out are prime refactoring targets."

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

    fun formatJson(results: List<ClassComplexity>): String =
        jsonArray(results) { c ->
            jsonObject(
                "className" to c.className.toString(),
                "sourceFile" to c.sourceFile,
                "fanOut" to c.fanOut,
                "fanIn" to c.fanIn,
                "distinctOutgoingClasses" to c.distinctOutgoingClasses,
                "distinctIncomingClasses" to c.distinctIncomingClasses,
                "outgoingByClass" to JsonRaw(jsonArray(c.outgoingByClass) { (cls, count) ->
                    jsonObject("className" to cls.toString(), "count" to count)
                }),
                "incomingByClass" to JsonRaw(jsonArray(c.incomingByClass) { (cls, count) ->
                    jsonObject("className" to cls.toString(), "count" to count)
                }),
            )
        }

    fun formatLlm(results: List<ClassComplexity>): String =
        results.joinToString("\n\n") { c ->
            buildString {
                append("${c.className} out=${c.fanOut}/${c.distinctOutgoingClasses} in=${c.fanIn}/${c.distinctIncomingClasses}")
                if (c.outgoingByClass.isEmpty()) {
                    append("\n  outgoing: none")
                } else {
                    append("\n  outgoing:")
                    c.outgoingByClass.forEach { append("\n    ${it.first}(${it.second})") }
                }
                if (c.incomingByClass.isEmpty()) {
                    append("\n  incoming: none")
                } else {
                    append("\n  incoming:")
                    c.incomingByClass.forEach { append("\n    ${it.first}(${it.second})") }
                }
            }
        }.withInterpretation(COMPLEXITY_INTERPRETATION)
}
