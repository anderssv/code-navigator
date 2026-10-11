package no.f12.codenavigator.navigation.declaration

import no.f12.codenavigator.config.OutputFormat

object DeclarationFormatter {

    fun format(declarations: List<DeclarationInfo>, format: OutputFormat): String = when (format) {
        OutputFormat.JSON -> DeclarationInfo.listToJson(declarations)
        OutputFormat.LLM -> formatLlm(declarations)
        OutputFormat.TEXT, OutputFormat.DIFF -> formatText(declarations)
    }

    private fun formatText(declarations: List<DeclarationInfo>): String {
        if (declarations.isEmpty()) return "No declarations found."

        val headers = listOf("Kind", "Declaration", "Lines", "Name@", "File")
        val rows = declarations.map {
            listOf(it.kind.label, it.fqn, "${it.startLine}-${it.endLine}", it.nameLine.toString(), it.sourcePath)
        }
        val widths = headers.indices.map { col -> maxOf(headers[col].length, rows.maxOf { it[col].length }) }

        return buildString {
            appendLine(headers.zip(widths).joinToString(" | ") { (h, w) -> h.padEnd(w) })
            appendLine(widths.joinToString(" | ") { "-".repeat(it) })
            for (row in rows) appendLine(row.zip(widths).joinToString(" | ") { (v, w) -> v.padEnd(w) })
            append("\n${declarations.size} declarations found.")
        }
    }

    private fun formatLlm(declarations: List<DeclarationInfo>): String = buildString {
        for (d in declarations) {
            appendLine("${d.kind.label} ${d.fqn}")
            appendLine("  signature: ${d.signature}")
            appendLine("  file: ${d.file}")
            appendLine("  lines: ${d.startLine}:${d.startColumn}-${d.endLine}:${d.endColumn} (name on line ${d.nameLine}), offsets ${d.startOffset}-${d.endOffset}")
        }
        appendLine()
        appendLine("Ranges cover the whole declaration including KDoc and annotations, through the closing brace.")
        append("Lines/columns are 1-based; offsets are 0-based with endOffset exclusive, over LF-normalised content.")
    }
}
