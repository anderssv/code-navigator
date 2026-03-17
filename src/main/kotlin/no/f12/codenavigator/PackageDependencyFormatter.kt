package no.f12.codenavigator

object PackageDependencyFormatter {

    fun format(deps: PackageDependencies, packageNames: List<String>): String = buildString {
        packageNames.forEachIndexed { index, pkg ->
            if (index > 0) appendLine()
            appendLine(pkg)
            val pkgDeps = deps.dependenciesOf(pkg)
            if (pkgDeps.isEmpty()) {
                appendLine("  (no outgoing dependencies)")
            } else {
                pkgDeps.forEach { dep ->
                    appendLine("  → $dep")
                }
            }
        }
    }.trimEnd()
}
