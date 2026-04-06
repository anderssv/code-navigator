package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.core.ClassName

object LayerFormatter {

    fun format(result: LayerCheckResult): String = buildString {
        if (result.violations.isEmpty()) {
            append("No layer violations found.")
            if (result.unassignedClasses.isNotEmpty()) {
                appendLine()
                appendLine()
                appendWarnings(result.unassignedClasses)
            }
            return@buildString
        }

        appendLine("${result.violations.size} layer violation(s) found:")
        appendLine()

        for (violation in result.violations) {
            val reason = when (violation.type) {
                ViolationType.OUTWARD -> "${violation.sourceLayer} must not depend on outer layers"
                ViolationType.PEER -> "exceeds peer dependency limit for ${violation.sourceLayer}"
            }
            appendLine("VIOLATION: ${violation.sourceClass} → ${violation.targetClass} ($reason)")
        }

        if (result.unassignedClasses.isNotEmpty()) {
            appendLine()
            appendWarnings(result.unassignedClasses)
        }
    }.trimEnd()

    fun formatInit(
        configPath: String,
        classCount: Int,
        sampleClasses: List<ClassName>,
    ): String = buildString {
        appendLine("Generated $configPath with default pattern-based layer config.")
        appendLine()
        appendLine("$classCount classes found. Sample classes:")
        for (cls in sampleClasses.take(20)) {
            appendLine("  ${cls.simpleName()} (${cls.value})")
        }
        appendLine()
        appendLine("Next steps:")
        appendLine("1. Edit $configPath to define layers by class name patterns.")
        appendLine("   - Outermost layers first (http), innermost (domain) last.")
        appendLine("   - First matching pattern wins.")
        appendLine("   - Each layer may only depend on layers below it.")
        appendLine("   - Peer dependencies (same layer) are forbidden by default; set peerLimit to allow.")
        appendLine("2. Run cnavLayerCheck to verify your architecture against the config.")
        append("3. Commit $configPath to version control.")
    }

    private fun StringBuilder.appendWarnings(unassigned: Set<ClassName>) {
        appendLine("WARNING: ${unassigned.size} class(es) not matched by any layer pattern:")
        for (cls in unassigned.sorted()) {
            appendLine("  $cls")
        }
    }
}
