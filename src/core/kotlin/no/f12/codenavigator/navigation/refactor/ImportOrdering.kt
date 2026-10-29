package no.f12.codenavigator.navigation.refactor

/**
 * Puts imports a refactoring added or rewrote in their sorted place, so projects that enforce import order (ktlint)
 * don't need a format pass. Only when the file's other imports already follow an order we recognise: ktlint's
 * default layout (`*, java.**, javax.**, kotlin.**, aliases`) or plain lexicographic. Otherwise the file is left
 * as the refactoring produced it, rather than reordering imports the user arranged by hand.
 */
object ImportOrdering {

    private val ktlintLayout = compareBy<String>({ ktlintGroup(it) }, { it })
    private val lexicographic = naturalOrder<String>()

    private fun ktlintGroup(spec: String) = when {
        " as " in spec -> 4
        spec.startsWith("java.") -> 1
        spec.startsWith("javax.") -> 2
        spec.startsWith("kotlin.") -> 3
        else -> 0
    }

    private fun spec(line: String) = line.trim().removePrefix("import ").trim()

    fun placeNewImports(before: String, after: String): String {
        val lines = after.lines()
        val importIdx = lines.indices.filter { lines[it].trimStart().startsWith("import ") }
        if (importIdx.isEmpty()) return after
        val first = importIdx.first()
        val last = importIdx.last()
        if ((first..last).any { it !in importIdx && lines[it].isNotBlank() }) return after

        val oldImports = before.lines().filter { it.trimStart().startsWith("import ") }.toSet()
        val imports = importIdx.map { lines[it] }
        val (added, kept) = imports.partition { it !in oldImports }
        if (added.isEmpty()) return after

        val comparator = listOf(ktlintLayout, lexicographic).firstOrNull { cmp ->
            kept.map(::spec).zipWithNext().all { (a, b) -> cmp.compare(a, b) <= 0 }
        } ?: return after
        val sorted = (kept + added).sortedWith(compareBy(comparator) { spec(it) })
        if (sorted == imports && (first..last).all { it in importIdx }) return after

        return (lines.subList(0, first) + sorted + lines.subList(last + 1, lines.size)).joinToString("\n")
    }

    fun placeNewImports(changes: List<RenameChange>): List<RenameChange> =
        changes.map { it.copy(after = placeNewImports(it.before, it.after)) }
}
