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
        appendDomainServiceViolations(output)
        appendPortBypassViolations(output)
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
                appendLine("  ${cls.value}${adapterNote(output, cls)}${serviceTierNote(output, cls)}")
            }
            appendLine()
        }
    }

    private fun serviceTierNote(output: HexRingsOutput, cls: ClassName): String =
        if (cls in output.serviceTier) "  [service tier]" else ""

    private fun adapterNote(output: HexRingsOutput, cls: ClassName): String {
        val reason = output.graph.adapterReasons[cls] ?: return ""
        val evidence = output.graph.adapterEvidence[cls]?.let { " — ${it.value}" } ?: ""
        return "  [${reasonLabel(reason)}$evidence]"
    }

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

        val evidencedTargets = violations.map { it.targetClass }.distinct()
            .mapNotNull { cls -> output.graph.adapterEvidence[cls]?.let { cls to it } }
        if (evidencedTargets.isNotEmpty()) {
            val (exampleClass, exampleEvidence) = evidencedTargets.first()
            appendLine()
            appendLine("If a target above looks miscategorized, check its evidence: the exact external")
            appendLine("type that triggered classification (the `[reason — evidence]` note in the ring")
            appendLine("listing). Example from this run:")
            appendLine("  ${exampleClass.value} was classified because of ${exampleEvidence.value}.")
            appendLine()
            appendLine("- If that type is a general-purpose library with no I/O of its own (dates,")
            appendLine("  serialization annotations, an HTML DSL — not a DB/HTTP/file/queue client),")
            appendLine("  this is likely a code-navigator classification gap, not a real violation:")
            appendLine("    - Running against code-navigator's own source: add the package prefix to")
            appendLine("      AdapterDetector.kt's VALUE_LIBRARY_PACKAGES.")
            appendLine("    - Running against any other project: use the cnav-config.json override below")
            appendLine("      now, and consider filing an issue upstream — a source fix helps every")
            appendLine("      project, a config override only fixes this one.")
            appendLine("- If it's real, project-specific I/O behavior, add a cnav-config.json override")
            appendLine("  instead of ignoring the violation:")
            appendLine()
            appendLine("""    { "rings": { "notAdapters": ["${exampleClass.value}"] } }""")
            appendLine("      — this one class is not an adapter, regardless of what it references.")
            appendLine("""    { "rings": { "valuePackages": ["${packagePrefixOf(exampleEvidence)}"] } }""")
            appendLine("      — this whole package is a pure value/DSL library, never an I/O signal.")
            appendLine("""    { "rings": { "frameworkPackages": ["${packagePrefixOf(exampleEvidence)}"] } }""")
            appendLine("      — this whole package IS an I/O library (an internal client cnav can't know")
            appendLine("        about), so referencing it should always mean adapter.")
        }
    }

    private fun packagePrefixOf(type: ClassName): String {
        val pkg = type.value.substringBeforeLast('.', missingDelimiterValue = type.value)
        return "$pkg."
    }

    private fun StringBuilder.appendDomainServiceViolations(output: HexRingsOutput) {
        val violations = output.domainServiceViolations
        if (violations.isEmpty()) return

        appendLine()
        appendLine("Domain \u2192 service violations (${violations.size}) — a domain class depends directly")
        appendLine("on a port or adapter, or on another class that does (service tier):")
        violations.forEach { appendLine("  ${it.sourceClass.value} -> ${it.targetClass.value}") }
        appendLine()
        appendLine("Fix: service can depend on domain; domain must never depend back on service.")
        appendLine("Pass the needed value in as a parameter, or move the shared logic into the domain.")
    }

    private fun StringBuilder.appendPortBypassViolations(output: HexRingsOutput) {
        val violations = output.portBypassViolations
        if (violations.isEmpty()) return

        appendLine()
        appendLine("Port bypass violations (${violations.size}) — calls a concrete adapter directly")
        appendLine("instead of through the port it implements:")
        violations.forEach {
            appendLine("  ${it.sourceClass.value} -> ${it.targetClass.value} (bypasses ${it.bypassedPort.value})")
        }
        appendLine()
        appendLine("Fix: depend on the port interface instead of the concrete adapter type.")
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
            val evidence = output.graph.adapterEvidence[cls]?.let { ""","evidence":"${it.value}"""" } ?: ""
            appendLine("    \"${cls.value}\": {\"reason\": \"${reason.name}\"$evidence}$comma")
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
        appendLine("  ],")
        appendLine("  \"serviceTier\": ${jsonArray(output.serviceTier.map { it.value })},")
        appendLine("  \"domainServiceViolations\": [")
        output.domainServiceViolations.forEachIndexed { index, v ->
            val comma = if (index == output.domainServiceViolations.size - 1) "" else ","
            appendLine("    {\"source\": \"${v.sourceClass.value}\", \"target\": \"${v.targetClass.value}\"}$comma")
        }
        appendLine("  ],")
        appendLine("  \"portBypassViolations\": [")
        output.portBypassViolations.forEachIndexed { index, v ->
            val comma = if (index == output.portBypassViolations.size - 1) "" else ","
            appendLine(
                "    {\"source\": \"${v.sourceClass.value}\", \"target\": \"${v.targetClass.value}\", " +
                    "\"bypassedPort\": \"${v.bypassedPort.value}\"}$comma",
            )
        }
        appendLine("  ]")
        append("}")
    }

    private fun jsonArray(values: Collection<String>): String =
        values.sorted().joinToString(", ", "[", "]") { "\"$it\"" }
}
