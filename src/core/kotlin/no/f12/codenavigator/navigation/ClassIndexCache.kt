package no.f12.codenavigator.navigation

import no.f12.codenavigator.CacheFreshness

import java.io.File

object ClassIndexCache {

    private const val FIELD_SEPARATOR = "\t"

    fun write(cacheFile: File, classes: List<ClassInfo>) {
        CacheFreshness.atomicWrite(cacheFile) { file ->
            file.bufferedWriter().use { writer ->
                classes.forEach { info ->
                    writer.write(
                        listOf(
                            info.className,
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

    fun read(cacheFile: File): List<ClassInfo> =
        cacheFile.useLines { lines ->
            lines
                .filter { it.isNotBlank() }
                .map { line ->
                    val parts = line.split(FIELD_SEPARATOR)
                    ClassInfo(
                        className = parts[0],
                        sourceFileName = parts[1],
                        reconstructedSourcePath = parts[2],
                        isUserDefinedClass = parts[3].toBoolean(),
                    )
                }
                .toList()
        }

    fun isFresh(cacheFile: File, classDirectories: List<File>): Boolean =
        CacheFreshness.isFresh(cacheFile, classDirectories)

    fun getOrScan(cacheFile: File, classDirectories: List<File>): ScanResult<List<ClassInfo>> {
        if (isFresh(cacheFile, classDirectories)) {
            try {
                return ScanResult(read(cacheFile), emptyList())
            } catch (_: Exception) {
                cacheFile.delete()
            }
        }

        val result = ClassScanner.scan(classDirectories)
        write(cacheFile, result.data)
        return result
    }
}
