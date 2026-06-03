package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.PackageName

object SimulateMoveFormatter {

    fun formatText(result: SimulateMoveResult, displayPrefix: PackageName = PackageName("")): String = buildString {
        appendLine("Simulating: move ${result.classToMove.simpleName()} → ${result.targetPackage}")
        appendLine()
        appendLine("Cycles BEFORE: ${result.cyclesBefore}")
        appendLine("Cycles AFTER:  ${result.cyclesAfter}")

        if (result.removedCycles.isNotEmpty()) {
            appendLine()
            for (cycle in result.removedCycles) {
                appendLine("✓ REMOVED: ${cycle.packages.joinToString(", ")}")
            }
        }
        if (result.addedCycles.isNotEmpty()) {
            appendLine()
            for (cycle in result.addedCycles) {
                appendLine("✗ ADDED: ${cycle.packages.joinToString(", ")}")
            }
        }
        if (result.removedCycles.isEmpty() && result.addedCycles.isEmpty()) {
            appendLine()
            appendLine("No cycle impact.")
        }
    }.trimEnd()

    fun formatLlm(result: SimulateMoveResult): String = buildString {
        append("simulate-move: ${result.classToMove.simpleName()} -> ${result.targetPackage}")
        append(" cycles:${result.cyclesBefore}->${result.cyclesAfter}")
        if (result.removedCycles.isNotEmpty()) {
            append(" removed:[${result.removedCycles.joinToString(";") { it.packages.joinToString(",") }}]")
        }
        if (result.addedCycles.isNotEmpty()) {
            append(" added:[${result.addedCycles.joinToString(";") { it.packages.joinToString(",") }}]")
        }
    }
}
