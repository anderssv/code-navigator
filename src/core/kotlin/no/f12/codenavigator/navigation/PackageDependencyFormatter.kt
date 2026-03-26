package no.f12.codenavigator.navigation

object PackageDependencyFormatter {

    fun format(
        deps: PackageDependencies,
        packageNames: List<PackageName>,
        reverse: Boolean = false,
    ): String = buildString {
        val arrow = if (reverse) "←" else "→"
        val emptyMessage = if (reverse) "(no incoming dependencies)" else "(no outgoing dependencies)"

        packageNames.forEachIndexed { index, pkg ->
            if (index > 0) appendLine()
            appendLine(pkg)
            val related = if (reverse) deps.dependentsOf(pkg) else deps.dependenciesOf(pkg)
            if (related.isEmpty()) {
                appendLine("  $emptyMessage")
            } else {
                related.forEach { dep ->
                    appendLine("  $arrow $dep")
                }
            }
        }
    }.trimEnd()
}
