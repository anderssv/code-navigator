package no.f12.codenavigator.navigation.refactor

fun computeDiff(before: String, after: String): List<String> {
    val beforeLines = before.lines()
    val afterLines = after.lines()
    val diff = mutableListOf<String>()

    val maxLines = maxOf(beforeLines.size, afterLines.size)
    for (i in 0 until maxLines) {
        val bLine = beforeLines.getOrNull(i)
        val aLine = afterLines.getOrNull(i)
        when {
            bLine == aLine -> {}
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

/**
 * Produces a standard unified diff suitable for `git apply` or `patch -p1`.
 * Uses LCS-based diff with configurable context lines (default 3).
 */
fun computeUnifiedDiff(
    filePath: String,
    before: String,
    after: String,
    contextLines: Int = 3,
): String {
    val beforeLines = before.lines()
    val afterLines = after.lines()

    val editScript = computeEditScript(beforeLines, afterLines)
    if (editScript.all { it.type == EditType.EQUAL }) return ""

    val hunks = groupIntoHunks(editScript, contextLines)
    if (hunks.isEmpty()) return ""

    return buildString {
        appendLine("--- a/$filePath")
        appendLine("+++ b/$filePath")
        for (hunk in hunks) {
            appendLine(hunk.header)
            for (line in hunk.lines) {
                appendLine(line)
            }
        }
    }.trimEnd()
}

internal data class UnifiedHunk(val header: String, val lines: List<String>)

private sealed class Edit(val type: EditType) {
    class Equal(val line: String, val oldIdx: Int, val newIdx: Int) : Edit(EditType.EQUAL)
    class Delete(val line: String, val oldIdx: Int) : Edit(EditType.DELETE)
    class Insert(val line: String, val newIdx: Int) : Edit(EditType.INSERT)
}

private enum class EditType { EQUAL, DELETE, INSERT }

private fun computeEditScript(before: List<String>, after: List<String>): List<Edit> {
    val m = before.size
    val n = after.size

    // LCS table
    val dp = Array(m + 1) { IntArray(n + 1) }
    for (i in 1..m) {
        for (j in 1..n) {
            dp[i][j] = if (before[i - 1] == after[j - 1]) dp[i - 1][j - 1] + 1
            else maxOf(dp[i - 1][j], dp[i][j - 1])
        }
    }

    // Backtrack
    val edits = mutableListOf<Edit>()
    var i = m
    var j = n
    while (i > 0 || j > 0) {
        when {
            i > 0 && j > 0 && before[i - 1] == after[j - 1] -> {
                edits.add(Edit.Equal(before[i - 1], i - 1, j - 1))
                i--; j--
            }
            j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j]) -> {
                edits.add(Edit.Insert(after[j - 1], j - 1))
                j--
            }
            else -> {
                edits.add(Edit.Delete(before[i - 1], i - 1))
                i--
            }
        }
    }
    edits.reverse()
    return edits
}

private fun groupIntoHunks(edits: List<Edit>, contextLines: Int): List<UnifiedHunk> {
    // Find indices of non-equal edits
    val changeIndices = edits.indices.filter { edits[it].type != EditType.EQUAL }
    if (changeIndices.isEmpty()) return emptyList()

    // Group changes into hunks with context, merging overlapping ranges
    data class Range(val start: Int, val end: Int)

    val ranges = mutableListOf<Range>()
    var currentStart = maxOf(0, changeIndices.first() - contextLines)
    var currentEnd = minOf(edits.size - 1, changeIndices.first() + contextLines)

    for (idx in 1 until changeIndices.size) {
        val expandedStart = maxOf(0, changeIndices[idx] - contextLines)
        val expandedEnd = minOf(edits.size - 1, changeIndices[idx] + contextLines)

        if (expandedStart <= currentEnd + 1) {
            currentEnd = expandedEnd
        } else {
            ranges.add(Range(currentStart, currentEnd))
            currentStart = expandedStart
            currentEnd = expandedEnd
        }
    }
    ranges.add(Range(currentStart, currentEnd))

    // Build hunks from ranges
    return ranges.map { range ->
        val hunkEdits = edits.subList(range.start, range.end + 1)
        val lines = hunkEdits.map { edit ->
            when (edit) {
                is Edit.Equal -> " ${edit.line}"
                is Edit.Delete -> "-${edit.line}"
                is Edit.Insert -> "+${edit.line}"
            }
        }

        // Calculate old/new line numbers and counts
        val firstOldIdx = hunkEdits.filterIsInstance<Edit.Equal>().firstOrNull()?.oldIdx
            ?: hunkEdits.filterIsInstance<Edit.Delete>().firstOrNull()?.oldIdx
            ?: 0
        val firstNewIdx = hunkEdits.filterIsInstance<Edit.Equal>().firstOrNull()?.newIdx
            ?: hunkEdits.filterIsInstance<Edit.Insert>().firstOrNull()?.newIdx
            ?: 0
        val oldCount = hunkEdits.count { it.type == EditType.EQUAL || it.type == EditType.DELETE }
        val newCount = hunkEdits.count { it.type == EditType.EQUAL || it.type == EditType.INSERT }

        UnifiedHunk(
            header = "@@ -${firstOldIdx + 1},$oldCount +${firstNewIdx + 1},$newCount @@",
            lines = lines,
        )
    }
}

/**
 * Formats a list of RenameChange as concatenated unified diffs.
 * Raw output with no headers or markers — suitable for `git apply`.
 */
fun formatChangesAsUnifiedDiff(changes: List<RenameChange>): String {
    if (changes.isEmpty()) return ""
    return changes.mapNotNull { change ->
        val diff = computeUnifiedDiff(change.filePath, change.before, change.after)
        diff.ifEmpty { null }
    }.joinToString("\n")
}
