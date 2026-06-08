package no.f12.codenavigator.navigation.dsm

object RingFormatter {

    fun format(result: RingAssignment, ringNames: Map<Int, String> = emptyMap(), configNotice: String? = null): String {
        val sb = StringBuilder()

        val ringLabel: (Int) -> String = { ring ->
            ringNames[ring] ?: when (ring) {
                0 -> "domain"
                else -> "ring $ring"
            }
        }

        // Group packages by ring
        val byRing = result.rings.entries.groupBy { it.value }.toSortedMap()

        sb.appendLine("## Detected Rings (hexagonal)")
        if (configNotice != null) sb.appendLine(configNotice)
        if (ringNames.isNotEmpty()) {
            val maxRing = byRing.keys.maxOrNull() ?: 0
            if (maxRing >= ringNames.size) {
                sb.appendLine("Warning: ringNames covers ${ringNames.size} rings but rings up to $maxRing were detected — rings ${ringNames.size}–$maxRing will use default names.")
            }
        }
        sb.appendLine()
        for ((ring, packages) in byRing) {
            val label = ringLabel(ring)
            val pkgs = packages.map { it.key }.sorted()
            val compositionMark = pkgs.filter { it in result.compositionRoots }.toSet()
            sb.appendLine("Ring $ring ($label):")
            for (pkg in pkgs) {
                val suffix = if (pkg in compositionMark) " [composition root]" else ""
                sb.appendLine("  $pkg$suffix")
            }
            sb.appendLine()
        }

        val filteredViolations = result.violations.filter { v ->
            v.sourcePackage !in result.compositionRoots && v.targetPackage !in result.compositionRoots
        }

        if (filteredViolations.isEmpty()) {
            sb.appendLine("No violations detected.")
        } else {
            sb.appendLine("## Violations (${filteredViolations.size})")
            sb.appendLine()
            for (v in filteredViolations) {
                val typeLabel = when (v.type) {
                    RingViolationType.OUTWARD -> "OUTWARD"
                    RingViolationType.PEER -> "PEER/CYCLE"
                }
                sb.appendLine("$typeLabel: ${v.sourcePackage} (ring ${v.sourceRing}) → ${v.targetPackage} (ring ${v.targetRing})")
            }
        }

        return sb.toString().trimEnd()
    }
}
