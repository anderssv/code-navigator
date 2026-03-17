package no.f12.codenavigator

object SymbolFilter {
    fun filter(symbols: List<SymbolInfo>, pattern: String): List<SymbolInfo> {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        return symbols.filter { symbol ->
            regex.containsMatchIn(symbol.packageName) ||
                regex.containsMatchIn(symbol.className) ||
                regex.containsMatchIn(symbol.symbolName) ||
                regex.containsMatchIn(symbol.sourceFile)
        }
    }
}
