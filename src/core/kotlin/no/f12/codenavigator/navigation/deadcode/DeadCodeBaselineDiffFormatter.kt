package no.f12.codenavigator.navigation.deadcode
import no.f12.codenavigator.navigation.deadcode.DeadCodeFormatter


object DeadCodeBaselineDiffFormatter {

    fun format(diff: DeadCodeDiff): String = buildString {
        appendLine("Baseline comparison: ${diff.removed.size} removed, ${diff.remaining.size} remaining, ${diff.new.size} new")
        appendLine()

        if (diff.removed.isNotEmpty()) {
            appendLine("Removed (${diff.removed.size}):")
            diff.removed.forEach { appendLine("  - ${it.label()}") }
            appendLine()
        }
        if (diff.remaining.isNotEmpty()) {
            appendLine("Remaining (${diff.remaining.size}):")
            diff.remaining.forEach { appendLine("  - ${it.label()}") }
            appendLine()
        }
        if (diff.new.isNotEmpty()) {
            appendLine("New (${diff.new.size}):")
            diff.new.forEach { appendLine("  - ${it.label()}") }
            appendLine()
        }
    }.trimEnd()

    fun formatJson(diff: DeadCodeDiff): String = buildString {
        appendLine("{")
        appendLine("""  "removed": ${DeadCodeFormatter.formatJson(diff.removed)},""")
        appendLine("""  "remaining": ${DeadCodeFormatter.formatJson(diff.remaining)},""")
        append("""  "new": ${DeadCodeFormatter.formatJson(diff.new)}""")
        appendLine()
        append("}")
    }

    private fun DeadCode.label(): String =
        if (memberName != null) "$className.$memberName" else className.toString()
}
