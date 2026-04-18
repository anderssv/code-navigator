package no.f12.codenavigator.analysis

import no.f12.codenavigator.navigation.core.Scope
import no.f12.codenavigator.navigation.core.SourceSet
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
                    val tokens = SourceTokenizer.tokenize(file.readText())
                    if (tokens.isNotEmpty()) {
                        tokenizedFiles.add(TokenizedFile(relativePath, tokens))
                    }
                }
        }

        return DuplicateDetector.detect(tokenizedFiles, minTokens).take(top)
    }
}
