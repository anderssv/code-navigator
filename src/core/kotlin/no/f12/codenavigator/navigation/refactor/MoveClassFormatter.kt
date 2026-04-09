package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat

object MoveClassFormatter {

    private const val COMPILE_RECOMMENDATION = "IMPORTANT: Automated refactoring is not always fully accurate. Compile the project to verify all references were updated correctly."

    fun format(result: MoveClassResult, config: MoveClassConfig): String =
        when (config.format) {
            OutputFormat.TEXT -> formatText(result, config)
            OutputFormat.JSON -> formatJson(result, config)
            OutputFormat.LLM -> formatLlm(result, config)
        }

    private fun operationDescription(config: MoveClassConfig): String {
        val oldPackage = config.className.substringBeforeLast(".")
        val isMove = config.newPackage != oldPackage
        val isRename = config.newName != null
        return when {
            isMove && isRename -> "move+rename ${config.className} -> ${config.newPackage}.${config.newName}"
            isRename -> "rename ${config.className} -> ${config.newName}"
            else -> "move ${config.className} -> ${config.newPackage}"
        }
    }

    private fun formatText(result: MoveClassResult, config: MoveClassConfig): String {
        if (result.changes.isEmpty()) return "No changes needed."

        val mode = if (config.preview) "Preview" else "Applied"
        val header = "$mode: ${operationDescription(config)} (${result.changes.size} file${if (result.changes.size != 1) "s" else ""})"

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

    private fun formatJson(result: MoveClassResult, config: MoveClassConfig): String {
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
        val movedJson = result.movedFilePath?.let { ""","movedFilePath":"${jsonEscape(it)}"""" } ?: ""
        val newFileJson = result.newFilePath?.let { ""","newFilePath":"${jsonEscape(it)}"""" } ?: ""
        val newNameJson = config.newName?.let { ""","newName":"${jsonEscape(it)}"""" } ?: ""
        val recommendationJson = if (!config.preview) ""","recommendation":"${jsonEscape(COMPILE_RECOMMENDATION)}"""" else ""
        return """{"preview":${config.preview},"className":"${jsonEscape(config.className)}","newPackage":"${jsonEscape(config.newPackage)}","changes":$changesJson$movedJson$newFileJson$newNameJson$recommendationJson}"""
    }

    private fun formatLlm(result: MoveClassResult, config: MoveClassConfig): String {
        if (result.changes.isEmpty()) return "No changes needed."

        val mode = if (config.preview) "preview" else "applied"
        val header = "move-class ${operationDescription(config)} ($mode)"

        return buildString {
            appendLine(header)
            for (change in result.changes) {
                val fileName = change.filePath.substringAfterLast("/")
                val changedLineCount = computeDiff(change.before, change.after).size
                appendLine("  $fileName lines=$changedLineCount")
            }
            if (!config.preview) {
                appendLine(COMPILE_RECOMMENDATION)
            }
        }.trimEnd()
    }
}
