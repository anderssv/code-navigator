package no.f12.codenavigator.analysis

data class CoupledPair(
    val entity: String,
    val coupled: String,
    val degree: Int,
    val sharedRevs: Int,
    val avgRevs: Int,
    /** True when either file path no longer resolves to a file on disk (rename/delete noise from git --all history). */
    val stale: Boolean = false,
)

object ChangeCouplingBuilder {

    fun build(
        commits: List<GitCommit>,
        minSharedRevs: Int = 5,
        minCoupling: Int = 30,
        maxChangesetSize: Int = 30,
        top: Int = 50,
    ): List<CoupledPair> {
        val fileRevisions = mutableMapOf<String, Int>()
        val pairCounts = mutableMapOf<Pair<String, String>, Int>()

        for (commit in commits) {
            val files = commit.files.map { it.path }.distinct()
            if (files.size > maxChangesetSize) continue

            for (file in files) {
                fileRevisions[file] = (fileRevisions[file] ?: 0) + 1
            }

            for (i in files.indices) {
                for (j in i + 1 until files.size) {
                    val a = minOf(files[i], files[j])
                    val b = maxOf(files[i], files[j])
                    val key = a to b
                    pairCounts[key] = (pairCounts[key] ?: 0) + 1
                }
            }
        }

        return pairCounts
            .filter { (_, shared) -> shared >= minSharedRevs }
            .mapNotNull { (pair, shared) ->
                val revsA = fileRevisions[pair.first] ?: 0
                val revsB = fileRevisions[pair.second] ?: 0
                val avgRevs = (revsA + revsB) / 2
                if (avgRevs == 0) return@mapNotNull null
                val degree = (shared * 100) / avgRevs
                if (degree < minCoupling) return@mapNotNull null
                CoupledPair(pair.first, pair.second, degree, shared, avgRevs)
            }
            .sortedByDescending { it.degree }
            .take(top)
    }
}
