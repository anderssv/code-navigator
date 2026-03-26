package no.f12.codenavigator

import no.f12.codenavigator.analysis.CoupledPair
import no.f12.codenavigator.analysis.FileAge
import no.f12.codenavigator.analysis.FileChurn
import no.f12.codenavigator.analysis.Hotspot
import no.f12.codenavigator.analysis.ModuleAuthors
import no.f12.codenavigator.navigation.CallDirection
import no.f12.codenavigator.navigation.CallTreeNode
import no.f12.codenavigator.navigation.ClassComplexity
import no.f12.codenavigator.navigation.CycleDetail
import no.f12.codenavigator.navigation.ClassDetail
import no.f12.codenavigator.navigation.ClassInfo
import no.f12.codenavigator.navigation.ClassName
import no.f12.codenavigator.navigation.InterfaceRegistry
import no.f12.codenavigator.navigation.PackageDependencies
import no.f12.codenavigator.navigation.PackageName
import no.f12.codenavigator.navigation.SymbolInfo
import no.f12.codenavigator.navigation.DsmMatrix
import no.f12.codenavigator.navigation.RankedType
import no.f12.codenavigator.navigation.DeadCode
import no.f12.codenavigator.navigation.MetricsResult
import no.f12.codenavigator.navigation.UsageSite

object LlmFormatter {

    fun formatClasses(classes: List<ClassInfo>): String =
        classes.sortedBy { it.className }.joinToString("\n") { "${it.className.value} ${it.sourceFileName}" }

    fun formatSymbols(symbols: List<SymbolInfo>): String =
        symbols.sortedWith(compareBy({ it.packageName.value }, { it.className }, { it.symbolName }))
            .joinToString("\n") { "${it.packageName.value}.${it.className}.${it.symbolName} ${it.kind.name.lowercase()} ${it.sourceFile}" }

    fun formatClassDetails(details: List<ClassDetail>): String =
        details.sortedBy { it.className }.joinToString("\n") { d ->
            buildString {
                append("${d.className.value} ${d.sourceFile}")
                if (d.superClass != null) append(" extends:${d.superClass.value}")
                if (d.interfaces.isNotEmpty()) append(" implements:${d.interfaces.joinToString(",") { it.value }}")
                if (d.fields.isNotEmpty()) append(" fields:${d.fields.joinToString(",") { "${it.name}:${it.type}" }}")
                if (d.methods.isNotEmpty()) append(" methods:${d.methods.joinToString(",") { "${it.name}(${it.parameterTypes.joinToString(",")}):${it.returnType}" }}")
            }
        }

