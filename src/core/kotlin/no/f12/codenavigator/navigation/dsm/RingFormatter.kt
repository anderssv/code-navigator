package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.JsonRaw
import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.jsonStringArray
import no.f12.codenavigator.navigation.types.PackageName

/** Separates a factual observation (shown in all formats) from an action recommendation (LLM only). */
data class RingHint(
    val observation: String,
    val action: String? = null,
)

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

    fun format(
        result: RingAssignment,
        ringNames: Map<Int, String> = emptyMap(),
        configNotice: String? = null,
        format: OutputFormat = OutputFormat.TEXT,
        moduleLabels: Map<PackageName, Set<String>> = emptyMap(),
    ): String {
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
                sb.appendLine("  ${DsmFormatter.labelFor(pkg, moduleLabels)}$suffix")
            }
            sb.appendLine()
        }

        val filteredViolations = result.reportableViolations

        renderHints(sb, computeHints(byRing.mapValues { it.value.size }), format)

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
                sb.appendLine("$typeLabel: ${DsmFormatter.labelFor(v.sourcePackage, moduleLabels)} (ring ${v.sourceRing}) → ${DsmFormatter.labelFor(v.targetPackage, moduleLabels)} (ring ${v.targetRing})")
            }
        }

        return sb.toString().trimEnd()
    }

    fun formatJson(
        result: RingAssignment,
        ringNames: Map<Int, String> = emptyMap(),
        configNotice: String? = null,
        moduleLabels: Map<PackageName, Set<String>> = emptyMap(),
    ): String {
        val ringLabel: (Int) -> String = { ring -> ringNames[ring] ?: if (ring == 0) "domain" else "ring $ring" }

        val ringsJson = jsonArray(result.rings.entries.sortedWith(compareBy({ it.value }, { it.key.toString() }))) { (pkg, ring) ->
            jsonObject(
                "package" to pkg.toString(),
                "ring" to ring,
                "ringName" to ringLabel(ring),
                "isCompositionRoot" to (pkg in result.compositionRoots),
                "modules" to moduleLabels[pkg]?.let { JsonRaw(jsonStringArray(it.sorted())) },
            )
        }

        val violationsJson = jsonArray(result.reportableViolations) { v ->
            jsonObject(
                "type" to v.type.name,
                "sourcePackage" to v.sourcePackage.toString(),
                "targetPackage" to v.targetPackage.toString(),
                "sourceRing" to v.sourceRing,
                "targetRing" to v.targetRing,
                "sourceModules" to moduleLabels[v.sourcePackage]?.let { JsonRaw(jsonStringArray(it.sorted())) },
                "targetModules" to moduleLabels[v.targetPackage]?.let { JsonRaw(jsonStringArray(it.sorted())) },
            )
        }

        return jsonObject(
            "rings" to JsonRaw(ringsJson),
            "violations" to JsonRaw(violationsJson),
            "configNotice" to configNotice,
        )
    }

    /** Appends the Notes section: observations always, actions only for LLM format. */
    internal fun renderHints(sb: StringBuilder, hints: List<RingHint>, format: OutputFormat) {
        if (hints.isEmpty()) return
        sb.appendLine()
        sb.appendLine("## Notes")
        for (hint in hints) {
            val action = if (format == OutputFormat.LLM) hint.action else null
            if (action != null) {
                sb.appendLine("- ${hint.observation} $action")
            } else {
                sb.appendLine("- ${hint.observation}")
            }
        }
    }

    /**
     * Factual observations about ring count and size distribution.
     * observation: shown in all formats. action: shown in LLM only.
     */
    internal fun computeHints(ringSizes: Map<Int, Int>): List<RingHint> {
        if (ringSizes.isEmpty()) return emptyList()
        val hints = mutableListOf<RingHint>()
        val total = ringSizes.values.sum()
        val maxRing = ringSizes.keys.max()
        val largestRing = ringSizes.maxByOrNull { it.value }!!
        val ring0Size = ringSizes[0] ?: 0

        // Many rings
        if (maxRing >= 8) {
            hints += RingHint(
                observation = "High ring count: $maxRing rings detected. Normal on package-by-feature layouts; can also indicate long dependency chains or test-class inflation.",
                action = "Try --scope=prod to remove test inflation. If still high, look for dependency chains that could be shortened.",
            )
        }

        // Outer ring is the largest
        if (largestRing.key > 0 && largestRing.value > ring0Size) {
            hints += RingHint(
                observation = "Ring ${largestRing.key} is the largest (${largestRing.value}) and sits above domain (ring 0: $ring0Size). May indicate feature logic in adapter/infrastructure layers.",
                action = "Check whether packages in ring ${largestRing.key} contain logic that belongs closer to the domain.",
            )
        }

        // One outer ring dominates (>60% of total)
        if (largestRing.key > 0 && total > 2 && largestRing.value.toDouble() / total > 0.6) {
            hints += RingHint(
                observation = "Ring ${largestRing.key} contains ${largestRing.value} of $total packages (${largestRing.value * 100 / total}%). A dominant outer ring may hide coupling.",
                action = "Run cnavCycles to check whether packages in ring ${largestRing.key} are genuinely independent.",
            )
        }

        return hints
    }
}
