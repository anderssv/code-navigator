package no.f12.codenavigator.navigation

import no.f12.codenavigator.CacheFreshness

import java.io.File

object SymbolIndexCache {

    private const val FIELD_SEPARATOR = "\t"

    fun write(cacheFile: File, symbols: List<SymbolInfo>) {
        CacheFreshness.atomicWrite(cacheFile) { file ->
            file.bufferedWriter().use { writer ->
                symbols.forEach { symbol ->
                    writer.write(
                        listOf(
                            symbol.packageName.toString(),
                            symbol.className.toString(),
                            symbol.symbolName,
                            symbol.kind.name,
                            symbol.sourceFile,
                        ).joinToString(FIELD_SEPARATOR),
                    )
                    writer.newLine()
                }
            }
        }
    }

    fun read(cacheFile: File): List<SymbolInfo> =
        cacheFile.useLines { lines ->
            lines
                .filter { it.isNotBlank() }
                .map { line ->
                    val parts = line.split(FIELD_SEPARATOR)
                    SymbolInfo(
                        packageName = PackageName(parts[0]),
                        className = ClassName(parts[1]),
                        symbolName = parts[2],
                        kind = SymbolKind.valueOf(parts[3]),
                        sourceFile = parts[4],
                    )
                }
                .toList()
        }

    fun isFresh(cacheFile: File, classDirectories: List<File>): Boolean =
        CacheFreshness.isFresh(cacheFile, classDirectories)

    fun getOrScan(cacheFile: File, classDirectories: List<File>): ScanResult<List<SymbolInfo>> {
        if (isFresh(cacheFile, classDirectories)) {
            try {
                return ScanResult(read(cacheFile), emptyList())
            } catch (_: Exception) {
                cacheFile.delete()
            }
        }

        val result = SymbolScanner.scan(classDirectories)
        write(cacheFile, result.data)
        return result
    }
}
