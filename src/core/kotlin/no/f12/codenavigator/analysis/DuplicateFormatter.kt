package no.f12.codenavigator.analysis

import no.f12.codenavigator.formatting.JsonRaw
import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject

object DuplicateFormatter {

    fun format(groups: List<DuplicateGroup>): String {
        if (groups.isEmpty()) return "No duplicates found."

        return buildString {
            appendLine("${groups.size} duplicate group(s) found:")
            appendLine()
            for ((index, group) in groups.withIndex()) {
                appendLine("Group ${index + 1}: ${group.tokenCount} tokens, ${group.locations.size} locations")
                for (loc in group.locations) {
                    appendLine("  ${loc.file}:${loc.startLine}-${loc.endLine}")
                }
                if (index < groups.size - 1) appendLine()
            }
        }.trimEnd()
    }

    fun formatJson(groups: List<DuplicateGroup>): String =
        jsonArray(groups) { g ->
            jsonObject(
                "tokenCount" to g.tokenCount,
                "locations" to JsonRaw(jsonArray(g.locations) { loc ->
                    jsonObject(
                        "file" to loc.file,
                        "startLine" to loc.startLine,
                        "endLine" to loc.endLine,
                    )
                }),
            )
        }

    fun formatLlm(groups: List<DuplicateGroup>): String =
        groups.joinToString("\n\n") { group ->
            "tokens=${group.tokenCount}\n" + group.locations.joinToString("\n") { "  ${it.file}:${it.startLine}-${it.endLine}" }
        }
}
