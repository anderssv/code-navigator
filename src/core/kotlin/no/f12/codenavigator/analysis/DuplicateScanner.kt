package no.f12.codenavigator.analysis

import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.types.SourceSet
import java.io.File

object DuplicateScanner {

    private val SOURCE_EXTENSIONS = setOf("kt", "java")

    fun scan(
        taggedSourceRoots: List<Pair<File, SourceSet>>,
        minTokens: Int,
        top: Int,
        scope: Scope = Scope.ALL,
    ): List<DuplicateGroup> {
        val filteredRoots = taggedSourceRoots.filter { (_, sourceSet) -> scope.matchesSourceSet(sourceSet) }

        val tokenizedFiles = mutableListOf<TokenizedFile>()

        for ((rootDir, _) in filteredRoots) {
            if (!rootDir.exists()) continue
            rootDir.walkTopDown()
                .filter { it.isFile && it.extension in SOURCE_EXTENSIONS }
                .forEach { file ->
                    val relativePath = file.relativeTo(rootDir).path
                    val tokens = stripHeaderDeclarations(SourceTokenizer.tokenize(file.readText()))
                    if (tokens.isNotEmpty()) {
                        tokenizedFiles.add(TokenizedFile(relativePath, tokens))
                    }
                }
        }

        return DuplicateDetector.detect(tokenizedFiles, minTokens).take(top)
    }

    /**
     * Drops `package` and `import` declarations before matching. Two files in the same framework
     * importing the same symbols tokenize identically, so on a Ktor/Spring codebase the import
     * headers alone produce a long run of "duplicates" that nobody can act on — they buried the
     * real body-level findings (field report: 14 of 29 blocks on greitt were import headers).
     */
    internal fun stripHeaderDeclarations(tokens: List<SourceToken>): List<SourceToken> {
        val headerLines = mutableSetOf<Int>()
        var previousLine = -1
        for (token in tokens) {
            val startsLine = token.line != previousLine
            previousLine = token.line
            if (startsLine && token.type == SourceTokenType.KEYWORD && (token.text == "import" || token.text == "package")) {
                headerLines.add(token.line)
            }
        }
        if (headerLines.isEmpty()) return tokens
        return tokens.filter { it.line !in headerLines }
    }
}
