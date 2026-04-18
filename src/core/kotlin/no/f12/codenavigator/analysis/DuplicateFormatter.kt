package no.f12.codenavigator.analysis

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
}
