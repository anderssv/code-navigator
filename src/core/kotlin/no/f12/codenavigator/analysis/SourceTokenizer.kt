package no.f12.codenavigator.analysis

enum class SourceTokenType {
    IDENTIFIER,
    KEYWORD,
    LITERAL,
    OPERATOR,
}

data class SourceToken(
    val type: SourceTokenType,
    val text: String,
    val line: Int,
)

object SourceTokenizer {

    private val KEYWORDS = setOf(
        // Kotlin & Java shared
        "abstract", "as", "break", "class", "continue", "do", "else", "enum",
        "false", "finally", "for", "fun", "if", "import", "in", "interface",
        "internal", "is", "null", "object", "open", "package", "private",
        "protected", "public", "return", "sealed", "super", "this", "throw",
        "true", "try", "val", "var", "when", "while",
        // Java-specific
        "boolean", "byte", "case", "catch", "char", "const", "default",
        "double", "extends", "final", "float", "goto", "implements",
        "instanceof", "int", "long", "native", "new", "short", "static",
        "strictfp", "switch", "synchronized", "throws", "transient", "void",
        "volatile",
        // Kotlin-specific
        "by", "companion", "constructor", "crossinline", "data", "delegate",
        "dynamic", "field", "file", "get", "init", "inline", "inner",
        "lateinit", "noinline", "operator", "out", "override", "reified",
        "set", "suspend", "tailrec", "typealias", "vararg", "where",
    )

    private val MULTI_CHAR_OPERATORS = listOf(
        "===", "!==", "==", "!=", "<=", ">=", "&&", "||", "->", "::", "+=",
        "-=", "*=", "/=", "%=", "++", "--", "..", "?:", "?.",
    )

    fun tokenize(source: String): List<SourceToken> {
        val tokens = mutableListOf<SourceToken>()
        var line = 1
        var pos = 0

        while (pos < source.length) {
            val ch = source[pos]

            // Newline
            if (ch == '\n') {
                line++
                pos++
                continue
            }

            // Whitespace
            if (ch.isWhitespace()) {
                pos++
                continue
            }

            // Single-line comment
            if (ch == '/' && pos + 1 < source.length && source[pos + 1] == '/') {
                pos = skipToEndOfLine(source, pos)
                continue
            }

            // Block comment
            if (ch == '/' && pos + 1 < source.length && source[pos + 1] == '*') {
                val result = skipBlockComment(source, pos, line)
                pos = result.first
                line = result.second
                continue
            }

            // Triple-quoted string
            if (ch == '"' && pos + 2 < source.length && source[pos + 1] == '"' && source[pos + 2] == '"') {
                val startLine = line
                val result = readTripleQuotedString(source, pos, line)
                tokens.add(SourceToken(SourceTokenType.LITERAL, result.first, startLine))
                pos = result.second
                line = result.third
                continue
            }

            // String literal
            if (ch == '"') {
                val str = readString(source, pos)
                tokens.add(SourceToken(SourceTokenType.LITERAL, str, line))
                pos += str.length
                continue
            }

            // Character literal
            if (ch == '\'') {
                val str = readCharLiteral(source, pos)
                tokens.add(SourceToken(SourceTokenType.LITERAL, str, line))
                pos += str.length
                continue
            }

            // Number literal (including hex 0x, binary 0b, and decimals)
            if (ch.isDigit()) {
                val num = readNumber(source, pos)
                tokens.add(SourceToken(SourceTokenType.LITERAL, num, line))
                pos += num.length
                continue
            }

            // Identifier or keyword
            if (ch.isLetter() || ch == '_') {
                val word = readWord(source, pos)
                val type = if (word in KEYWORDS) SourceTokenType.KEYWORD else SourceTokenType.IDENTIFIER
                tokens.add(SourceToken(type, word, line))
                pos += word.length
                continue
            }

            // Multi-char operators
            val multiOp = MULTI_CHAR_OPERATORS.firstOrNull { source.startsWith(it, pos) }
            if (multiOp != null) {
                tokens.add(SourceToken(SourceTokenType.OPERATOR, multiOp, line))
                pos += multiOp.length
                continue
            }

            // Single-char operators/punctuation
            if (ch in "+-*/%=<>!&|^~?:.;,{}()[]@#") {
                tokens.add(SourceToken(SourceTokenType.OPERATOR, ch.toString(), line))
                pos++
                continue
            }

            // Skip anything else (backtick identifiers, etc.)
            pos++
        }

        return tokens
    }

    private fun readWord(source: String, start: Int): String {
        var end = start + 1
        while (end < source.length && (source[end].isLetterOrDigit() || source[end] == '_')) {
            end++
        }
        return source.substring(start, end)
    }

    private fun readNumber(source: String, start: Int): String {
        var end = start

        // Hex
        if (end + 1 < source.length && source[end] == '0' && (source[end + 1] == 'x' || source[end + 1] == 'X')) {
            end += 2
            while (end < source.length && (source[end].isLetterOrDigit() || source[end] == '_')) end++
            return source.substring(start, end)
        }

        // Binary
        if (end + 1 < source.length && source[end] == '0' && (source[end + 1] == 'b' || source[end + 1] == 'B')) {
            end += 2
            while (end < source.length && (source[end] == '0' || source[end] == '1' || source[end] == '_')) end++
            return source.substring(start, end)
        }

        // Decimal (with optional dot and exponent)
        while (end < source.length && (source[end].isDigit() || source[end] == '_')) end++
        if (end < source.length && source[end] == '.' && end + 1 < source.length && source[end + 1].isDigit()) {
            end++
            while (end < source.length && (source[end].isDigit() || source[end] == '_')) end++
        }
        if (end < source.length && (source[end] == 'e' || source[end] == 'E')) {
            end++
            if (end < source.length && (source[end] == '+' || source[end] == '-')) end++
            while (end < source.length && source[end].isDigit()) end++
        }
        // Suffix (L, f, F, etc.)
        if (end < source.length && source[end] in "lLfFdD") end++

        return source.substring(start, end)
    }

    private fun readString(source: String, start: Int): String {
        var end = start + 1
        while (end < source.length && source[end] != '"') {
            if (source[end] == '\\') end++ // skip escaped char
            end++
        }
        if (end < source.length) end++ // closing quote
        return source.substring(start, end)
    }

    private fun readCharLiteral(source: String, start: Int): String {
        var end = start + 1
        while (end < source.length && source[end] != '\'') {
            if (source[end] == '\\') end++
            end++
        }
        if (end < source.length) end++
        return source.substring(start, end)
    }

    private fun readTripleQuotedString(source: String, start: Int, startLine: Int): Triple<String, Int, Int> {
        var end = start + 3
        var line = startLine
        while (end + 2 < source.length) {
            if (source[end] == '\n') line++
            if (source[end] == '"' && source[end + 1] == '"' && source[end + 2] == '"') {
                end += 3
                return Triple(source.substring(start, end), end, line)
            }
            end++
        }
        // Unterminated — take rest
        end = source.length
        return Triple(source.substring(start, end), end, line)
    }

    private fun skipToEndOfLine(source: String, start: Int): Int {
        var pos = start
        while (pos < source.length && source[pos] != '\n') pos++
        return pos
    }

    private fun skipBlockComment(source: String, start: Int, startLine: Int): Pair<Int, Int> {
        var pos = start + 2
        var line = startLine
        while (pos + 1 < source.length) {
            if (source[pos] == '\n') line++
            if (source[pos] == '*' && source[pos + 1] == '/') {
                return Pair(pos + 2, line)
            }
            pos++
        }
        return Pair(source.length, line)
    }
}
