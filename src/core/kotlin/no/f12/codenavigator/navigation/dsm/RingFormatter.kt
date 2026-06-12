package no.f12.codenavigator.navigation.dsm

object RingFormatter {

    /**
     * Shown when the user explicitly selects package mode (emergent is the default). Frames the
     * output correctly so it is not misread as a per-package layer assignment.
     */
    const val PACKAGE_MODE_NOTICE =
        "Note: package mode ranks whole packages by topological depth. On package-by-feature layouts " +
            "this can nudge you toward package-by-layer — read the violation list as a cross-feature " +
            "independence check (do features stay independent?), NOT as a per-package layer label. " +
            "cnav-config.json hints/overrides do not apply in package mode; use --mode=emergent for " +
            "class-level layering and calibration."

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
            sb.appendLine("Ring $ring ($label): ${pkgs.size} packages")
            for (pkg in pkgs) {
                val suffix = if (pkg in compositionMark) " [composition root]" else ""
                sb.appendLine("  $pkg$suffix")
            }
            sb.appendLine()
        }

        val filteredViolations = result.violations.filter { v ->
            v.sourcePackage !in result.compositionRoots && v.targetPackage !in result.compositionRoots
        }

        val hints = computeHints(byRing.mapValues { it.value.size })
        if (hints.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("## Notes")
            hints.forEach { sb.appendLine("- $it") }
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

    /**
     * Factual observations about ring count and size distribution.
     * Emitted in all output formats (TEXT, LLM, JSON) so no one misses them.
     */
    internal fun computeHints(ringSizes: Map<Int, Int>): List<String> {
        if (ringSizes.isEmpty()) return emptyList()
        val hints = mutableListOf<String>()
        val total = ringSizes.values.sum()
        val maxRing = ringSizes.keys.max()
        val largestRing = ringSizes.maxByOrNull { it.value }!!
        val ring0Size = ringSizes[0] ?: 0

        // Many rings
        if (maxRing >= 8) {
            hints += "High ring count ($maxRing rings detected). This is normal on package-by-feature layouts " +
                "but can also indicate long dependency chains. Try --scope=prod to remove test inflation, " +
                "then look for ring chains that could be shortened."
        }

        // Outer ring is the largest
        if (largestRing.key > 0 && largestRing.value > ring0Size) {
            hints += "Ring ${largestRing.key} is the largest ring (${largestRing.value} packages) and sits " +
                "above the domain ring (ring 0: $ring0Size packages). This may indicate feature logic " +
                "leaking into adapter/infrastructure layers — consider whether those packages belong closer to the domain."
        }

        // One ring dominates (>60% of total, excluding the domain dominating which is healthy)
        if (largestRing.key > 0 && total > 2 && largestRing.value.toDouble() / total > 0.6) {
            hints += "Ring ${largestRing.key} contains ${largestRing.value} of $total packages (${largestRing.value * 100 / total}%). " +
                "A single dominant outer ring often means many packages share the same layer depth — " +
                "check whether they are genuinely independent or contain hidden coupling (run cnavCycles)."
        }

        return hints
    }
}
