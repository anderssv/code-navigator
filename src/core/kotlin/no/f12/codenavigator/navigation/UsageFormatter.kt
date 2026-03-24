package no.f12.codenavigator.navigation

object UsageFormatter {
    fun format(usages: List<UsageSite>): String {
        if (usages.isEmpty()) return "No usages found."

        return usages
            .sortedWith(compareBy({ it.callerClass }, { it.callerMethod }))
            .joinToString("\n") { u ->
                "${u.callerClass}.${u.callerMethod} → ${u.targetOwner}.${u.targetName} (${u.sourceFile}) [${u.kind.name.lowercase()}]"
            }
    }
}
