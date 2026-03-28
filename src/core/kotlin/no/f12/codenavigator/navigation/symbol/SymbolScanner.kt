package no.f12.codenavigator.navigation.symbol

import no.f12.codenavigator.navigation.ClassName
import no.f12.codenavigator.navigation.ScanResult
import no.f12.codenavigator.navigation.UnsupportedBytecodeVersionException
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
            data = symbols.sortedWith(compareBy({ it.packageName.toString() }, { it.className }, { it.symbolName })),
            skippedFiles = skipped,
        )
    }

    private fun isSyntheticClassFile(file: File): Boolean =
        ClassName.isSyntheticName(file.nameWithoutExtension)
}
