package no.f12.codenavigator.navigation.symbol

object SymbolFilter {
    fun filter(symbols: List<SymbolInfo>, pattern: String): List<SymbolInfo> {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        return symbols.filter { symbol ->
            symbol.packageName.matches(regex) ||
                symbol.className.matches(regex) ||
                regex.containsMatchIn(symbol.symbolName) ||
                regex.containsMatchIn(symbol.sourceFile)
        }
    }
}
