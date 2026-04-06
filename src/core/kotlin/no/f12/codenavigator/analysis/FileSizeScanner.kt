package no.f12.codenavigator.analysis

import java.io.File

data class FileSizeEntry(
    val file: String,
    val lines: Int,
)

object FileSizeScanner {

    private val SOURCE_EXTENSIONS = setOf("kt", "java")

    fun scan(
        sourceRoots: List<File>,
        over: Int = 0,
        top: Int = 50,
    ): List<FileSizeEntry> {
        val entries = mutableListOf<FileSizeEntry>()

        for (rootDir in sourceRoots) {
            if (!rootDir.exists()) continue

            rootDir.walkTopDown()
                .filter { it.isFile && it.extension in SOURCE_EXTENSIONS }
                .forEach { file ->
                    val lineCount = file.readLines().size
                    if (lineCount > over) {
                        val relativePath = file.relativeTo(rootDir).path
                        entries.add(FileSizeEntry(relativePath, lineCount))
                    }
                }
        }

        return entries
            .sortedByDescending { it.lines }
            .take(top)
    }
}
