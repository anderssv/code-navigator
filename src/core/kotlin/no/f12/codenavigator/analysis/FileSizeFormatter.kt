package no.f12.codenavigator.analysis

object FileSizeFormatter {

    fun format(entries: List<FileSizeEntry>): String {
        if (entries.isEmpty()) return "No source files found."

        val fileWidth = maxOf("File".length, entries.maxOf { it.file.length })
        val linesWidth = maxOf("Lines".length, entries.maxOf { it.lines.toString().length })
        val sizeThreshold = sizeThreshold(entries)

        return buildString {
            appendLine("%-${fileWidth}s  %${linesWidth}s".format("File", "Lines"))
            entries.forEachIndexed { index, entry ->
                if (index > 0) appendLine()
                append("%-${fileWidth}s  %${linesWidth}d".format(entry.file, entry.lines))
                if (sizeThreshold != null && entry.lines >= sizeThreshold) {
                    append("  ← Consider splitting — file has many responsibilities.")
                }
            }
        }
    }

    private fun sizeThreshold(entries: List<FileSizeEntry>): Int? {
        if (entries.size < 3) return null
        val sorted = entries.map { it.lines }.sorted()
        val median = sorted[sorted.size / 2]
        val threshold = median * 3
        return if (threshold > median && entries.any { it.lines >= threshold }) threshold else null
    }
}
