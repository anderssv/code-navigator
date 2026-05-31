package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat

object SafeDeleteFormatter {

    fun format(result: SafeDeleteResult, config: SafeDeleteConfig): String = when (config.format) {
        OutputFormat.JSON -> result.toJson()
        OutputFormat.LLM -> formatLlm(result, config)
        OutputFormat.TEXT, OutputFormat.DIFF -> formatText(result, config)
    }

    private fun formatText(result: SafeDeleteResult, config: SafeDeleteConfig): String = buildString {
        val target = config.methodName?.let { "${config.className}.$it" } ?: config.className
        if (result.deleted) {
            appendLine("Deleted: $target")
            if (config.preview) appendLine("(preview mode — no files modified)")
            for (change in result.changes) {
                appendLine("  Modified: ${change.filePath}")
            }
        } else {
            appendLine("Cannot delete: $target")
            if (result.reason != null) appendLine("  Reason: ${result.reason}")
            if (result.usages.isNotEmpty()) {
                appendLine("  Usages found:")
                for (usage in result.usages.take(10)) {
                    appendLine("    - ${usage.callerClass}.${usage.callerMethod} (${usage.sourceFile})")
                }
                if (result.usages.size > 10) {
                    appendLine("    ... and ${result.usages.size - 10} more")
                }
            }
        }
    }

    private fun formatLlm(result: SafeDeleteResult, config: SafeDeleteConfig): String = buildString {
        val target = config.methodName?.let { "${config.className}.$it" } ?: config.className
        if (result.deleted) {
            appendLine("Successfully deleted `$target`.")
            if (config.preview) appendLine("Preview mode — no files were modified on disk.")
            for (change in result.changes) {
                appendLine("Modified: ${change.filePath}")
            }
        } else {
            appendLine("Cannot safely delete `$target`.")
            if (result.reason != null) appendLine(result.reason)
            if (result.usages.isNotEmpty()) {
                appendLine("Referenced by:")
                for (usage in result.usages.take(10)) {
                    appendLine("  ${usage.callerClass}.${usage.callerMethod} (${usage.sourceFile})")
                }
                if (result.usages.size > 10) {
                    appendLine("  ... and ${result.usages.size - 10} more")
                }
            }
        }
    }
}
