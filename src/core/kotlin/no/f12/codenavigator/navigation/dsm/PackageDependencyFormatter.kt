package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.formatting.JsonRaw
import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.jsonStringArray
import no.f12.codenavigator.navigation.types.PackageName

object PackageDependencyFormatter {

    fun noResultsHints(packageCount: Int): List<String> = buildList {
        if (packageCount <= 1) {
            add("All classes are in a single package. Package dependencies show inter-package relationships, so there is nothing to display. Consider splitting classes into multiple packages.")
        }
    }

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

    fun formatJson(
        deps: PackageDependencies,
        packageNames: List<PackageName>,
        reverse: Boolean = false,
    ): String =
        jsonArray(packageNames) { pkg ->
            val related = if (reverse) deps.dependentsOf(pkg) else deps.dependenciesOf(pkg)
            val key = if (reverse) "dependents" else "dependencies"
            jsonObject(
                "package" to pkg.toString(),
                key to JsonRaw(jsonStringArray(related.map { it.toString() })),
            )
        }

    fun formatLlm(deps: PackageDependencies, packageNames: List<PackageName>, reverse: Boolean): String {
        val arrow = if (reverse) "<-" else "->"
        return packageNames.sorted().joinToString("\n") { pkg ->
            val related = if (reverse) deps.dependentsOf(pkg) else deps.dependenciesOf(pkg)
            "$pkg $arrow ${related.joinToString(",")}"
        }
    }
}
