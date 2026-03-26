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

    fun noResultsGuidance(ownerClass: String?, method: String?, field: String?, type: String?): String {
        val target = buildString {
            if (ownerClass != null) {
                append(ownerClass)
                if (method != null) append(".$method")
                if (field != null) append(".$field")
            } else {
                append(type)
            }
        }
        return buildString {
            appendLine("No usages found for '$target'.")
            appendLine("Hint: Ensure the value is a fully-qualified class name (e.g., com.example.MyClass).")
            if (ownerClass != null && method != null && field == null) {
                appendLine("Hint: Try -Pfield=$method to also find getter/setter calls for Kotlin properties.")
            }
            if (ownerClass != null) {
                appendLine("Hint: Try -Ptype=$ownerClass to also search type references, casts, and signatures.")
            }
        }.trimEnd()
    }
}
