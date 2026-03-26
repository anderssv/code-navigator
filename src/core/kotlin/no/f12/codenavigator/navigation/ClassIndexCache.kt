package no.f12.codenavigator.navigation

import no.f12.codenavigator.CacheFreshness
import java.io.File

object ClassIndexCache : FileCache<List<ClassInfo>>() {

    override fun write(cacheFile: File, data: List<ClassInfo>) {
        CacheFreshness.atomicWrite(cacheFile) { file ->
            file.bufferedWriter().use { writer ->
                data.forEach { info ->
                    writer.write(
                        listOf(
                            info.className.toString(),
                            info.sourceFileName,
                            info.reconstructedSourcePath,
                            info.isUserDefinedClass.toString(),
                        ).joinToString(FIELD_SEPARATOR),
                    )
                    writer.newLine()
                }
            }
        }
    }

    override fun read(cacheFile: File): List<ClassInfo> =
        cacheFile.useLines { lines ->
            lines
                .filter { it.isNotBlank() }
                .map { line ->
                    val parts = line.split(FIELD_SEPARATOR)
                    ClassInfo(
                        className = ClassName(parts[0]),
                        sourceFileName = parts[1],
                        reconstructedSourcePath = parts[2],
                        isUserDefinedClass = parts[3].toBoolean(),
                    )
                }
                .toList()
        }

    override fun build(classDirectories: List<File>): ScanResult<List<ClassInfo>> =
        ClassScanner.scan(classDirectories)
}
