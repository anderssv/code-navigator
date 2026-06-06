package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.PackageName

object EmergentRingFormatter {

    fun format(result: ClassRingAssignment, ringNames: Map<Int, String> = emptyMap(), hasHints: Boolean = false): String {
        val sb = StringBuilder()

        val ringLabel: (Int) -> String = { ring ->
            ringNames[ring] ?: when (ring) {
                0 -> "domain"
                else -> "ring $ring"
            }
        }

        sb.appendLine("## Emergent Rings (class-level)")
        sb.appendLine()

        // Group classes by ring
        val byRing = result.classRings.entries.groupBy { it.value }.toSortedMap()
        for ((ring, classes) in byRing) {
            val label = ringLabel(ring)
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
                    sb.appendLine("  ${ringLabel(ring)}: ${classes.sorted().joinToString(", ") { it.simpleName() }}")
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

        // Self-documenting tip
        if (hasHints) {
            sb.appendLine()
            sb.appendLine("---")
            sb.appendLine()
            sb.appendLine("**Hints configured via cnav-config.json**")
            sb.appendLine()
            sb.appendLine("Some classes were promoted to higher rings based on hints in `cnav-config.json`.")
            sb.appendLine("To add or adjust hints, edit `cnav-config.json` in the project root:")
            sb.appendLine()
            sb.appendLine("```jsonc")
            sb.appendLine("// cnav-config.json — Code Navigator configuration")
            sb.appendLine("{")
            sb.appendLine("  \"ringNames\": [\"domain\", \"port\", \"application\", \"adapter\"],")
            sb.appendLine("  \"hints\": {")
            sb.appendLine("    \"<ring-name>\": [")
            sb.appendLine("      \"*Pattern\",   // glob matching on simple class name")
            sb.appendLine("    ],")
            sb.appendLine("  },")
            sb.appendLine("  \"overrides\": {")
            sb.appendLine("    \"com.example.Foo\": \"port\",  // FQCN always takes precedence")
            sb.appendLine("  }")
            sb.appendLine("}")
            sb.appendLine("```")
            sb.appendLine()
            sb.appendLine("Ring names above match the indices: 0=domain, 1=port, 2=application, 3=adapter.")
            sb.appendLine("Any class matching the hint will be promoted to (or above) that ring.")
            sb.appendLine("Overrides use fully qualified class names and always take precedence over glob hints.")
        }

        return sb.toString().trimEnd()
    }
}

private fun no.f12.codenavigator.navigation.types.ClassName.simpleName(): String {
    val full = this.value
    return full.substringAfterLast(".")
}
