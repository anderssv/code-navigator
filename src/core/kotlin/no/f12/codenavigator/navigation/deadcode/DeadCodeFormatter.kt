package no.f12.codenavigator.navigation.deadcode

object DeadCodeFormatter {

    private val NOTE = "Note: Dead code detection is a hard problem with many edge cases (reflection, serialization, generated code). Use exclude=<regex> to filter out packages or classes you know are not dead."

    fun format(dead: List<DeadCode>): String {
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
            append(NOTE)
        }
    }
}
