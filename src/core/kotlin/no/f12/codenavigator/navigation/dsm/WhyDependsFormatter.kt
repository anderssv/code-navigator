package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName

object WhyDependsFormatter {

    private fun displayName(className: ClassName, basePackage: PackageName): String {
        val pkg = className.packageName()
        val shortName = className.value.substringAfterLast('.')
        return if (pkg.value == basePackage.value) shortName
        else "${className.value.removePrefix("${basePackage.value}.")}"
    }

    fun noResultsHints(fromPackage: PackageName, toPackage: PackageName): List<String> = listOf(
        "No class-level dependencies found from '$fromPackage' → '$toPackage'. Check that both package names are correct (use cnavListClasses to verify).",
        "Dependencies only flow in the direction specified. Try swapping from-package and to-package to check the reverse direction.",
    )

    fun format(result: WhyDependsResult): String = buildString {
        appendLine("Dependencies: ${result.fromPackage} → ${result.toPackage}")
        appendLine("${result.edges.size} class-level dependency edge(s)")
        appendLine()
        result.edges.forEach { edge ->
            val count = if (edge.count > 1) " (${edge.count} references)" else ""
            appendLine("  ${displayName(edge.sourceClass, result.fromPackage)} → ${displayName(edge.targetClass, result.toPackage)}$count")
        }
    }.trimEnd()
}
