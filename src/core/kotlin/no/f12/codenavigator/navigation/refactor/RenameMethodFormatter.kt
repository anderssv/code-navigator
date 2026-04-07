package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat

object RenameMethodFormatter {

    private const val COMPILE_RECOMMENDATION = "Compile to verify all call sites were updated."

    fun format(result: RenameMethodResult, config: RenameMethodConfig): String =
        when (config.format) {
            OutputFormat.TEXT -> formatText(result, config)
            OutputFormat.JSON -> formatJson(result, config)
            OutputFormat.LLM -> formatLlm(result, config)
        }

    private fun formatText(result: RenameMethodResult, config: RenameMethodConfig): String {
        if (result.changes.isEmpty()) return "No changes needed."

        val mode = if (config.apply) "Applied" else "Preview"
        val header = "$mode: rename ${config.className}.${config.methodName} -> ${config.newName} (${result.changes.size} file${if (result.changes.size != 1) "s" else ""})"

        return buildString {
            appendLine(header)
            appendLine()
            for (change in result.changes) {
                appendLine("--- ${change.filePath}")
                val diffLines = computeDiff(change.before, change.after)
                for (line in diffLines) {
                    appendLine(line)
                }
                appendLine()
            }
            if (config.apply) {
                appendLine(COMPILE_RECOMMENDATION)
            }
        }.trimEnd()
    }

    private fun formatJson(result: RenameMethodResult, config: RenameMethodConfig): String {
        val preview = !config.apply
        val changesJson = if (result.changes.isEmpty()) {
            "[]"
        } else {
            result.changes.joinToString(",", "[", "]") { change ->
                val escapedPath = jsonEscape(change.filePath)
                val diffLines = computeDiff(change.before, change.after)
                val diffJson = diffLines.joinToString(",", "[", "]") { "\"${jsonEscape(it)}\"" }
                """{"filePath":"$escapedPath","diff":$diffJson}"""
            }
        }
        val recommendationJson = if (config.apply) ""","recommendation":"${jsonEscape(COMPILE_RECOMMENDATION)}"""" else ""
        return """{"preview":$preview,"method":"${jsonEscape(config.methodName)}","newName":"${jsonEscape(config.newName)}","changes":$changesJson$recommendationJson}"""
    }

    private fun formatLlm(result: RenameMethodResult, config: RenameMethodConfig): String {
        if (result.changes.isEmpty()) return "No changes needed."

        val mode = if (config.apply) "applied" else "preview"
        val header = "rename-method ${config.methodName} -> ${config.newName} in ${config.className} ($mode)"

        return buildString {
            appendLine(header)
            for (change in result.changes) {
                val fileName = change.filePath.substringAfterLast("/")
                val changedLineCount = computeDiff(change.before, change.after).size
                appendLine("  $fileName ${config.methodName} -> ${config.newName} lines=$changedLineCount")
            }
            if (config.apply) {
                appendLine(COMPILE_RECOMMENDATION)
            }
        }.trimEnd()
    }

    private fun computeDiff(before: String, after: String): List<String> {
        val beforeLines = before.lines()
        val afterLines = after.lines()
        val diff = mutableListOf<String>()

        val maxLines = maxOf(beforeLines.size, afterLines.size)
        for (i in 0 until maxLines) {
            val bLine = beforeLines.getOrNull(i)
            val aLine = afterLines.getOrNull(i)
            when {
                bLine == aLine -> {}
                bLine != null && aLine != null -> {
                    diff.add("- $bLine")
                    diff.add("+ $aLine")
                }
                bLine != null -> diff.add("- $bLine")
                aLine != null -> diff.add("+ $aLine")
            }
        }
        return diff
    }
}
