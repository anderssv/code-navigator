package no.f12.codenavigator.navigation

import java.io.File

object SymbolScanner {
    fun scan(classDirectories: List<File>): ScanResult<List<SymbolInfo>> {
        val symbols = mutableListOf<SymbolInfo>()
        val skipped = mutableListOf<UnsupportedBytecodeVersionException>()

        classDirectories
            .filter { it.exists() }
            .forEach { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .filter { !isSyntheticClassFile(it) }
                    .forEach { classFile ->
                        try {
                            symbols.addAll(SymbolExtractor.extract(classFile))
                        } catch (e: UnsupportedBytecodeVersionException) {
                            skipped.add(e)
                        }
                    }
            }

        return ScanResult(
            data = symbols.sortedWith(compareBy({ it.packageName.value }, { it.className }, { it.symbolName })),
            skippedFiles = skipped,
        )
    }

    private val SYNTHETIC_SUFFIX = Regex("""\$\d+""")
    private val LAMBDA_PATTERN = Regex("""\${'$'}lambda\${'$'}""")

    private fun isSyntheticClassFile(file: File): Boolean {
        val nameWithoutExtension = file.nameWithoutExtension
        return SYNTHETIC_SUFFIX.containsMatchIn(nameWithoutExtension) ||
            LAMBDA_PATTERN.containsMatchIn(nameWithoutExtension)
    }
}