    fun formatInterfaces(registry: InterfaceRegistry, interfaceNames: List<ClassName>): String =
        interfaceNames.sorted().joinToString("\n") { name ->
            val impls = registry.implementorsOf(name).sortedBy { it.className }
            "${name.value}: ${impls.joinToString(",") { "${it.className.value}(${it.sourceFile})" }}"
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

    fun formatPackageDeps(deps: PackageDependencies, packageNames: List<PackageName>, reverse: Boolean): String {
        val arrow = if (reverse) "<-" else "->"
        return packageNames.sorted().joinToString("\n") { pkg ->
            val related = if (reverse) deps.dependentsOf(pkg) else deps.dependenciesOf(pkg)
            "${pkg.value} $arrow ${related.joinToString(",") { it.value }}"
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

    fun formatUsages(usages: List<UsageSite>): String =
        usages.sortedWith(compareBy({ it.callerClass }, { it.callerMethod }))
            .joinToString("\n") { "${it.callerClass}.${it.callerMethod} -> ${it.targetOwner}.${it.targetName}${it.targetDescriptor} ${it.kind.name.lowercase()} ${it.sourceFile}" }

    fun formatRank(ranked: List<RankedType>): String =
        ranked.joinToString("\n") { "%.4f".format(it.rank).let { rank -> "${it.className.value} rank=$rank in=${it.inDegree} out=${it.outDegree}" } }

    fun formatDead(dead: List<DeadCode>): String =
        dead.joinToString("\n") { d ->
            val name = if (d.memberName != null) "${d.className.value}.${d.memberName}" else d.className.value
            "$name ${d.kind.name} ${d.sourceFile}"
        }

    fun formatComplexity(results: List<ClassComplexity>): String =
        results.joinToString("\n\n") { c ->
            buildString {
                append("${c.className.value} out=${c.fanOut}/${c.distinctOutgoingClasses} in=${c.fanIn}/${c.distinctIncomingClasses}")
                if (c.outgoingByClass.isEmpty()) {
                    append("\n  outgoing: none")
                } else {
                    append("\n  outgoing:")
                    c.outgoingByClass.forEach { append("\n    ${it.first.value}(${it.second})") }
                }
                if (c.incomingByClass.isEmpty()) {
                    append("\n  incoming: none")
                } else {
                    append("\n  incoming:")
                    c.incomingByClass.forEach { append("\n    ${it.first.value}(${it.second})") }
                }
            }
        }

    fun formatCycles(details: List<CycleDetail>): String {
        if (details.isEmpty()) return "(no cycles)"

        return details.joinToString("\n") { detail ->
            buildString {
                append("CYCLE ${detail.packages.joinToString(",") { it.value }}")
                for (edge in detail.edges) {
                    val classStr = edge.classEdges.sortedBy { "${it.first.value}-${it.second.value}" }
                        .joinToString(",") { "${it.first.value}->${it.second.value}" }
                    append("\n  ${edge.from.value}->${edge.to.value}: $classStr")
                }
            }
        }
    }

    fun formatMetrics(metrics: MetricsResult): String = buildString {
        append("classes=${metrics.totalClasses}")
        append(" packages=${metrics.packageCount}")
        append(" avg-fan-in=${"%.1f".format(java.util.Locale.US, metrics.averageFanIn)}")
        append(" avg-fan-out=${"%.1f".format(java.util.Locale.US, metrics.averageFanOut)}")
        append(" cycles=${metrics.cycleCount}")
        append(" dead-classes=${metrics.deadClassCount}")
        append(" dead-methods=${metrics.deadMethodCount}")
        if (metrics.topHotspots.isNotEmpty()) {
            appendLine()
            appendLine("hotspots:")
            append(metrics.topHotspots.joinToString("\n") { "${it.file} revisions=${it.revisions} churn=${it.totalChurn}" })
        }
    }

    fun formatDsm(matrix: DsmMatrix): String = buildString {
        append("packages:${matrix.packages.joinToString(",") { it.value }}")
        if (matrix.cells.isEmpty()) {
            append("\n(no dependencies)")
        } else {
            for ((key, count) in matrix.cells.entries.sortedBy { "${it.key.first.value}-${it.key.second.value}" }) {
                append("\n${key.first.value}->${key.second.value}:$count")
                val classDeps = matrix.classDependencies[key]
                if (!classDeps.isNullOrEmpty()) {
                    val classStr = classDeps.sortedBy { "${it.first.value}-${it.second.value}" }
                        .joinToString(",") { "${it.first.value}->${it.second.value}" }
                    append(" [$classStr]")
                }
            }
            val cyclicPairs = matrix.findCyclicPairs()
            if (cyclicPairs.isNotEmpty()) {
                val cycleStr = cyclicPairs.joinToString(",") { (a, b, _) -> "${a.value}<->${b.value}" }
                append("\nCYCLES: $cycleStr")
            }
        }
    }

    fun formatDsmCycles(matrix: DsmMatrix, cycleFilter: Pair<PackageName, PackageName>? = null): String {
        val cyclicPairs = matrix.findCyclicPairs(cycleFilter)
        if (cyclicPairs.isEmpty()) return "(no cycles)"

        return cyclicPairs.joinToString("\n") { (a, b, counts) ->
            val fwd = matrix.classDependencies[a to b]
            val bwd = matrix.classDependencies[b to a]
            val fwdStr = fwd?.sortedBy { "${it.first.value}-${it.second.value}" }
                ?.joinToString(",") { "${it.first.value}->${it.second.value}" } ?: ""
            val bwdStr = bwd?.sortedBy { "${it.first.value}-${it.second.value}" }
                ?.joinToString(",") { "${it.first.value}->${it.second.value}" } ?: ""
            buildString {
                append("CYCLE ${a.value}<->${b.value} ${counts.first}/${counts.second}")
                if (fwdStr.isNotEmpty()) append("\n  ${a.value}->${b.value}: $fwdStr")
                if (bwdStr.isNotEmpty()) append("\n  ${b.value}->${a.value}: $bwdStr")
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
