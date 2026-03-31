package no.f12.codenavigator.navigation.callgraph

object UsageFormatter {
    fun format(usages: List<UsageSite>): String {
        if (usages.isEmpty()) return "No usages found."

        return usages
            .sortedWith(compareBy({ it.callerClass }, { it.callerMethod }))
            .joinToString("\n") { u ->
                val sourceSetTag = u.sourceSet?.let { " [${it.label}]" } ?: ""
                "${u.callerClass}.${u.callerMethod} → ${u.targetOwner}.${u.targetName} (${u.sourceFile}) [${u.kind.name.lowercase()}]$sourceSetTag"
            }
    }

    fun noResultsTarget(ownerClass: String?, method: String?, field: String?, type: String?): String = buildString {
        if (ownerClass != null) {
            append(ownerClass)
            if (method != null) append(".$method")
            if (field != null) append(".$field")
        } else {
            append(type)
        }
    }

    fun noResultsHints(ownerClass: String?, method: String?, field: String?, type: String?): List<String> = buildList {
        add("Short names and camelCase patterns are supported (e.g., MyService matches com.example.MyService).")
        add("For exact matching, use a fully-qualified class name (e.g., com.example.MyClass).")
        if (ownerClass != null && method != null && field == null) {
            add("Try -Pfield=$method to also find getter/setter calls for Kotlin properties.")
        }
        if (ownerClass != null) {
            add("Try -Ptype=$ownerClass to also search type references, casts, and signatures.")
        }
    }
}
