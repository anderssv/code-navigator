package no.f12.codenavigator

import no.f12.codenavigator.analysis.CoupledPair
import no.f12.codenavigator.analysis.FileAge
import no.f12.codenavigator.analysis.FileChurn
import no.f12.codenavigator.analysis.Hotspot
import no.f12.codenavigator.analysis.ModuleAuthors
import no.f12.codenavigator.navigation.callgraph.CallDirection
import no.f12.codenavigator.navigation.callgraph.CallTreeNode
import no.f12.codenavigator.navigation.complexity.ClassComplexity
import no.f12.codenavigator.navigation.dsm.CycleDetail
import no.f12.codenavigator.navigation.ClassName
import no.f12.codenavigator.navigation.classinfo.AnnotationDetail
import no.f12.codenavigator.navigation.classinfo.ClassDetail
import no.f12.codenavigator.navigation.classinfo.ClassInfo
import no.f12.codenavigator.navigation.classinfo.FieldDetail
import no.f12.codenavigator.navigation.interfaces.InterfaceRegistry
import no.f12.codenavigator.navigation.classinfo.MethodDetail
import no.f12.codenavigator.navigation.dsm.PackageDependencies
import no.f12.codenavigator.navigation.PackageName
import no.f12.codenavigator.navigation.symbol.SymbolInfo
import no.f12.codenavigator.navigation.dsm.DsmMatrix
import no.f12.codenavigator.navigation.rank.RankedType
import no.f12.codenavigator.navigation.deadcode.DeadCode
import no.f12.codenavigator.navigation.stringconstant.StringConstantMatch
import no.f12.codenavigator.navigation.hierarchy.SupertypeInfo
import no.f12.codenavigator.navigation.hierarchy.SupertypeKind
import no.f12.codenavigator.navigation.metrics.MetricsResult
import no.f12.codenavigator.navigation.hierarchy.TypeHierarchyResult
import no.f12.codenavigator.navigation.callgraph.UsageSite
import no.f12.codenavigator.navigation.annotation.AnnotationMatch
import no.f12.codenavigator.navigation.callgraph.AnnotationTag
import no.f12.codenavigator.navigation.changedsince.ChangedClassImpact

object LlmFormatter {

    fun formatClasses(classes: List<ClassInfo>): String =
        classes.sortedBy { it.className }.joinToString("\n") { "${it.className.displayName()} ${it.sourceFileName}" }

    fun formatSymbols(symbols: List<SymbolInfo>): String =
        symbols.sortedWith(compareBy({ it.packageName.toString() }, { it.className.toString() }, { it.symbolName }))
            .joinToString("\n") { "${it.className}.${it.symbolName} ${it.kind.name.lowercase()} ${it.sourceFile}" }

    fun formatClassDetails(details: List<ClassDetail>): String =
        details.sortedBy { it.className }.joinToString("\n") { d ->
            buildString {
                append("${d.className} ${d.sourceFile}")
                if (d.annotations.isNotEmpty()) append(" annotations:${d.annotations.joinToString(",") { formatAnnotation(it) }}")
                if (d.superClass != null) append(" extends:${d.superClass}")
                if (d.interfaces.isNotEmpty()) append(" implements:${d.interfaces.joinToString(",")}")
                if (d.fields.isNotEmpty()) append(" fields:${d.fields.joinToString(",") { formatFieldCompact(it) }}")
                if (d.methods.isNotEmpty()) append(" methods:${d.methods.joinToString(",") { formatMethodCompact(it) }}")
            }
        }

    fun formatInterfaces(registry: InterfaceRegistry, interfaceNames: List<ClassName>): String =
        interfaceNames.sorted().joinToString("\n") { name ->
            val impls = registry.implementorsOf(name).sortedBy { it.className }
            "$name: ${impls.joinToString(",") { "${it.className}(${it.sourceFile})" }}"
        }

    fun formatTypeHierarchy(results: List<TypeHierarchyResult>): String =
        results.sortedBy { it.className }.joinToString("\n\n") { result ->
            buildString {
                append("${result.className} ${result.sourceFile}")
                if (result.supertypes.isNotEmpty()) {
                    renderSupertypesLlm(result.supertypes, 1)
                }
                if (result.implementors.isNotEmpty()) {
                    appendLine()
                    append("  implementors: ${result.implementors.sortedBy { it.className }.joinToString(",") { "${it.className}(${it.sourceFile})" }}")
                }
            }
        }

