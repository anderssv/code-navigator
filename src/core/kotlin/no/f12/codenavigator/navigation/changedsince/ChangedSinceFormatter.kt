package no.f12.codenavigator.navigation.changedsince

import no.f12.codenavigator.formatting.JsonRaw
import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.jsonStringArray

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

    fun formatJson(impacts: List<ChangedClassImpact>, unresolved: List<String>): String =
        jsonObject(
            "changedClasses" to JsonRaw(jsonArray(impacts) { impact ->
                jsonObject(
                    "className" to impact.className.toString(),
                    "sourceFile" to impact.sourceFile,
                    "callers" to JsonRaw(jsonArray(impact.callers.sortedBy { "${it.className}.${it.methodName}" }) { caller ->
                        jsonObject(
                            "className" to caller.className.toString(),
                            "method" to caller.methodName,
                        )
                    }),
                )
            }),
            "unresolvedFiles" to JsonRaw(jsonStringArray(unresolved)),
        )

    fun formatLlm(impacts: List<ChangedClassImpact>, unresolved: List<String>): String = buildString {
        impacts.forEachIndexed { index, impact ->
            if (index > 0) appendLine()
            append("${impact.className} ${impact.sourceFile}")
            if (impact.callers.isEmpty()) {
                append(" (no callers)")
            } else {
                for (caller in impact.callers.sortedBy { "${it.className}.${it.methodName}" }) {
                    appendLine()
                    append("  <- ${caller.className}.${caller.methodName}")
                }
            }
        }
        if (unresolved.isNotEmpty()) {
            if (impacts.isNotEmpty()) appendLine()
            append("UNRESOLVED: ${unresolved.joinToString(",")}")
        }
    }
}
