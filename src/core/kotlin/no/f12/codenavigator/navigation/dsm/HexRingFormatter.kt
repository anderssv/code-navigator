package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.types.ClassName

object HexRingFormatter {

    fun format(output: HexRingsOutput, format: OutputFormat): String =
        if (format == OutputFormat.JSON) formatJson(output) else formatText(output)

    private fun formatText(output: HexRingsOutput): String = buildString {
        val layering = output.layering

        when (layering.diagnosis) {
            RingDiagnosis.NO_CLASSES -> {
                appendLine("No classes found to analyse.")
                return@buildString
            }
            RingDiagnosis.NO_INVERSION_BOUNDARY -> {
                appendLine("Hexagonal rings: 1 (no boundaries)")
                appendLine()
                appendLine("No dependency inversion was found anywhere in this code, so there is no")
                appendLine("hexagon to report: every class sits in a single undifferentiated ring.")
                appendLine("To create a boundary, extract a port — an interface owned by the inside,")
                appendLine("implemented by the class that does the I/O.")
            }
            RingDiagnosis.LAYERED -> {
                appendLine("Hexagonal rings: ${layering.ringCount} (${layering.ringCount - 1} inversion boundaries)")
                appendLine()
                appendRings(output)
            }
        }

        appendCompositionRoots(output)
        appendViolations(output)
        appendNotices(output)
    }

    private fun StringBuilder.appendRings(output: HexRingsOutput) {
        val byRing = output.layering.rings.entries.groupBy({ it.value }, { it.key })
        byRing.keys.sortedDescending().forEach { ring ->
            val label = when (ring) {
                0 -> "ring 0 (innermost)"
                output.layering.ringCount - 1 -> "ring $ring (adapters)"
                else -> "ring $ring"
            }
            appendLine(label)
            byRing.getValue(ring).sortedBy { it.value }.forEach { cls ->
                appendLine("  ${cls.value}${adapterNote(output, cls)}")
            }
            appendLine()
        }
    }

    private fun adapterNote(output: HexRingsOutput, cls: ClassName): String =
        output.graph.adapterReasons[cls]?.let { "  [${reasonLabel(it)}]" } ?: ""

    private fun reasonLabel(reason: AdapterReason): String = when (reason) {
        AdapterReason.CONFIGURED -> "configured as adapter"
        AdapterReason.FRAMEWORK_SIGNATURE -> "names a framework type in its signature"
        AdapterReason.FRAMEWORK_TYPE -> "references a framework type internally"
        AdapterReason.SINK_WITH_EXTERNAL_CALLS -> "calls a library, calls nothing in the project"
        AdapterReason.UNCALLED_ENTRY_POINT -> "calls a library, nothing in the project calls it"
    }

    private fun StringBuilder.appendCompositionRoots(output: HexRingsOutput) {
        val roots = output.graph.compositionRoots
        if (roots.isEmpty()) return
        appendLine("Composition roots (excluded — an assembler is not a ring):")
        roots.sortedBy { it.value }.forEach { appendLine("  ${it.value}") }
        appendLine()
    }

    private fun StringBuilder.appendViolations(output: HexRingsOutput) {
        val violations = output.layering.violations
        if (violations.isEmpty()) {
            if (output.layering.diagnosis == RingDiagnosis.LAYERED) {
                appendLine("No violations: every dependency points inward.")
            }
            return
        }

        appendLine("Violations (${violations.size}) — an inner class reaching outward across a boundary:")
        violations.forEach {
            appendLine(
                "  ${it.sourceClass.value} (ring ${it.sourceRing}) -> " +
                    "${it.targetClass.value} (ring ${it.targetRing})",
            )
        }
        appendLine()
        appendLine("Fix: invert the dependency — put an interface in the inner ring and have the")
        appendLine("outer class implement it.")
    }

    private fun StringBuilder.appendNotices(output: HexRingsOutput) {
        output.unexpectedRingCount?.let {
            appendLine()
            appendLine(
                "NOTE: detected ${output.layering.ringCount} rings but cnav-config.json expected $it. " +
                    "Ring counts move when ports are added or removed — update the pin, or look at " +
                    "which boundary changed.",
            )
        }

        if (output.unhonoured.isNotEmpty()) {
            appendLine()
            appendLine("NOTE: these cnav-config.json directives matched no class and did nothing:")
            output.unhonoured.forEach {
                appendLine("  ${it.kind.name.lowercase()}: ${it.pattern}")
            }
        }

        output.testInvolvement?.let {
            appendLine()
            appendLine("${it.testInvolved} of ${it.total} violation edge(s) touch test sources — try --scope=prod.")
        }
    }

    private fun formatJson(output: HexRingsOutput): String = buildString {
        appendLine("{")
        appendLine("  \"ringCount\": ${output.layering.ringCount},")
        appendLine("  \"diagnosis\": \"${output.layering.diagnosis.name}\",")
        output.expectedRingCount?.let { appendLine("  \"expectedRingCount\": $it,") }
        appendLine("  \"compositionRoots\": ${jsonArray(output.graph.compositionRoots.map { it.value })},")
        appendLine("  \"rings\": {")
        val byRing = output.layering.rings.entries.groupBy({ it.value }, { it.key })
        byRing.keys.sorted().forEachIndexed { index, ring ->
            val comma = if (index == byRing.size - 1) "" else ","
            appendLine("    \"$ring\": ${jsonArray(byRing.getValue(ring).map { it.value })}$comma")
        }
        appendLine("  },")
        appendLine("  \"adapters\": {")
        val adapters = output.graph.adapterReasons.entries.sortedBy { it.key.value }
        adapters.forEachIndexed { index, (cls, reason) ->
            val comma = if (index == adapters.size - 1) "" else ","
            appendLine("    \"${cls.value}\": \"${reason.name}\"$comma")
        }
        appendLine("  },")
        appendLine("  \"violations\": [")
        output.layering.violations.forEachIndexed { index, v ->
            val comma = if (index == output.layering.violations.size - 1) "" else ","
            appendLine(
                "    {\"source\": \"${v.sourceClass.value}\", \"sourceRing\": ${v.sourceRing}, " +
                    "\"target\": \"${v.targetClass.value}\", \"targetRing\": ${v.targetRing}, " +
                    "\"type\": \"${v.type.name}\"}$comma",
            )
        }
        appendLine("  ],")
        appendLine("  \"unhonouredDirectives\": [")
        output.unhonoured.forEachIndexed { index, d ->
            val comma = if (index == output.unhonoured.size - 1) "" else ","
            appendLine("    {\"kind\": \"${d.kind.name}\", \"pattern\": \"${d.pattern}\"}$comma")
        }
        appendLine("  ]")
        append("}")
    }

    private fun jsonArray(values: Collection<String>): String =
        values.sorted().joinToString(", ", "[", "]") { "\"$it\"" }
}
