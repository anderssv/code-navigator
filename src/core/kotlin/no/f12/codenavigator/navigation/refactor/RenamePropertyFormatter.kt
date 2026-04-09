package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat

object RenamePropertyFormatter {

    private const val COMPILE_RECOMMENDATION = "IMPORTANT: Automated refactoring is not always fully accurate. Compile the project to verify all access sites were updated correctly."

    fun format(result: RenamePropertyResult, config: RenamePropertyConfig): String =
        when (config.format) {
            OutputFormat.TEXT -> formatText(result, config)
            OutputFormat.JSON -> formatJson(result, config)
            OutputFormat.LLM -> formatLlm(result, config)
        }

    private fun formatText(result: RenamePropertyResult, config: RenamePropertyConfig): String {
        if (result.changes.isEmpty()) return "No changes needed."

        val mode = if (config.preview) "Preview" else "Applied"
        val header = "$mode: rename ${config.className}.${config.propertyName} -> ${config.newName} (${result.changes.size} file${if (result.changes.size != 1) "s" else ""})"

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
            if (!config.preview) {
                appendLine(COMPILE_RECOMMENDATION)
            }
        }.trimEnd()
    }

    private fun formatJson(result: RenamePropertyResult, config: RenamePropertyConfig): String {
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
        val recommendationJson = if (!config.preview) ""","recommendation":"${jsonEscape(COMPILE_RECOMMENDATION)}"""" else ""
        return """{"preview":${config.preview},"property":"${jsonEscape(config.propertyName)}","newName":"${jsonEscape(config.newName)}","changes":$changesJson$recommendationJson}"""
    }

    private fun formatLlm(result: RenamePropertyResult, config: RenamePropertyConfig): String {
        if (result.changes.isEmpty()) return "No changes needed."

        val mode = if (config.preview) "preview" else "applied"
        val header = "rename-property ${config.propertyName} -> ${config.newName} in ${config.className} ($mode)"

        return buildString {
            appendLine(header)
            for (change in result.changes) {
                val fileName = change.filePath.substringAfterLast("/")
                val changedLineCount = computeDiff(change.before, change.after).size
                appendLine("  $fileName ${config.propertyName} -> ${config.newName} lines=$changedLineCount")
            }
            if (!config.preview) {
                appendLine(COMPILE_RECOMMENDATION)
            }
        }.trimEnd()
    }
}
