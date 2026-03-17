package no.f12.codenavigator

import java.io.File

object ClassIndexCache {

    private const val FIELD_SEPARATOR = "\t"

    fun write(cacheFile: File, classes: List<ClassInfo>) {
        cacheFile.parentFile?.mkdirs()
        cacheFile.bufferedWriter().use { writer ->
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

    fun isFresh(cacheFile: File, classDirectories: List<File>): Boolean {
        if (!cacheFile.exists()) return false

        val cacheLastModified = cacheFile.lastModified()

        return classDirectories
            .filter { it.exists() }
            .flatMap { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .toList()
            }
            .all { it.lastModified() <= cacheLastModified }
    }
}
