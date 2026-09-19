package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat

object MoveClassFormatter {

    private const val COMPILE_RECOMMENDATION = "IMPORTANT: Automated refactoring is not always fully accurate. Compile the project to verify all references were updated correctly."

    private fun StringBuilder.appendWarnings(result: MoveClassResult) {
        if (result.warnings.isNotEmpty()) {
            appendLine()
            for (warning in result.warnings) {
                appendLine(warning)
            }
        }
    }

    fun format(result: MoveClassResult, config: MoveClassConfig): String {
        if (result.error != null) return when (config.format) {
            OutputFormat.TEXT, OutputFormat.LLM, OutputFormat.DIFF -> result.error
            OutputFormat.JSON -> """{"error":"${jsonEscape(result.error)}"}"""
        }
        return when (config.format) {
            OutputFormat.TEXT -> formatText(result, config)
            OutputFormat.JSON -> formatJson(result, config)
            OutputFormat.LLM -> formatLlm(result, config)
            OutputFormat.DIFF -> formatDiff(result)
        }
    }

    private fun formatDiff(result: MoveClassResult): String =
        formatChangesAsUnifiedDiff(result.changes, result.renamedPaths()).ifEmpty { "No changes needed." }

    /**
     * The file this operation relocated on disk, as old path -> new path. `cnavMoveClass` moves the
     * file itself, but nothing in the output used to say so — a follow-up `git mv` was attempted on
     * an already-moved file. Rendered as a summary line and as the diff's `---`/`+++` pair.
     */
    private fun MoveClassResult.renamedPaths(): Map<String, String> {
        val from = movedFilePath ?: return emptyMap()
        val to = newFilePath ?: return emptyMap()
        if (from == to) return emptyMap()
        return mapOf(from to to)
    }

    private fun StringBuilder.appendFileMoveLine(result: MoveClassResult, preview: Boolean) {
        val (from, to) = result.renamedPaths().entries.singleOrNull()?.toPair() ?: return
        val verb = if (preview) "file moves" else "file moved"
        appendLine("$verb: $from -> $to")
    }

    private fun operationDescription(config: MoveClassConfig): String {
        val isMove = config.fromPackage != config.toPackage
        val isRename = config.fromSimpleName != config.toSimpleName
        return when {
            isMove && isRename -> "move+rename ${config.from} -> ${config.to}"
            isRename -> "rename ${config.from} -> ${config.toSimpleName}"
            else -> "move ${config.from} -> ${config.toPackage}"
        }
    }

    private fun formatText(result: MoveClassResult, config: MoveClassConfig): String {
        if (result.changes.isEmpty()) return "No changes needed."

        val mode = if (config.preview) "Preview" else "Applied"
        val header = "$mode: ${operationDescription(config)} (${result.changes.size} file${if (result.changes.size != 1) "s" else ""})"

        return buildString {
            appendLine(header)
            appendFileMoveLine(result, config.preview)
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
            appendWarnings(result)
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
        val recommendationJson = if (!config.preview) ""","recommendation":"${jsonEscape(COMPILE_RECOMMENDATION)}"""" else ""
        return """{"preview":${config.preview},"from":"${jsonEscape(config.from)}","to":"${jsonEscape(config.to)}","changes":$changesJson$movedJson$newFileJson$recommendationJson}"""
    }

    private fun formatLlm(result: MoveClassResult, config: MoveClassConfig): String {
        if (result.changes.isEmpty()) return "No changes needed."

        val mode = if (config.preview) "preview" else "applied"
        val header = "move-class ${operationDescription(config)} ($mode, ${result.changes.size} file${if (result.changes.size != 1) "s" else ""})"

        val renamed = result.renamedPaths()
        return buildString {
            appendLine(header)
            appendFileMoveLine(result, config.preview)
            appendLine()
            for (change in result.changes) {
                val diff = computeUnifiedDiff(
                    change.filePath,
                    renamed[change.filePath] ?: change.filePath,
                    change.before,
                    change.after,
                )
                if (diff.isNotEmpty()) {
                    appendLine(diff)
                }
            }
            if (!config.preview) {
                appendLine(COMPILE_RECOMMENDATION)
                append(RefactoringHints.moveClassFollowUp(config.from))
            }
            appendWarnings(result)
        }.trimEnd()
    }

    fun formatFileMove(result: MoveClassResult, config: MoveFileConfig): String =
        when (config.format) {
            OutputFormat.TEXT -> formatFileMoveText(result, config)
            OutputFormat.JSON -> formatFileMoveJson(result, config)
            OutputFormat.LLM -> formatFileMoveLlm(result, config)
            OutputFormat.DIFF -> formatDiff(result)
        }

    private fun formatFileMoveText(result: MoveClassResult, config: MoveFileConfig): String {
        if (result.changes.isEmpty()) return "No changes needed."
        val mode = if (config.preview) "Preview" else "Applied"
        val header = "$mode: move-file ${config.fromFile} -> ${config.toPackage} (${result.changes.size} file${if (result.changes.size != 1) "s" else ""})"
        return buildString {
            appendLine(header)
            appendFileMoveLine(result, config.preview)
            appendLine()
            for (change in result.changes) {
                appendLine("--- ${change.filePath}")
                val diffLines = computeDiff(change.before, change.after)
                for (line in diffLines) { appendLine(line) }
                appendLine()
            }
            if (!config.preview) { appendLine(COMPILE_RECOMMENDATION) }
        }.trimEnd()
    }

    private fun formatFileMoveJson(result: MoveClassResult, config: MoveFileConfig): String {
        val changesJson = if (result.changes.isEmpty()) "[]" else {
            result.changes.joinToString(",", "[", "]") { change ->
                val diffLines = computeDiff(change.before, change.after)
                val diffJson = diffLines.joinToString(",", "[", "]") { "\"${jsonEscape(it)}\"" }
                """{"filePath":"${jsonEscape(change.filePath)}","diff":$diffJson}"""
            }
        }
        val movedJson = result.movedFilePath?.let { ""","movedFilePath":"${jsonEscape(it)}"""" } ?: ""
        val newFileJson = result.newFilePath?.let { ""","newFilePath":"${jsonEscape(it)}"""" } ?: ""
        val recommendationJson = if (!config.preview) ""","recommendation":"${jsonEscape(COMPILE_RECOMMENDATION)}"""" else ""
        return """{"preview":${config.preview},"fromFile":"${jsonEscape(config.fromFile)}","toPackage":"${jsonEscape(config.toPackage)}","changes":$changesJson$movedJson$newFileJson$recommendationJson}"""
    }

    private fun formatFileMoveLlm(result: MoveClassResult, config: MoveFileConfig): String {
        if (result.changes.isEmpty()) return "No changes needed."
        val mode = if (config.preview) "preview" else "applied"
        val header = "move-file ${config.fromFile} -> ${config.toPackage} ($mode, ${result.changes.size} file${if (result.changes.size != 1) "s" else ""})"
        val renamed = result.renamedPaths()
        return buildString {
            appendLine(header)
            appendFileMoveLine(result, config.preview)
            appendLine()
            for (change in result.changes) {
                val diff = computeUnifiedDiff(
                    change.filePath,
                    renamed[change.filePath] ?: change.filePath,
                    change.before,
                    change.after,
                )
                if (diff.isNotEmpty()) { appendLine(diff) }
            }
            if (!config.preview) {
                appendLine(COMPILE_RECOMMENDATION)
                append(RefactoringHints.moveClassFollowUp(config.fromFile))
            }
        }.trimEnd()
    }
}
