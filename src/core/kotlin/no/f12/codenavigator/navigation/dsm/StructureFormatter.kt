package no.f12.codenavigator.navigation.dsm

object StructureFormatter {

    fun formatText(result: StructureResult): String {
        val sb = StringBuilder()
        sb.appendLine("Structural drift: ${result.misplacedCount}/${result.totalClassCount} classes (${formatPercent(result.driftScore)}) cluster outside their current package")
        sb.appendLine()

        for (group in result.groups) {
            sb.appendLine("→ ${group.targetPackage} (${group.classes.size} classes)")
            for (cls in group.classes) {
                sb.appendLine("    ${cls.className.simpleName()} (from ${cls.currentPackage}, confidence ${String.format("%.2f", cls.confidence)})")
            }
            sb.appendLine()
        }

        return sb.toString().trimEnd()
    }

    fun formatLlm(result: StructureResult): String {
        val sb = StringBuilder()
        sb.appendLine("# Structure Suggestions")
        sb.appendLine()
        sb.appendLine("Structural drift: ${result.misplacedCount}/${result.totalClassCount} classes (${formatPercent(result.driftScore)}) cluster outside their current package.")
        sb.appendLine()

        for (group in result.groups) {
            sb.appendLine("## Move to ${group.targetPackage}")
            for (cls in group.classes) {
                sb.appendLine("- `${cls.className.simpleName()}` from `${cls.currentPackage}` (confidence ${String.format("%.2f", cls.confidence)})")
            }
            sb.appendLine()
        }

        sb.appendLine("## Interpretation")
        sb.appendLine("Classes in the same group have stronger dependency affinity to the target package than to their current package. Moving them together reduces coupling and improves cohesion.")

        return sb.toString().trimEnd()
    }

    fun formatJson(result: StructureResult): String {
        val groups = result.groups.joinToString(",") { group ->
            val classes = group.classes.joinToString(",") { cls ->
                """{"className":"${cls.className}","currentPackage":"${cls.currentPackage}","confidence":${cls.confidence}}"""
            }
            """{"targetPackage":"${group.targetPackage}","classes":[$classes]}"""
        }
        return """{"driftScore":${result.driftScore},"totalClassCount":${result.totalClassCount},"misplacedCount":${result.misplacedCount},"groups":[$groups]}"""
    }

    private fun formatPercent(score: Double): String = "${(score * 100).toInt()}%"
}
