package no.f12.codenavigator.navigation.symbol

object SymbolFilter {
    fun filter(symbols: List<SymbolInfo>, pattern: String): List<SymbolInfo> {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        val isQualified = '.' in pattern
        return symbols.filter { symbol ->
            if (isQualified) {
                symbol.packageName.matches(regex) ||
                    symbol.className.matches(regex) ||
                    regex.containsMatchIn(symbol.symbolName)
            } else {
                regex.containsMatchIn(symbol.symbolName) ||
                    regex.containsMatchIn(symbol.className.simpleName())
            }
        }
    }
}
