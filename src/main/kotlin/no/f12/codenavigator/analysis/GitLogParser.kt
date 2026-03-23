package no.f12.codenavigator.analysis

import java.time.LocalDate

data class FileChange(
    val added: Int,
    val deleted: Int,
    val path: String,
)

data class GitCommit(
    val hash: String,
    val date: LocalDate,
    val author: String,
    val files: List<FileChange>,
)

object GitLogParser {

    private val COMMIT_HEADER = Regex("^--([^-]+)--([0-9]{4}-[0-9]{2}-[0-9]{2})--(.+)$")

    fun parse(input: String): List<GitCommit> {
        if (input.isBlank()) return emptyList()

        val commits = mutableListOf<GitCommit>()
        var currentHash: String? = null
        var currentDate: LocalDate? = null
        var currentAuthor: String? = null
        var currentFiles = mutableListOf<FileChange>()

        for (line in input.lines()) {
            val headerMatch = COMMIT_HEADER.matchEntire(line)
            if (headerMatch != null) {
                if (currentHash != null) {
                    commits.add(GitCommit(currentHash, currentDate!!, currentAuthor!!, currentFiles.toList()))
                }
                currentHash = headerMatch.groupValues[1]
                currentDate = LocalDate.parse(headerMatch.groupValues[2])
                currentAuthor = headerMatch.groupValues[3]
                currentFiles = mutableListOf()
            } else if (line.isNotBlank() && currentHash != null) {
                val parts = line.split("\t")
                if (parts.size == 3) {
                    val added = parts[0].toIntOrNull() ?: 0
                    val deleted = parts[1].toIntOrNull() ?: 0
                    currentFiles.add(FileChange(added, deleted, resolveRenamePath(parts[2])))
                }
            }
        }

        if (currentHash != null) {
            commits.add(GitCommit(currentHash, currentDate!!, currentAuthor!!, currentFiles.toList()))
        }

        return commits
    }

    private val BRACE_RENAME = Regex("^(.*?)\\{[^}]* => ([^}]*)\\}(.*)$")
    private val FULL_RENAME = Regex("^.+ => (.+)$")

    private fun resolveRenamePath(raw: String): String {
        val braceMatch = BRACE_RENAME.matchEntire(raw)
        if (braceMatch != null) {
            val prefix = braceMatch.groupValues[1]
            val newPart = braceMatch.groupValues[2]
            val suffix = braceMatch.groupValues[3]
            return buildPath(prefix, newPart, suffix)
        }

        val fullMatch = FULL_RENAME.matchEntire(raw)
        if (fullMatch != null) {
            return fullMatch.groupValues[1]
        }

        return raw
    }

    private fun buildPath(vararg segments: String): String =
        segments
            .map { it.trim('/') }
            .filter { it.isNotEmpty() }
            .joinToString("/")
}
