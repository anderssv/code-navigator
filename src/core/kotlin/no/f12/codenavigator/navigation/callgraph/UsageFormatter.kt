package no.f12.codenavigator.navigation.callgraph

object UsageFormatter {

    /** Emit a disambiguation header when the query pattern matched multiple distinct types. */
    private fun StringBuilder.appendDisambiguationHint(result: SmartUsageResult) {
        if (result.matchedTypes.size > 1) {
            val typeLabels = result.matchedTypes.sorted().joinToString(", ") { type ->
                val label = type.toString()
                if (type in result.interfaceTypes) "$label (interface)" else label
            }
            appendLine("[matched] $typeLabels")
            val firstInterface = result.matchedTypes.filter { it in result.interfaceTypes }.minOrNull()
            if (firstInterface != null) {
                appendLine("[hint] For exact match, use FQN: -Ptype=$firstInterface")
            }
        }
    }

    fun format(usages: List<UsageSite>): String {
        if (usages.isEmpty()) return "No usages found."

        return usages
            .sortedWith(compareBy({ it.callerClass }, { it.callerMethod }))
            .joinToString("\n") { u ->
                val sourceSetTag = u.sourceSet?.let { " [${it.label}]" } ?: ""
                "${u.callerClass}.${u.callerMethod} → ${u.targetOwner}.${u.targetName} (${u.sourceFile}) [${u.kind.name.lowercase()}]$sourceSetTag"
            }
    }

    fun formatCollapsed(usages: List<CollapsedUsage>): String {
        if (usages.isEmpty()) return "No usages found."

        return usages
            .joinToString("\n") { u ->
                val sourceSetTag = u.sourceSet?.let { " [${it.label}]" } ?: ""
                "${u.callerClass}.${u.callerMethod} → ${u.targetOwner} (${u.sourceFile}) [${u.kinds.sorted().joinToString(", ")}]$sourceSetTag"
            }
    }

    fun formatSmartUsages(result: SmartUsageResult, collapsedUsages: List<CollapsedUsage>): String = buildString {
        appendDisambiguationHint(result)
        if (result.implementations.isNotEmpty()) {
            result.implementations.forEach { impl ->
                appendLine("[impl] ${impl.className} (${impl.sourceFile})")
            }
        }
        collapsedUsages.forEach { u ->
            val sourceSetTag = u.sourceSet?.let { " [${it.label}]" } ?: ""
            appendLine("[ref] ${u.callerClass}.${u.callerMethod} → ${u.targetOwner} (${u.sourceFile}) [${u.kinds.sorted().joinToString(", ")}]$sourceSetTag")
        }
    }.trimEnd()

    fun formatSummary(usages: List<UsageSite>): String {
        if (usages.isEmpty()) return "No usages found."
        return usages
            .groupBy { it.sourceFile }
            .toSortedMap()
            .entries
            .joinToString("\n") { (sourceFile, sites) ->
                val count = sites.size
                val noun = if (count == 1) "reference" else "references"
                "$sourceFile ($count $noun)"
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
