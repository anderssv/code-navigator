package no.f12.codenavigator.analysis

import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.withInterpretation

object ChurnFormatter {

    internal const val CHURN_INTERPRETATION = "Interpretation: High added+deleted lines indicate files undergoing significant rework. Files with high churn but few commits may have large, risky changes. Files with steady churn across many commits are actively maintained."

    fun format(churn: List<FileChurn>): String {
        if (churn.isEmpty()) return "No churn data found."

        val fileWidth = maxOf("File".length, churn.maxOf { it.file.length })
        val addedWidth = maxOf("Added".length, churn.maxOf { it.added.toString().length })
        val deletedWidth = maxOf("Deleted".length, churn.maxOf { it.deleted.toString().length })
        val netWidth = maxOf("Net".length, churn.maxOf { (it.added - it.deleted).toString().length })
        val commitsWidth = maxOf("Commits".length, churn.maxOf { it.commits.toString().length })

        return buildString {
            appendLine(
                "%-${fileWidth}s  %${addedWidth}s  %${deletedWidth}s  %${netWidth}s  %${commitsWidth}s".format(
                    "File", "Added", "Deleted", "Net", "Commits",
                ),
            )
            churn.forEachIndexed { index, c ->
                if (index > 0) appendLine()
                append(
                    "%-${fileWidth}s  %${addedWidth}d  %${deletedWidth}d  %${netWidth}d  %${commitsWidth}d".format(
                        c.file, c.added, c.deleted, c.added - c.deleted, c.commits,
                    ),
                )
            }
        }
    }

    fun formatJson(churn: List<FileChurn>): String =
        jsonArray(churn) { c ->
            jsonObject(
                "file" to c.file,
                "added" to c.added,
                "deleted" to c.deleted,
                "commits" to c.commits,
            )
        }

    fun formatLlm(churn: List<FileChurn>): String =
        churn.joinToString("\n") { "${it.file} added=${it.added} deleted=${it.deleted} commits=${it.commits}" }
            .withInterpretation(CHURN_INTERPRETATION)
}
