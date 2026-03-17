package no.f12.codenavigator

import java.io.File

object SymbolScanner {
    fun scan(classDirectories: List<File>): List<SymbolInfo> =
        classDirectories
            .filter { it.exists() }
            .flatMap { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .filter { !isSyntheticClassFile(it) }
                    .flatMap { SymbolExtractor.extract(it) }
                    .toList()
            }
            .sortedWith(compareBy({ it.packageName }, { it.className }, { it.symbolName }))

    private val SYNTHETIC_SUFFIX = Regex("""\$\d+""")
    private val LAMBDA_PATTERN = Regex("""\${'$'}lambda\${'$'}""")

    private fun isSyntheticClassFile(file: File): Boolean {
        val nameWithoutExtension = file.nameWithoutExtension
        return SYNTHETIC_SUFFIX.containsMatchIn(nameWithoutExtension) ||
            LAMBDA_PATTERN.containsMatchIn(nameWithoutExtension)
    }
}
