package no.f12.codenavigator.navigation

import no.f12.codenavigator.CacheFreshness
import java.io.BufferedWriter
import java.io.File

abstract class FileCache<T> {

    protected val FIELD_SEPARATOR = "\t"

    abstract fun write(cacheFile: File, data: T)
    abstract fun read(cacheFile: File): T
    protected abstract fun build(classDirectories: List<File>): ScanResult<T>

    fun isFresh(cacheFile: File, classDirectories: List<File>): Boolean =
        CacheFreshness.isFresh(cacheFile, classDirectories)

    fun getOrBuild(cacheFile: File, classDirectories: List<File>): ScanResult<T> {
        if (isFresh(cacheFile, classDirectories)) {
            try {
                return ScanResult(read(cacheFile), emptyList())
            } catch (_: Exception) {
                cacheFile.delete()
            }
        }

        val result = build(classDirectories)
        write(cacheFile, result.data)
        return result
    }

    protected fun writeLines(cacheFile: File, writeContent: (BufferedWriter) -> Unit) {
        CacheFreshness.atomicWrite(cacheFile) { file ->
            file.bufferedWriter().use(writeContent)
        }
    }

    protected fun <R> readLines(cacheFile: File, parseLine: (List<String>) -> R): List<R> =
        cacheFile.useLines { lines ->
            lines
                .filter { it.isNotBlank() }
                .map { parseLine(it.split(FIELD_SEPARATOR)) }
                .toList()
        }
}
