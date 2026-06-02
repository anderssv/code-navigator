package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName

object TypeAffinityFormatter {

    fun format(result: TypeAffinityResult, format: OutputFormat): String = when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> formatText(result)
        OutputFormat.JSON -> formatJson(result)
        OutputFormat.LLM -> formatLlm(result)
    }

    private fun formatText(result: TypeAffinityResult): String = buildString {
        if (result.singleOwnerTypes.isNotEmpty()) {
            appendLine("SINGLE-OWNER TYPES (candidates to move):")
            for (entry in result.singleOwnerTypes) {
                val ringNote = if (entry.ringImpact > 0) " (ring drops by ${entry.ringImpact})" else ""
                appendLine("  ${entry.type.shortName()} → owned by: ${entry.ownerPackage.lastSegment()} (${entry.usageCount} usages)$ringNote")
                appendLine("    Move to: ${entry.ownerPackage}")
            }
        }
        if (result.sharedTypes.isNotEmpty()) {
            if (result.singleOwnerTypes.isNotEmpty()) appendLine()
            appendLine("SHARED TYPES (stay in place):")
            for (entry in result.sharedTypes) {
                val consumers = entry.consumerPackages.joinToString(", ") { it.lastSegment() }
                appendLine("  ${entry.type.shortName()} → consumers: $consumers")
            }
        }
    }.trimEnd()

    private fun formatLlm(result: TypeAffinityResult): String = buildString {
        if (result.singleOwnerTypes.isNotEmpty()) {
            appendLine("SINGLE-OWNER (move candidates):")
            for (entry in result.singleOwnerTypes) {
                val ring = if (entry.ringImpact > 0) " ring-drop=${entry.ringImpact}" else ""
                appendLine("${entry.type.shortName()} -> ${entry.ownerPackage.lastSegment()} (${entry.usageCount} refs)$ring")
            }
        }
        if (result.sharedTypes.isNotEmpty()) {
            appendLine("SHARED:")
            for (entry in result.sharedTypes) {
                val consumers = entry.consumerPackages.joinToString(",") { it.lastSegment() }
                appendLine("${entry.type.shortName()} -> $consumers")
            }
        }
    }.trimEnd()

    private fun formatJson(result: TypeAffinityResult): String = buildString {
        appendLine("{")
        appendLine("  \"singleOwner\": [")
        result.singleOwnerTypes.forEachIndexed { i, entry ->
            val comma = if (i < result.singleOwnerTypes.size - 1) "," else ""
            appendLine("    {\"type\":\"${entry.type}\",\"owner\":\"${entry.ownerPackage}\",\"usages\":${entry.usageCount},\"ringImpact\":${entry.ringImpact}}$comma")
        }
        appendLine("  ],")
        appendLine("  \"shared\": [")
        result.sharedTypes.forEachIndexed { i, entry ->
            val comma = if (i < result.sharedTypes.size - 1) "," else ""
            val consumers = entry.consumerPackages.joinToString("\",\"") { it.toString() }
            appendLine("    {\"type\":\"${entry.type}\",\"consumers\":[\"$consumers\"]}$comma")
        }
        appendLine("  ]")
        append("}")
    }

    private fun ClassName.shortName() = simpleName()
    private fun PackageName.lastSegment() = value.substringAfterLast('.')
}
