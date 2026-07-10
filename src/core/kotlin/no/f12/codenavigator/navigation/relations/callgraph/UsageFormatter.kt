package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.formatting.JsonRaw
import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.jsonStringArray

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
                appendLine("[hint] For exact match, use FQN: --type=$firstInterface / -Dtype=$firstInterface")
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

    fun formatJson(usages: List<UsageSite>): String =
        jsonArray(usages.sortedWith(compareBy({ it.callerClass }, { it.callerMethod }))) { u ->
            jsonObject(
                "callerClass" to u.callerClass.toString(),
                "callerMethod" to u.callerMethod,
                "sourceFile" to u.sourceFile,
                "targetOwner" to u.targetOwner.toString(),
                "targetMethod" to u.targetName,
                "targetDescriptor" to u.targetDescriptor,
                "kind" to u.kind.name.lowercase(),
                "sourceSet" to u.sourceSet?.label,
            )
        }

    fun formatLlm(usages: List<UsageSite>): String =
        usages.sortedWith(compareBy({ it.callerClass }, { it.callerMethod }))
            .joinToString("\n") {
                val sourceSetTag = it.sourceSet?.let { ss -> " [${ss.label}]" } ?: ""
                "${it.callerClass}.${it.callerMethod} -> ${it.targetOwner}.${it.targetName}${it.targetDescriptor} ${it.kind.name.lowercase()} ${it.sourceFile}$sourceSetTag"
            }

    fun formatCollapsed(usages: List<CollapsedUsage>): String {
        if (usages.isEmpty()) return "No usages found."

        return usages
            .joinToString("\n") { u ->
                val sourceSetTag = u.sourceSet?.let { " [${it.label}]" } ?: ""
                "${u.callerClass}.${u.callerMethod} → ${u.targetOwner} (${u.sourceFile}) [${u.kinds.sorted().joinToString(", ")}]$sourceSetTag"
            }
    }

    fun formatCollapsedJson(usages: List<CollapsedUsage>): String =
        jsonArray(usages) { u ->
            jsonObject(
                "callerClass" to u.callerClass.toString(),
                "callerMethod" to u.callerMethod,
                "sourceFile" to u.sourceFile,
                "targetOwner" to u.targetOwner.toString(),
                "kinds" to JsonRaw(jsonStringArray(u.kinds.sorted())),
                "sourceSet" to u.sourceSet?.label,
            )
        }

    fun formatCollapsedLlm(usages: List<CollapsedUsage>): String =
        usages.joinToString("\n") { u ->
            val sourceSetTag = u.sourceSet?.let { " [${it.label}]" } ?: ""
            "${u.callerClass}.${u.callerMethod} -> ${u.targetOwner} ${u.kinds.sorted().joinToString(",")} ${u.sourceFile}$sourceSetTag"
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

    fun formatSmartUsagesJson(result: SmartUsageResult, collapsedUsages: List<CollapsedUsage>): String =
        jsonObject(
            "matchedTypes" to JsonRaw(jsonArray(result.matchedTypes) { it.toString() }),
            "interfaceTypes" to JsonRaw(jsonArray(result.interfaceTypes.sorted()) { it.toString() }),
            "implementations" to JsonRaw(jsonArray(result.implementations) { impl ->
                jsonObject(
                    "className" to impl.className.toString(),
                    "sourceFile" to impl.sourceFile,
                )
            }),
            "usages" to JsonRaw(formatCollapsedJson(collapsedUsages)),
        )

    fun formatSmartUsagesLlm(result: SmartUsageResult, collapsedUsages: List<CollapsedUsage>): String = buildString {
        appendDisambiguationHint(result)
        if (result.implementations.isNotEmpty()) {
            result.implementations.forEach { impl ->
                appendLine("[impl] ${impl.className} ${impl.sourceFile}")
            }
        }
        collapsedUsages.forEach { u ->
            val sourceSetTag = u.sourceSet?.let { " [${it.label}]" } ?: ""
            appendLine("[ref] ${u.callerClass}.${u.callerMethod} -> ${u.targetOwner} ${u.kinds.sorted().joinToString(",")} ${u.sourceFile}$sourceSetTag")
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

    fun formatSummaryJson(usages: List<UsageSite>): String {
        val sorted = usages.groupBy { it.sourceFile }.toSortedMap().entries.toList()
        return jsonArray(sorted) { (sourceFile, sites) ->
            jsonObject(
                "sourceFile" to sourceFile,
                "referenceCount" to sites.size,
            )
        }
    }

    fun formatSummaryLlm(usages: List<UsageSite>): String =
        usages.groupBy { it.sourceFile }
            .toSortedMap()
            .entries
            .joinToString("\n") { (sourceFile, sites) -> "$sourceFile ${sites.size}" }

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
            add("Try --field=$method / -Dfield=$method to also find getter/setter calls for Kotlin properties.")
        }
        if (ownerClass != null) {
            add("Try --type=$ownerClass / -Dtype=$ownerClass to also search type references, casts, and signatures.")
        }
    }
}
