package no.f12.codenavigator.analysis

import java.io.File

/**
 * Marks coupled pairs whose file paths no longer resolve to a file on disk.
 *
 * `cnavCoupling` reads git history with `--all`, so paths from renamed or deleted files (and from
 * other branches) can appear alongside live ones. A pair is marked `stale=true` when either side
 * no longer exists under the project root, so agents can discard rename/delete noise.
 */
object StalePairMarker {

    fun mark(pairs: List<CoupledPair>, projectDir: File): List<CoupledPair> {
        if (pairs.isEmpty()) return pairs
        val existenceCache = HashMap<String, Boolean>()
        fun exists(path: String): Boolean = existenceCache.getOrPut(path) { File(projectDir, path).exists() }

        return pairs.map { pair ->
            val stale = !exists(pair.entity) || !exists(pair.coupled)
            if (stale == pair.stale) pair else pair.copy(stale = stale)
        }
    }
}
