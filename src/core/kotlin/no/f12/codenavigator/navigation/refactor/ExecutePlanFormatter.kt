package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat

data class ExecutePlanStepResult(
    val from: String,
    val to: String,
    val result: MoveClassResult,
)

data class ExecutePlanResult(
    val steps: List<ExecutePlanStepResult>,
    val preview: Boolean,
) {
    val totalChanges: Int get() = steps.sumOf { it.result.changes.size }
    val allWarnings: List<String> get() = steps.flatMap { it.result.warnings }
}

object ExecutePlanFormatter {

    private const val COMPILE_RECOMMENDATION = "IMPORTANT: Automated refactoring is not always fully accurate. Compile the project to verify all references were updated correctly."

    fun format(result: ExecutePlanResult, format: OutputFormat): String =
        when (format) {
            OutputFormat.TEXT -> formatText(result)
            OutputFormat.JSON -> formatJson(result)
            OutputFormat.LLM -> formatLlm(result)
            OutputFormat.DIFF -> formatDiff(result)
        }

    private fun formatText(result: ExecutePlanResult): String {
        if (result.steps.isEmpty()) return "No steps in plan."

        val mode = if (result.preview) "Preview" else "Applied"
        return buildString {
            appendLine("$mode: ${result.steps.size} plan step(s), ${result.totalChanges} file(s) changed")
            appendLine()
            for ((index, step) in result.steps.withIndex()) {
                appendLine("Step ${index + 1}: move ${step.from} -> ${step.to}")
                if (step.result.changes.isEmpty()) {
                    appendLine("  No changes needed.")
                } else {
                    appendLine("  ${step.result.changes.size} file(s) modified")
                    for (change in step.result.changes) {
                        appendLine("    ${change.filePath}")
                    }
                }
                appendLine()
            }
            if (!result.preview) {
                appendLine(COMPILE_RECOMMENDATION)
            }
            if (result.allWarnings.isNotEmpty()) {
                appendLine()
                for (warning in result.allWarnings) {
                    appendLine(warning)
                }
            }
        }.trimEnd()
    }

    private fun formatJson(result: ExecutePlanResult): String = buildString {
        appendLine("{")
        appendLine("""  "preview": ${result.preview},""")
        appendLine("""  "totalChanges": ${result.totalChanges},""")
        appendLine("""  "steps": [""")
        for ((index, step) in result.steps.withIndex()) {
            appendLine("    {")
            appendLine("""      "from": "${step.from}",""")
            appendLine("""      "to": "${step.to}",""")
            appendLine("""      "changedFiles": ${step.result.changes.size},""")
            appendLine("""      "files": [${step.result.changes.joinToString(", ") { "\"${it.filePath}\"" }}]""")
            append("    }")
            if (index < result.steps.size - 1) appendLine(",") else appendLine()
        }
        appendLine("  ]")
        append("}")
    }

    private fun formatLlm(result: ExecutePlanResult): String {
        if (result.steps.isEmpty()) return "No steps in plan."

        val mode = if (result.preview) "PREVIEW" else "APPLIED"
        return buildString {
            appendLine("$mode ${result.steps.size} move(s), ${result.totalChanges} file(s) changed")
            for ((index, step) in result.steps.withIndex()) {
                val files = step.result.changes.size
                appendLine("  ${index + 1}. ${step.from} → ${step.to} ($files file${if (files != 1) "s" else ""})")
            }
            if (result.allWarnings.isNotEmpty()) {
                appendLine()
                appendLine("Warnings:")
                for (warning in result.allWarnings) {
                    appendLine("  $warning")
                }
            }
            if (!result.preview) {
                appendLine()
                appendLine(COMPILE_RECOMMENDATION)
            }
        }.trimEnd()
    }

    private fun formatDiff(result: ExecutePlanResult): String =
        result.steps
            .filter { it.result.changes.isNotEmpty() }
            .joinToString("\n") { step ->
                "# move ${step.from} -> ${step.to}\n" +
                    formatChangesAsUnifiedDiff(step.result.changes)
            }
            .ifEmpty { "No changes needed." }
}
