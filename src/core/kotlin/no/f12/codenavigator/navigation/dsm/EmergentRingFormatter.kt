package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.PackageName

object EmergentRingFormatter {

    fun format(result: ClassRingAssignment): String {
        val sb = StringBuilder()

        sb.appendLine("## Emergent Rings (class-level)")
        sb.appendLine()

        // Group classes by ring
        val byRing = result.classRings.entries.groupBy { it.value }.toSortedMap()
        for ((ring, classes) in byRing) {
            val label = when (ring) {
                0 -> "domain"
                else -> "ring $ring"
            }
            sb.appendLine("Ring $ring ($label): ${classes.size} classes")
            for (entry in classes.sortedBy { it.key }) {
                sb.appendLine("  ${entry.key}")
            }
            sb.appendLine()
        }

        // Mixed-ring packages
        val mixedPackages = result.packageSummary
            .filter { it.value.isMixedRing }
            .toSortedMap()

        if (mixedPackages.isNotEmpty()) {
            sb.appendLine("## Mixed-ring packages (${mixedPackages.size})")
            sb.appendLine()
            sb.appendLine("Packages containing classes at multiple rings. This is expected in package-by-feature architectures.")
            sb.appendLine("For stronger enforcement, consider ring subpackages (e.g., feature/domain/, feature/adapters/) —")
            sb.appendLine("this gives better tool-enforced boundaries but may not be worth it for smaller projects.")
            sb.appendLine()
            for ((pkg, summary) in mixedPackages) {
                sb.appendLine("$pkg (rings ${summary.minRing}–${summary.maxRing}):")
                for ((ring, classes) in summary.classesByRing.toSortedMap()) {
                    val ringLabel = if (ring == 0) "domain" else "ring $ring"
                    sb.appendLine("  $ringLabel: ${classes.sorted().joinToString(", ") { it.simpleName() }}")
                }
                sb.appendLine()
            }
        }

        if (result.violations.isNotEmpty()) {
            sb.appendLine("## Violations (${result.violations.size})")
            sb.appendLine()
            for (v in result.violations) {
                sb.appendLine("OUTWARD: ${v.sourceClass.simpleName()} (ring ${v.sourceRing}) → ${v.targetClass.simpleName()} (ring ${v.targetRing})")
            }
        }

        return sb.toString().trimEnd()
    }
}

private fun no.f12.codenavigator.navigation.types.ClassName.simpleName(): String {
    val full = this.value
    return full.substringAfterLast(".")
}
