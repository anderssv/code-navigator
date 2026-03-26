package no.f12.codenavigator.navigation

import no.f12.codenavigator.CacheFreshness
import java.io.File

object SymbolIndexCache : FileCache<List<SymbolInfo>>() {

    override fun write(cacheFile: File, data: List<SymbolInfo>) {
        CacheFreshness.atomicWrite(cacheFile) { file ->
            file.bufferedWriter().use { writer ->
                data.forEach { symbol ->
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

    override fun read(cacheFile: File): List<SymbolInfo> =
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

    override fun build(classDirectories: List<File>): ScanResult<List<SymbolInfo>> =
        SymbolScanner.scan(classDirectories)
}
