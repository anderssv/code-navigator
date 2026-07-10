package no.f12.codenavigator.navigation.stringconstant

import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject

object StringConstantFormatter {

    fun format(matches: List<StringConstantMatch>): String {
        if (matches.isEmpty()) return "No string constant matches found."

        val classWidth = maxOf("Class".length, matches.maxOf { it.className.toString().length })
        val methodWidth = maxOf("Method".length, matches.maxOf { it.methodName.length })
        val valueWidth = maxOf("Value".length, matches.maxOf { it.value.length })
        val sourceWidth = maxOf("Source".length, matches.maxOf { it.sourceFile.length })

        return buildString {
            appendLine(
                "%-${classWidth}s  %-${methodWidth}s  %-${valueWidth}s  %-${sourceWidth}s".format(
                    "Class", "Method", "Value", "Source",
                ),
            )
            matches.forEachIndexed { index, m ->
                if (index > 0) appendLine()
                append(
                    "%-${classWidth}s  %-${methodWidth}s  %-${valueWidth}s  %-${sourceWidth}s".format(
                        m.className.toString(), m.methodName, m.value, m.sourceFile,
                    ),
                )
            }
        }
    }

    fun formatJson(matches: List<StringConstantMatch>): String =
        jsonArray(matches) { m ->
            jsonObject(
                "className" to m.className.toString(),
                "methodName" to m.methodName,
                "value" to m.value,
                "sourceFile" to m.sourceFile,
            )
        }

    fun formatLlm(matches: List<StringConstantMatch>): String =
        matches.joinToString("\n") { m ->
            "${m.className}.${m.methodName}: \"${m.value}\" ${m.sourceFile}"
        }
}
