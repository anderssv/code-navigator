package no.f12.codenavigator.navigation.symbol

import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject

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

    fun formatJson(symbols: List<SymbolInfo>): String =
        jsonArray(symbols.sortedWith(compareBy({ it.packageName.toString() }, { it.className.toString() }, { it.symbolName }))) { s ->
            jsonObject(
                "package" to s.packageName.toString(),
                "class" to s.className.simpleName(),
                "symbol" to s.symbolName,
                "kind" to s.kind.name.lowercase(),
                "sourceFile" to s.sourceFile,
            )
        }

    fun formatLlm(symbols: List<SymbolInfo>): String =
        symbols.sortedWith(compareBy({ it.packageName.toString() }, { it.className.toString() }, { it.symbolName }))
            .joinToString("\n") { "${it.className}.${it.symbolName} ${it.kind.name.lowercase()} ${it.sourceFile}" }
}
