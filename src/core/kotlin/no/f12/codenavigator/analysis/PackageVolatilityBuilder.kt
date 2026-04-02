package no.f12.codenavigator.analysis

data class PackageVolatility(
    val packageName: String,
    val revisions: Int,
    val totalChurn: Int,
    val fileCount: Int,
    val avgRevisionsPerFile: Double,
)

data class PackageVolatilityResult(
    val entries: List<PackageVolatility>,
)

object PackageVolatilityBuilder {

    fun build(
        hotspots: List<Hotspot>,
        top: Int = Int.MAX_VALUE,
    ): PackageVolatilityResult {
        val byPackage = mutableMapOf<String, MutableStats>()

        for (hotspot in hotspots) {
            val pkg = FileToPackageMapper.map(hotspot.file) ?: continue
            val stats = byPackage.getOrPut(pkg) { MutableStats() }
            stats.revisions += hotspot.revisions
            stats.totalChurn += hotspot.totalChurn
            stats.fileCount++
        }

        val entries = byPackage
            .map { (pkg, stats) ->
                PackageVolatility(
                    packageName = pkg,
                    revisions = stats.revisions,
                    totalChurn = stats.totalChurn,
                    fileCount = stats.fileCount,
                    avgRevisionsPerFile = stats.revisions.toDouble() / stats.fileCount,
                )
            }
            .sortedByDescending { it.revisions }
            .take(top)

        return PackageVolatilityResult(entries)
    }

    private class MutableStats(
        var revisions: Int = 0,
        var totalChurn: Int = 0,
        var fileCount: Int = 0,
    )
}