    fun renderCallTrees(trees: List<CallTreeNode>, direction: CallDirection): String = buildString {
        trees.forEachIndexed { index, tree ->
            if (index > 0) appendLine()
            val lineRef = tree.lineNumber?.let { ":$it" } ?: ""
            append("${tree.method.qualifiedName} ${tree.sourceFile ?: "<unknown>"}$lineRef${formatAnnotationTags(tree.annotations)}")
            if (tree.children.isNotEmpty()) {
                renderChildren(tree.children, direction, 1)
            }
        }
    }.trimEnd()

    fun formatPackageDeps(deps: PackageDependencies, packageNames: List<PackageName>, reverse: Boolean): String {
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

    fun formatUsages(usages: List<UsageSite>): String =
        usages.sortedWith(compareBy({ it.callerClass }, { it.callerMethod }))
            .joinToString("\n") { "${it.callerClass}.${it.callerMethod} -> ${it.targetOwner}.${it.targetName}${it.targetDescriptor} ${it.kind.name.lowercase()} ${it.sourceFile}" }

    fun formatRank(ranked: List<RankedType>): String =
        ranked.joinToString("\n") { "%.4f".format(it.rank).let { rank -> "${it.className} rank=$rank in=${it.inDegree} out=${it.outDegree}" } }

    private val DEAD_CODE_NOTE = "Note: Dead code detection is a hard problem with many edge cases (reflection, serialization, generated code). Use exclude=<regex> to filter out packages or classes you know are not dead."

    fun formatDead(dead: List<DeadCode>): String {
        if (dead.isEmpty()) return ""
        return dead.joinToString("\n") { d ->
            val name = if (d.memberName != null) "${d.className}.${d.memberName}" else d.className.toString()
            "$name ${d.kind.name} ${d.sourceFile} confidence=${d.confidence.name} reason=${d.reason.name}"
        } + "\n\n" + DEAD_CODE_NOTE
    }

    fun formatStringConstants(matches: List<StringConstantMatch>): String =
        matches.joinToString("\n") { m ->
            "${m.className}.${m.methodName}: \"${m.value}\" ${m.sourceFile}"
        }

    fun formatChangedSince(impacts: List<ChangedClassImpact>, unresolved: List<String>): String = buildString {
        impacts.forEachIndexed { index, impact ->
            if (index > 0) appendLine()
            append("${impact.className} ${impact.sourceFile}")
            if (impact.callers.isEmpty()) {
                append(" (no callers)")
            } else {
                for (caller in impact.callers.sortedBy { "${it.className}.${it.methodName}" }) {
                    appendLine()
                    append("  <- ${caller.className}.${caller.methodName}")
                }
            }
        }
        if (unresolved.isNotEmpty()) {
            if (impacts.isNotEmpty()) appendLine()
            append("UNRESOLVED: ${unresolved.joinToString(",")}")
        }
    }

    fun formatAnnotations(matches: List<AnnotationMatch>): String {
        if (matches.isEmpty()) return "(no matches)"
        return matches.joinToString("\n") { match ->
            buildString {
                append("${match.className.value} ${match.sourceFile ?: "<unknown>"}")
                if (match.classAnnotations.isNotEmpty()) {
                    append(" ${match.classAnnotations.sorted().joinToString(",") { "@${it.simpleName()}" }}")
                }
                for (method in match.matchedMethods) {
                    appendLine()
                    append("  ${method.method.methodName} ${method.annotations.sorted().joinToString(",") { "@${it.simpleName()}" }}")
                }
            }
        }
    }

    fun formatComplexity(results: List<ClassComplexity>): String =
        results.joinToString("\n\n") { c ->
            buildString {
                append("${c.className} out=${c.fanOut}/${c.distinctOutgoingClasses} in=${c.fanIn}/${c.distinctIncomingClasses}")
                if (c.outgoingByClass.isEmpty()) {
                    append("\n  outgoing: none")
                } else {
                    append("\n  outgoing:")
                    c.outgoingByClass.forEach { append("\n    ${it.first}(${it.second})") }
                }
                if (c.incomingByClass.isEmpty()) {
                    append("\n  incoming: none")
                } else {
                    append("\n  incoming:")
                    c.incomingByClass.forEach { append("\n    ${it.first}(${it.second})") }
                }
            }
        }

    fun formatCycles(details: List<CycleDetail>): String {
        if (details.isEmpty()) return "(no cycles)"

        return details.joinToString("\n") { detail ->
            buildString {
                append("CYCLE ${detail.packages.joinToString(",")}")
                for (edge in detail.edges) {
                    val classStr = edge.classEdges.sortedBy { "${it.first}-${it.second}" }
                        .joinToString(",") { "${it.first}->${it.second}" }
                    append("\n  ${edge.from}->${edge.to}: $classStr")
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

    fun formatDsmCycles(matrix: DsmMatrix, cycleFilter: Pair<PackageName, PackageName>? = null): String {
        val cyclicPairs = matrix.findCyclicPairs(cycleFilter)
        if (cyclicPairs.isEmpty()) return "(no cycles)"

        return cyclicPairs.joinToString("\n") { (a, b, counts) ->
            val fwd = matrix.classDependencies[a to b]
            val bwd = matrix.classDependencies[b to a]
            val fwdStr = fwd?.sortedBy { "${it.first}-${it.second}" }
                ?.joinToString(",") { "${it.first}->${it.second}" } ?: ""
            val bwdStr = bwd?.sortedBy { "${it.first}-${it.second}" }
                ?.joinToString(",") { "${it.first}->${it.second}" } ?: ""
            buildString {
                append("CYCLE $a<->$b ${counts.first}/${counts.second}")
                if (fwdStr.isNotEmpty()) append("\n  $a->$b: $fwdStr")
                if (bwdStr.isNotEmpty()) append("\n  $b->$a: $bwdStr")
            }
        }
    }

    private fun StringBuilder.renderChildren(children: List<CallTreeNode>, direction: CallDirection, depth: Int) {
        val indent = "  ".repeat(depth)
        for (node in children) {
            val lineRef = node.lineNumber?.let { ":$it" } ?: ""
            appendLine()
            append("$indent${direction.arrow} ${node.method.qualifiedName} ${node.sourceFile ?: "<unknown>"}$lineRef${formatAnnotationTags(node.annotations)}")
            if (node.children.isNotEmpty()) {
                renderChildren(node.children, direction, depth + 1)
            }
        }
    }

    private fun formatAnnotationTags(annotations: List<AnnotationTag>): String =
        if (annotations.isEmpty()) "" else " [${annotations.joinToString(", ") { tag ->
            val params = if (tag.parameters.isNotEmpty()) {
                val paramStr = tag.parameters.entries.joinToString(",") { "${it.key}=\"${it.value}\"" }
                "($paramStr)"
            } else {
                ""
            }
            val suffix = if (tag.framework != null) " [${tag.framework}]" else ""
            "@${tag.name.simpleName()}$params$suffix"
        }}]"

    private fun formatAnnotation(annotation: AnnotationDetail): String = buildString {
        append("@${annotation.name.simpleName()}")
        if (annotation.parameters.isNotEmpty()) {
            val params = annotation.parameters.entries.joinToString(",") { "${it.key}=\"${it.value}\"" }
            append("($params)")
        }
    }

    private fun formatFieldCompact(field: FieldDetail): String {
        val prefix = field.annotations.joinToString("") { "${formatAnnotation(it)}+" }
        return "$prefix${field.name}:${field.type}"
    }

    private fun formatMethodCompact(method: MethodDetail): String {
        val prefix = method.annotations.joinToString("") { "${formatAnnotation(it)}+" }
        return "$prefix${method.name}(${method.parameterTypes.joinToString(",")}):${method.returnType}"
    }

    private fun StringBuilder.renderSupertypesLlm(supertypes: List<SupertypeInfo>, depth: Int) {
        val indent = "  ".repeat(depth)
        for (st in supertypes) {
            val kindLabel = when (st.kind) {
                SupertypeKind.CLASS -> "extends"
                SupertypeKind.INTERFACE -> "implements"
            }
            appendLine()
            append("$indent$kindLabel ${st.className}")
            if (st.supertypes.isNotEmpty()) {
                renderSupertypesLlm(st.supertypes, depth + 1)
            }
        }
    }
}
