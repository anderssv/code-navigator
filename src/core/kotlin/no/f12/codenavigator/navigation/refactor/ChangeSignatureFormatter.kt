package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat

object ChangeSignatureFormatter {

    fun format(result: ChangeSignatureResult, config: ChangeSignatureConfig): String = when (config.format) {
        OutputFormat.JSON -> result.toJson()
        OutputFormat.LLM -> formatLlm(result, config)
        OutputFormat.TEXT, OutputFormat.DIFF -> formatText(result, config)
    }

    private fun formatText(result: ChangeSignatureResult, config: ChangeSignatureConfig): String = buildString {
        val target = "${config.className}.${config.methodName}"
        if (result.changes.isNotEmpty()) {
            appendLine("Changed signature: $target")
            appendLine("  New params: ${config.params}")
            if (config.preview) appendLine("(preview mode — no files modified)")
            for (change in result.changes) {
                appendLine("  Modified: ${change.filePath}")
            }
        } else {
            appendLine("Cannot change signature: $target")
            if (result.reason != null) appendLine("  Reason: ${result.reason}")
        }
    }

    private fun formatLlm(result: ChangeSignatureResult, config: ChangeSignatureConfig): String = buildString {
        val target = "${config.className}.${config.methodName}"
        if (result.changes.isNotEmpty()) {
            appendLine("# Change Signature: $target")
            appendLine()
            appendLine("New parameters: `${config.params}`")
            if (config.preview) appendLine("*Preview mode — no files modified.*")
            appendLine()
            appendLine("## Modified files")
            for (change in result.changes) {
                appendLine()
                appendLine("### ${change.filePath}")
                appendLine("```kotlin")
                appendLine(change.after)
                appendLine("```")
            }
            if (!config.preview) {
                append(RefactoringHints.changeSignatureFollowUp(config.methodName))
            }
        } else {
            appendLine("# Cannot change signature: $target")
            if (result.reason != null) appendLine(result.reason)
        }
    }
}
