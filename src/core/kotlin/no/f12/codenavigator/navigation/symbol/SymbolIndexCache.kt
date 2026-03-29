package no.f12.codenavigator.navigation.symbol

import no.f12.codenavigator.navigation.ClassName
import no.f12.codenavigator.navigation.FileCache
import no.f12.codenavigator.navigation.PackageName
import no.f12.codenavigator.navigation.ScanResult
import java.io.File

object SymbolIndexCache : FileCache<List<SymbolInfo>>() {

    override fun write(cacheFile: File, data: List<SymbolInfo>) {
        writeLines(cacheFile) { writer ->
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

    override fun read(cacheFile: File): List<SymbolInfo> =
        readLines(cacheFile) { parts ->
            SymbolInfo(
                packageName = PackageName(parts[0]),
                className = ClassName(parts[1]),
                symbolName = parts[2],
                kind = SymbolKind.valueOf(parts[3]),
                sourceFile = parts[4],
            )
        }

    override fun build(classDirectories: List<File>): ScanResult<List<SymbolInfo>> =
        SymbolScanner.scan(classDirectories)
}
