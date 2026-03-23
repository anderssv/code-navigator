package no.f12.codenavigator

import no.f12.codenavigator.analysis.CoupledPair
import no.f12.codenavigator.analysis.FileAge
import no.f12.codenavigator.analysis.FileChurn
import no.f12.codenavigator.analysis.Hotspot
import no.f12.codenavigator.analysis.ModuleAuthors
import no.f12.codenavigator.navigation.CallDirection
import no.f12.codenavigator.navigation.CallTreeNode
import no.f12.codenavigator.navigation.ClassDetail
import no.f12.codenavigator.navigation.ClassInfo
import no.f12.codenavigator.navigation.InterfaceRegistry
import no.f12.codenavigator.navigation.PackageDependencies
import no.f12.codenavigator.navigation.SymbolInfo
import no.f12.codenavigator.navigation.DsmMatrix

object LlmFormatter {

    fun formatClasses(classes: List<ClassInfo>): String =
        classes.sortedBy { it.className }.joinToString("\n") { "${it.className} ${it.sourceFileName}" }

    fun formatSymbols(symbols: List<SymbolInfo>): String =
        symbols.sortedWith(compareBy({ it.packageName }, { it.className }, { it.symbolName }))
            .joinToString("\n") { "${it.packageName}.${it.className}.${it.symbolName} ${it.kind.name.lowercase()} ${it.sourceFile}" }

    fun formatClassDetails(details: List<ClassDetail>): String =
        details.sortedBy { it.className }.joinToString("\n") { d ->
            buildString {
                append("${d.className} ${d.sourceFile}")
                if (d.superClass != null) append(" extends:${d.superClass}")
                if (d.interfaces.isNotEmpty()) append(" implements:${d.interfaces.joinToString(",")}")
                if (d.fields.isNotEmpty()) append(" fields:${d.fields.joinToString(",") { "${it.name}:${it.type}" }}")
                if (d.methods.isNotEmpty()) append(" methods:${d.methods.joinToString(",") { "${it.name}(${it.parameterTypes.joinToString(",")}):${it.returnType}" }}")
            }
        }

    fun formatInterfaces(registry: InterfaceRegistry, interfaceNames: List<String>): String =
        interfaceNames.sorted().joinToString("\n") { name ->
            val impls = registry.implementorsOf(name).sortedBy { it.className }
            "$name: ${impls.joinToString(",") { "${it.className}(${it.sourceFile})" }}"
        }

    fun renderCallTrees(trees: List<CallTreeNode>, direction: CallDirection): String = buildString {
        trees.forEachIndexed { index, tree ->
            if (index > 0) appendLine()
            append("${tree.method.qualifiedName} ${tree.sourceFile ?: "<unknown>"}")
            if (tree.children.isNotEmpty()) {
                renderChildren(tree.children, direction, 1)
            }
        }
    }.trimEnd()

    fun formatPackageDeps(deps: PackageDependencies, packageNames: List<String>, reverse: Boolean): String {
        val arrow = if (reverse) "<-" else "->"
        return packageNames.sorted().joinToString("\n") { pkg ->
            val related = if (reverse) deps.dependentsOf(pkg) else deps.dependenciesOf(pkg)
            "$pkg $arrow ${related.joinToString(",")}"
        }
    }

    fun formatHotspots(hotspots: List<Hotspot>): String =
        hotspots.joinToString("\n") { "${it.file} revisions=${it.revisions} churn=${it.totalChurn}" }

    fun formatCoupling(pairs: List<CoupledPair>): String =
        pairs.joinToString("\n") { "${it.entity} -- ${it.coupled} degree=${it.degree}% shared=${it.sharedRevs} avg=${it.avgRevs}" }

    fun formatAge(ages: List<FileAge>): String =
        ages.joinToString("\n") { "${it.file} age=${it.ageMonths}months last=${it.lastChangeDate}" }

    fun formatAuthors(modules: List<ModuleAuthors>): String =
        modules.joinToString("\n") { "${it.file} authors=${it.authors} revisions=${it.revisions}" }

    fun formatChurn(churn: List<FileChurn>): String =
        churn.joinToString("\n") { "${it.file} added=${it.added} deleted=${it.deleted} commits=${it.commits}" }

    fun formatDsm(matrix: DsmMatrix): String = buildString {
        append("packages:${matrix.packages.joinToString(",")}")
        if (matrix.cells.isEmpty()) {
            append("\n(no dependencies)")
        } else {
            for ((key, count) in matrix.cells.entries.sortedBy { "${it.key.first}-${it.key.second}" }) {
                append("\n${key.first}->${key.second}:$count")
                val classDeps = matrix.classDependencies[key]
                if (!classDeps.isNullOrEmpty()) {
                    val classStr = classDeps.sortedBy { "${it.first}-${it.second}" }
                        .joinToString(",") { "${it.first}->${it.second}" }
                    append(" [$classStr]")
                }
            }
            val cyclicPairs = matrix.findCyclicPairs()
            if (cyclicPairs.isNotEmpty()) {
                val cycleStr = cyclicPairs.joinToString(",") { (a, b, _) -> "$a<->$b" }
                append("\nCYCLES: $cycleStr")
            }
        }
    }

    private fun StringBuilder.renderChildren(children: List<CallTreeNode>, direction: CallDirection, depth: Int) {
        val indent = "  ".repeat(depth)
        for (node in children) {
            appendLine()
            append("$indent${direction.arrow} ${node.method.qualifiedName} ${node.sourceFile ?: "<unknown>"}")
            if (node.children.isNotEmpty()) {
                renderChildren(node.children, direction, depth + 1)
            }
        }
    }
}
