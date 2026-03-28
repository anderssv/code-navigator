package no.f12.codenavigator.navigation.symbol

object SymbolTableFormatter {
    fun format(symbols: List<SymbolInfo>): String {
        if (symbols.isEmpty()) return "No symbols found."

        val headers = listOf("Package", "Class", "Symbol", "Kind", "Source File")
        val rows = symbols.map { listOf(it.packageName.toString(), it.className.simpleName(), it.symbolName, it.kind.name, it.sourceFile) }

        val columnWidths = headers.indices.map { col ->
            maxOf(headers[col].length, rows.maxOf { it[col].length })
        }

        return buildString {
            appendLine(headers.zip(columnWidths).joinToString(" | ") { (h, w) -> h.padEnd(w) })
            appendLine(columnWidths.joinToString(" | ") { "-".repeat(it) })
            for (row in rows) {
                appendLine(row.zip(columnWidths).joinToString(" | ") { (v, w) -> v.padEnd(w) })
            }
            append("\n${symbols.size} symbols found.")
        }
    }
}
