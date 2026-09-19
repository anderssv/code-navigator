package no.f12.codenavigator.navigation.cache

import java.io.File

object CacheFreshness {

    /**
     * Directories are checked alongside class files because a *deleted* class leaves no mtime behind:
     * every surviving file is still older than the cache, so a file-only check calls the cache fresh
     * and keeps serving classes that no longer exist. Removing an entry does bump its containing
     * directory's mtime, which is the only cheap signal that a deletion happened. Field-verified
     * failure: a test class deleted after a `cnavTestAlignment` violation kept failing the gate,
     * un-greenable until the cache was wiped by hand.
     */
    fun isFresh(cacheFile: File, classDirectories: List<File>): Boolean {
        if (!cacheFile.exists()) return false

        val cacheLastModified = cacheFile.lastModified()

        return classDirectories
            .filter { it.exists() }
            .flatMap { dir ->
                dir.walkTopDown()
                    .filter { it.isDirectory || (it.isFile && it.extension == "class") }
                    .toList()
            }
            .all { it.lastModified() <= cacheLastModified }
    }

    fun atomicWrite(cacheFile: File, writeContent: (File) -> Unit) {
        cacheFile.parentFile?.mkdirs()
        val tempFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        try {
            writeContent(tempFile)
            tempFile.renameTo(cacheFile)
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }
}
