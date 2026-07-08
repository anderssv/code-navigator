package no.f12.codenavigator.analysis

import java.io.File

data class Hotspot(
    val file: String,
    val revisions: Int,
    val totalChurn: Int,
)

object HotspotBuilder {

    /**
     * @param projectDir when provided, a file is only included if it still exists on disk at its
     * final (most-recently-renamed-to) path — otherwise deleted files and stale intermediate rename
     * names would show up alongside their still-existing successor.
     */
    fun build(
        commits: List<GitCommit>,
        minRevs: Int = 1,
        top: Int = 50,
        projectDir: File? = null,
    ): List<Hotspot> {
        val canonicalPathOf = buildCanonicalPathMap(commits)
        val fileStats = mutableMapOf<String, MutablePair>()

        for (commit in commits) {
            for (file in commit.files) {
                val canonical = canonicalPathOf[file.path] ?: file.path
                val stats = fileStats.getOrPut(canonical) { MutablePair(0, 0) }
                stats.revisions++
                stats.churn += file.added + file.deleted
            }
        }

        return fileStats
            .filter { (path, stats) -> stats.revisions >= minRevs && (projectDir == null || File(projectDir, path).exists()) }
            .map { (file, stats) -> Hotspot(file, stats.revisions, stats.churn) }
            .sortedByDescending { it.revisions }
            .take(top)
    }

    /**
     * Maps every historical path that was ever renamed away from to the final path it (transitively)
     * ended up at, so a file's revisions/churn aren't split across its old and new names.
     */
    private fun buildCanonicalPathMap(commits: List<GitCommit>): Map<String, String> {
        val renameEdges = mutableMapOf<String, String>()
        for (commit in commits) {
            for (file in commit.files) {
                val from = file.renamedFrom ?: continue
                renameEdges[from] = file.path
            }
        }

        val resolved = mutableMapOf<String, String>()
        fun resolve(path: String): String {
            resolved[path]?.let { return it }
            var current = path
            val seen = mutableSetOf<String>()
            while (seen.add(current)) {
                current = renameEdges[current] ?: break
            }
            resolved[path] = current
            return current
        }

        return renameEdges.keys.associateWith { resolve(it) }
    }

    private class MutablePair(var revisions: Int, var churn: Int)
}
