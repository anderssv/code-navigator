package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat

object RenameParamFormatter {

    private const val COMPILE_RECOMMENDATION = "Compile to verify all call sites were updated."

    fun format(result: RenameResult, config: RenameParamConfig): String =
        when (config.format) {
            OutputFormat.TEXT -> formatText(result, config)
            OutputFormat.JSON -> formatJson(result, config)
            OutputFormat.LLM -> formatLlm(result, config)
        }

    private fun formatText(result: RenameResult, config: RenameParamConfig): String {
        if (result.changes.isEmpty()) return "No changes needed."

        val mode = if (config.apply) "Applied" else "Preview"
        val header = "$mode: rename ${config.className}.${config.methodName} param '${config.paramName}' -> '${config.newName}' (${result.changes.size} file${if (result.changes.size != 1) "s" else ""})"

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
            for (candidate in result.cascadeCandidates) {
                appendLine("Consider cascading rename: ${candidate.className}.${candidate.methodName} param '${candidate.paramName}'")
            }
        }.trimEnd()
    }

    private fun formatJson(result: RenameResult, config: RenameParamConfig): String {
        val preview = !config.apply
        val changesJson = if (result.changes.isEmpty()) {
            "[]"
        } else {
            result.changes.joinToString(",", "[", "]") { change ->
                val escapedPath = escapeJson(change.filePath)
                val diffLines = computeDiff(change.before, change.after)
                val diffJson = diffLines.joinToString(",", "[", "]") { "\"${escapeJson(it)}\"" }
                """{"filePath":"$escapedPath","diff":$diffJson}"""
            }
        }
        val recommendationJson = if (config.apply) ""","recommendation":"${escapeJson(COMPILE_RECOMMENDATION)}"""" else ""
        val cascadeJson = if (result.cascadeCandidates.isNotEmpty()) {
            val candidates = result.cascadeCandidates.joinToString(",", "[", "]") { c ->
                """{"className":"${escapeJson(c.className)}","methodName":"${escapeJson(c.methodName)}","paramName":"${escapeJson(c.paramName)}"}"""
            }
            ""","cascadeCandidates":$candidates"""
        } else {
            ""
        }
        return """{"preview":$preview,"param":"${escapeJson(config.paramName)}","newName":"${escapeJson(config.newName)}","changes":$changesJson$recommendationJson$cascadeJson}"""
    }

    private fun formatLlm(result: RenameResult, config: RenameParamConfig): String {
        if (result.changes.isEmpty()) return "No changes needed."

        val mode = if (config.apply) "applied" else "preview"
        val header = "rename-param ${config.paramName} -> ${config.newName} in ${config.className}.${config.methodName} ($mode)"

        return buildString {
            appendLine(header)
            for (change in result.changes) {
                val fileName = change.filePath.substringAfterLast("/")
                val changedLineCount = computeDiff(change.before, change.after).size
                appendLine("  $fileName name -> ${config.newName} lines=$changedLineCount")
            }
            if (config.apply) {
                appendLine(COMPILE_RECOMMENDATION)
            }
            for (candidate in result.cascadeCandidates) {
                appendLine("Consider cascading rename: ${candidate.className}.${candidate.methodName} param '${candidate.paramName}'")
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
                bLine == aLine -> {} // unchanged, skip
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

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
