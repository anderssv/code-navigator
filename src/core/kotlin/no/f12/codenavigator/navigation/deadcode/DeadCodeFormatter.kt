package no.f12.codenavigator.navigation.deadcode

import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.navigation.types.Scope

object DeadCodeFormatter {

    private val NOTE = "Note: Dead code detection is a hard problem with many edge cases (reflection, serialization, generated code). Use exclude=<regex> to filter out packages or classes you know are not dead."

    fun format(dead: List<DeadCode>, scope: Scope = Scope.ALL): String {
        if (dead.isEmpty()) return "No potential dead code found."

        val classWidth = maxOf("Class".length, dead.maxOf { it.className.toString().length })
        val memberWidth = maxOf("Member".length, dead.maxOf { (it.memberName ?: "-").length })
        val kindWidth = maxOf("Kind".length, dead.maxOf { it.kind.name.length })
        val sourceWidth = maxOf("Source".length, dead.maxOf { it.sourceFile.length })
        val confWidth = maxOf("Confidence".length, dead.maxOf { it.confidence.name.length })
        val reasonWidth = maxOf("Reason".length, dead.maxOf { it.reason.name.length })

        return buildString {
            appendLine(
                "%-${classWidth}s  %-${memberWidth}s  %-${kindWidth}s  %-${sourceWidth}s  %-${confWidth}s  %-${reasonWidth}s".format(
                    "Class", "Member", "Kind", "Source", "Confidence", "Reason",
                )
            )
            dead.forEachIndexed { index, d ->
                if (index > 0) appendLine()
                append(
                    "%-${classWidth}s  %-${memberWidth}s  %-${kindWidth}s  %-${sourceWidth}s  %-${confWidth}s  %-${reasonWidth}s".format(
                        d.className.toString(), d.memberName ?: "-", d.kind.name, d.sourceFile, d.confidence.name, d.reason.name,
                    )
                )
            }
            appendLine()
            appendLine()
            if (scope == Scope.PROD) {
                appendLine("Test classes excluded. Use scope=all to include test classes.")
            }
            append(NOTE)
        }
    }

    fun formatJson(dead: List<DeadCode>, @Suppress("UNUSED_PARAMETER") scope: Scope = Scope.ALL): String =
        jsonArray(dead) { d ->
            jsonObject(
                "className" to d.className.toString(),
                "memberName" to d.memberName,
                "kind" to d.kind.name.lowercase(),
                "sourceFile" to d.sourceFile,
                "confidence" to d.confidence.name.lowercase(),
                "reason" to d.reason.name.lowercase(),
            )
        }

    fun formatLlm(dead: List<DeadCode>, scope: Scope = Scope.ALL): String {
        if (dead.isEmpty()) return ""
        val scopeNotice = if (scope == Scope.PROD) "Test classes excluded. Use scope=all to include test classes.\n" else ""
        return dead.joinToString("\n") { d ->
            val name = if (d.memberName != null) "${d.className}.${d.memberName}" else d.className.toString()
            "$name ${d.kind.name} ${d.sourceFile} confidence=${d.confidence.name} reason=${d.reason.name}"
        } + "\n\n" + scopeNotice + NOTE
    }
}
