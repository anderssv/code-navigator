package no.f12.codenavigator.navigation.changedsince

object ChangedSinceFormatter {

    fun format(impacts: List<ChangedClassImpact>, unresolved: List<String>): String = buildString {
        if (impacts.isEmpty() && unresolved.isEmpty()) {
            append("No changed files found.")
            return@buildString
        }

        if (impacts.isNotEmpty()) {
            appendLine("Changed classes and their callers:")
            appendLine()
            impacts.forEach { impact ->
                val callerCount = impact.callers.size
                val callerLabel = if (callerCount == 1) "1 caller" else "$callerCount callers"
                appendLine("  ${impact.className} (${impact.sourceFile}) — $callerLabel")
                if (impact.callers.isEmpty()) {
                    appendLine("    (no callers)")
                } else {
                    impact.callers
                        .sortedBy { "${it.className}.${it.methodName}" }
                        .forEach { caller ->
                            appendLine("    ${caller.className}.${caller.methodName}")
                        }
                }
            }
        }

        if (unresolved.isNotEmpty()) {
            if (impacts.isNotEmpty()) appendLine()
            appendLine("Unresolved files (not mapped to classes):")
            unresolved.forEach { file ->
                appendLine("  $file")
            }
        }
    }.trimEnd()
}
