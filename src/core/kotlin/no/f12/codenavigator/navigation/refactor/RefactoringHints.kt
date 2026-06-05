package no.f12.codenavigator.navigation.refactor

object RefactoringHints {

    fun moveClassFollowUp(from: String): String = buildString {
        appendLine()
        appendLine("### Follow-up")
        appendLine("Verify the structural impact of the move:")
        appendLine("  cnavPackageDeps --scope=prod")
        appendLine("  cnavRings --mode=emergent --scope=prod")
        appendLine("  cnavCycles --scope=prod")
        appendLine()
        appendLine("Check for stale references to the old location:")
        appendLine("  cnavFindUsages --type=\"${from.substringAfterLast('.')}\" --scope=prod")
    }

    fun renameMethodFollowUp(methodName: String, newName: String): String = buildString {
        appendLine()
        appendLine("### Follow-up")
        appendLine("Verify no stale references remain:")
        appendLine("  cnavFindUsages --type=\"$methodName\" --scope=prod")
    }

    fun renamePropertyFollowUp(propertyName: String): String = buildString {
        appendLine()
        appendLine("### Follow-up")
        appendLine("Verify no stale references remain:")
        appendLine("  cnavFindUsages --type=\"$propertyName\" --scope=prod")
    }

    fun safeDeleteFollowUp(): String = buildString {
        appendLine()
        appendLine("### Follow-up")
        appendLine("Check for newly dead code exposed by this deletion:")
        appendLine("  cnavDead --scope=prod")
        appendLine("  cnavFindUsages --scope=prod # verify no broken references")
    }

    fun changeSignatureFollowUp(methodName: String): String = buildString {
        appendLine()
        appendLine("### Follow-up")
        appendLine("Verify all callers were updated:")
        appendLine("  cnavFindUsages --type=\"$methodName\" --scope=prod")
    }

    fun executePlanFollowUp(): String = buildString {
        appendLine()
        appendLine("### Follow-up")
        appendLine("Recompile and verify the structural improvement:")
        appendLine("  cnavPackageDeps --scope=prod")
        appendLine("  cnavRings --mode=emergent --scope=prod")
        appendLine("  cnavCycles --scope=prod")
    }
}
