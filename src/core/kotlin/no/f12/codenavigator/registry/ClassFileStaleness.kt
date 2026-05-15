package no.f12.codenavigator.registry

import java.io.File

sealed interface StalenessResult {
    data object Fresh : StalenessResult
    data class Stale(val warning: String) : StalenessResult
    data class NoClassFiles(val error: String) : StalenessResult
}

object ClassFileStaleness {

    private val SOURCE_EXTENSIONS = setOf("kt", "java")

    fun check(sourceDirectories: List<File>, classDirectories: List<File>): StalenessResult {
        val newestClass = newestTimestamp(classDirectories, setOf("class"))
            ?: return StalenessResult.NoClassFiles("No class files found — run a successful build first.")

        val newestSource = newestTimestamp(sourceDirectories, SOURCE_EXTENSIONS)
            ?: return StalenessResult.Fresh

        if (newestSource > newestClass) {
            return StalenessResult.Stale(
                "Class files may be stale: newest source file is ${formatTimestamp(newestSource)}, " +
                    "newest class file is ${formatTimestamp(newestClass)}. " +
                    "Changes after ${formatTime(newestClass)} are not reflected."
            )
        }

        return StalenessResult.Fresh
    }

    private fun newestTimestamp(directories: List<File>, extensions: Set<String>): Long? {
        var newest: Long? = null
        for (dir in directories) {
            if (!dir.exists()) continue
            dir.walkTopDown()
                .filter { it.isFile && it.extension in extensions }
                .forEach { file ->
                    val mtime = file.lastModified()
                    if (newest == null || mtime > newest!!) newest = mtime
                }
        }
        return newest
    }

    private fun formatTimestamp(millis: Long): String {
        val instant = java.time.Instant.ofEpochMilli(millis)
        val local = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(local)
    }

    private fun formatTime(millis: Long): String {
        val instant = java.time.Instant.ofEpochMilli(millis)
        val local = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
        return java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").format(local)
    }
}
