package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.formatting.JsonRaw
import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.jsonStringArray

/** What call-paths searched for, plus what it found. */
data class CallPathReport(
    val result: CallPathResult,
    val targets: List<MethodRef>,
    val guard: String?,
    val maxDepth: Int,
) {
    val anyPathFound: Boolean get() = result.reaching.isNotEmpty() || result.guardedEntries.isNotEmpty()
}

object CallPathFormatter {

    private const val MAX_TARGETS_IN_HEADER = 3

    const val GUARD_NOTE = "The guard check ignores call order and conditions: a method that calls the guard anywhere counts as guarded."
    const val NO_PATH_NOTE = "No path is not proof: reflection, dependency-injection wiring and coroutine bodies can hide calls from the call graph."
    const val TRUNCATED_NOTE = "More entry points reach the target than are listed; raise top to see them."

    /** Notes shared by every output format, so JSON consumers get the same caveats as readers of text. */
    fun notes(report: CallPathReport): List<String> = buildList {
        if (!report.anyPathFound) add(NO_PATH_NOTE)
        if (report.guard != null && report.anyPathFound) add(GUARD_NOTE)
        if (report.result.truncated) add(TRUNCATED_NOTE)
    }

    fun formatText(report: CallPathReport): String = buildString {
        val result = report.result
        val searched = listOfNotNull(
            report.guard?.let { "guard: $it" },
            "${result.entryCount} entry points searched",
            "maxdepth ${report.maxDepth}",
        ).joinToString(", ")
        val title = when {
            result.reaching.isNotEmpty() && report.guard != null -> "Unguarded call paths"
            result.reaching.isNotEmpty() -> "Call paths"
            report.anyPathFound -> "No unguarded call path"
            else -> "No call path"
        }
        append("$title to ${targetsHeader(report.targets)} ($searched)")

        for (entry in result.reaching) {
            entry.paths.forEachIndexed { index, path ->
                appendLine()
                appendLine()
                append(path.first().let { "${it.method.qualifiedName}${textLocation(it)}" })
                for (step in path.drop(1)) {
                    appendLine()
                    append("  → ${step.method.qualifiedName}${textLocation(step)}${step.via?.let { " via $it" } ?: ""}")
                }
                if (entry.morePaths && index == entry.paths.lastIndex) {
                    appendLine()
                    append("  … more paths from this entry point not shown")
                }
            }
        }

        if (result.guardedEntries.isNotEmpty()) {
            appendLine()
            appendLine()
            append("Guarded on every path (${result.guardedEntries.size}):")
            for (entry in result.guardedEntries) {
                appendLine()
                append("  ${entry.qualifiedName}")
            }
        }

        val notes = notes(report)
        if (notes.isNotEmpty()) {
            appendLine()
            for (note in notes) {
                appendLine()
                append("Note: $note")
            }
        }
    }

    fun formatJson(report: CallPathReport): String {
        val result = report.result
        return jsonObject(
            "targets" to JsonRaw(jsonStringArray(report.targets.map { it.qualifiedName })),
            "guard" to report.guard,
            "maxDepth" to report.maxDepth,
            "entryCount" to result.entryCount,
            "entries" to JsonRaw(
                jsonArray(result.reaching) { entry ->
                    jsonObject(
                        "entry" to entry.entry.qualifiedName,
                        "morePaths" to entry.morePaths,
                        "paths" to JsonRaw(jsonArray(entry.paths) { path -> jsonArray(path, ::stepJson) }),
                    )
                },
            ),
            "guardedEntries" to JsonRaw(jsonStringArray(result.guardedEntries.map { it.qualifiedName })),
            "truncated" to result.truncated,
            "notes" to JsonRaw(jsonStringArray(notes(report))),
        )
    }

    fun formatLlm(report: CallPathReport): String = buildString {
        val result = report.result
        val header = listOfNotNull(
            "call-paths",
            "to=${report.targets.joinToString(",") { it.qualifiedName }}",
            report.guard?.let { "guard=$it" },
            "entries=${result.entryCount}",
            "maxdepth=${report.maxDepth}",
        ).joinToString(" ")
        append(header)

        val marker = if (report.guard != null) "UNGUARDED " else ""
        for (entry in result.reaching) {
            for (path in entry.paths) {
                appendLine()
                append(marker + path.joinToString(" → ") { step ->
                    step.method.qualifiedName + llmLocation(step) + (step.via?.let { " [via $it]" } ?: "")
                })
            }
            if (entry.morePaths) {
                appendLine()
                append("(more paths from ${entry.entry.qualifiedName} not shown)")
            }
        }
        if (result.reaching.isEmpty()) {
            appendLine()
            append(if (report.anyPathFound) "(no unguarded paths)" else "(no paths)")
        }
        for (entry in result.guardedEntries) {
            appendLine()
            append("GUARDED ${entry.qualifiedName}")
        }
        for (note in notes(report)) {
            appendLine()
            append("note: $note")
        }
    }

    private fun targetsHeader(targets: List<MethodRef>): String {
        val shown = targets.take(MAX_TARGETS_IN_HEADER).joinToString(", ") { it.qualifiedName }
        val hidden = targets.size - MAX_TARGETS_IN_HEADER
        return if (hidden > 0) "$shown +$hidden more" else shown
    }

    private fun stepJson(step: PathStep): String = jsonObject(
        "method" to step.method.qualifiedName,
        "sourceFile" to step.sourceFile,
        "lineNumber" to step.lineNumber,
        "via" to step.via?.toString(),
    )

    private fun location(step: PathStep): String? =
        step.sourceFile?.let { file -> file + (step.lineNumber?.let { ":$it" } ?: "") }

    private fun textLocation(step: PathStep): String = location(step)?.let { " ($it)" } ?: ""

    private fun llmLocation(step: PathStep): String = location(step)?.let { " $it" } ?: ""
}
