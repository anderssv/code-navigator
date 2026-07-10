package no.f12.codenavigator.formatting

import no.f12.codenavigator.analysis.CoupledPair
import no.f12.codenavigator.analysis.DuplicateGroup
import no.f12.codenavigator.analysis.FileAge
import no.f12.codenavigator.analysis.FileChurn
import no.f12.codenavigator.analysis.FileSizeEntry
import no.f12.codenavigator.analysis.Hotspot
import no.f12.codenavigator.analysis.ModuleAuthors
import no.f12.codenavigator.analysis.PackageVolatilityResult
import no.f12.codenavigator.navigation.relations.callgraph.CallDirection
import no.f12.codenavigator.navigation.relations.callgraph.CallTreeFormatter
import no.f12.codenavigator.navigation.relations.callgraph.CallTreeNode
import no.f12.codenavigator.navigation.complexity.ClassComplexity
import no.f12.codenavigator.navigation.dsm.CycleBreakAnalyzer
import no.f12.codenavigator.navigation.dsm.CycleDetail
import no.f12.codenavigator.navigation.dsm.TestInvolvement
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.classinfo.AnnotationDetail
import no.f12.codenavigator.navigation.classinfo.ClassDetail
import no.f12.codenavigator.navigation.classinfo.ClassDetailFormatter
import no.f12.codenavigator.navigation.classinfo.ClassInfo
import no.f12.codenavigator.navigation.classinfo.ClassInfoFormatter
import no.f12.codenavigator.navigation.classinfo.FieldDetail
import no.f12.codenavigator.navigation.relations.implementors.InterfaceFormatter
import no.f12.codenavigator.navigation.relations.implementors.InterfaceRegistry
import no.f12.codenavigator.navigation.classinfo.MethodDetail
import no.f12.codenavigator.navigation.dsm.PackageDependencies
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.symbol.SymbolInfo
import no.f12.codenavigator.navigation.symbol.SymbolTableFormatter
import no.f12.codenavigator.navigation.dsm.CyclesFormatter
import no.f12.codenavigator.navigation.dsm.DsmFormatter
import no.f12.codenavigator.navigation.dsm.DsmMatrix
import no.f12.codenavigator.navigation.rank.RankedType
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.deadcode.DeadCode
import no.f12.codenavigator.navigation.stringconstant.StringConstantMatch
import no.f12.codenavigator.navigation.relations.hierarchy.SupertypeInfo
import no.f12.codenavigator.navigation.relations.hierarchy.SupertypeKind
import no.f12.codenavigator.navigation.metrics.MetricsResult
import no.f12.codenavigator.navigation.relations.hierarchy.TypeHierarchyFormatter
import no.f12.codenavigator.navigation.relations.hierarchy.TypeHierarchyResult
import no.f12.codenavigator.navigation.relations.callgraph.CollapsedUsage
import no.f12.codenavigator.navigation.relations.callgraph.SmartUsageResult
import no.f12.codenavigator.navigation.relations.callgraph.UsageFormatter
import no.f12.codenavigator.navigation.relations.callgraph.UsageSite
import no.f12.codenavigator.navigation.annotation.AnnotationMatch
import no.f12.codenavigator.navigation.relations.callgraph.AnnotationTag
import no.f12.codenavigator.navigation.changedsince.ChangedClassImpact
import no.f12.codenavigator.navigation.context.ContextResult
import no.f12.codenavigator.navigation.dsm.BalanceResult
import no.f12.codenavigator.navigation.dsm.PackageDistanceResult
import no.f12.codenavigator.navigation.dsm.CohesionResult
import no.f12.codenavigator.navigation.dsm.MoveSuggestionResult
import no.f12.codenavigator.navigation.dsm.StrengthResult
import no.f12.codenavigator.navigation.classmetrics.ClassMetricsResult

object LlmFormatter {

    fun formatClasses(classes: List<ClassInfo>): String = ClassInfoFormatter.formatLlm(classes)

    fun formatSymbols(symbols: List<SymbolInfo>): String = SymbolTableFormatter.formatLlm(symbols)

    fun formatClassDetails(details: List<ClassDetail>): String = ClassDetailFormatter.formatLlm(details)

    fun formatInterfaces(registry: InterfaceRegistry, interfaceNames: List<ClassName>): String =
        InterfaceFormatter.formatLlm(registry, interfaceNames)

    fun formatTypeHierarchy(results: List<TypeHierarchyResult>): String = TypeHierarchyFormatter.formatLlm(results)

    fun renderCallTrees(trees: List<CallTreeNode>, direction: CallDirection): String = buildString {
        trees.forEachIndexed { index, tree ->
            if (index > 0) appendLine()
            val lineRef = tree.lineNumber?.let { ":$it" } ?: ""
            append("${tree.method.qualifiedName} ${tree.sourceFile ?: "<unknown>"}$lineRef${formatAnnotationTags(tree.annotations)}")
            if (tree.children.isNotEmpty()) {
                renderChildren(tree.children, direction, 1)
            } else {
                appendLine()
                append("  ${direction.emptyMessage}")
                val hint = CallTreeFormatter.frameworkEntryPointHint(tree, direction)
                if (hint != null) append(" — $hint")
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
            .withInterpretation(HOTSPOT_INTERPRETATION)

    fun formatSize(entries: List<FileSizeEntry>): String =
        entries.joinToString("\n") { "${it.file} lines=${it.lines}" }

    fun formatDuplicates(groups: List<DuplicateGroup>): String =
        groups.joinToString("\n\n") { group ->
            "tokens=${group.tokenCount}\n" + group.locations.joinToString("\n") { "  ${it.file}:${it.startLine}-${it.endLine}" }
        }

    fun formatVolatility(result: PackageVolatilityResult): String =
        result.entries.joinToString("\n") { "${it.packageName} revisions=${it.revisions} churn=${it.totalChurn} files=${it.fileCount} avgRev=${"%.1f".format(it.avgRevisionsPerFile)}" }
            .withInterpretation(VOLATILITY_INTERPRETATION)

    fun formatCoupling(pairs: List<CoupledPair>): String =
        pairs.joinToString("\n") { "${it.entity} -- ${it.coupled} degree=${it.degree}% shared=${it.sharedRevs} avg=${it.avgRevs}${if (it.stale) " [stale]" else ""}" }
            .withInterpretation(COUPLING_INTERPRETATION)

    fun formatAge(ages: List<FileAge>): String =
        ages.joinToString("\n") { "${it.file} age=${it.ageMonths}months last=${it.lastChangeDate}" }
            .withInterpretation(AGE_INTERPRETATION)

    fun formatAuthors(modules: List<ModuleAuthors>): String =
        modules.joinToString("\n") { "${it.file} authors=${it.authors} revisions=${it.revisions}" }

    fun formatChurn(churn: List<FileChurn>): String =
        churn.joinToString("\n") { "${it.file} added=${it.added} deleted=${it.deleted} commits=${it.commits}" }
            .withInterpretation(CHURN_INTERPRETATION)

    fun formatUsages(usages: List<UsageSite>): String = UsageFormatter.formatLlm(usages)

    fun formatUsagesSummary(usages: List<UsageSite>): String = UsageFormatter.formatSummaryLlm(usages)

    fun formatCollapsedUsages(usages: List<CollapsedUsage>): String = UsageFormatter.formatCollapsedLlm(usages)

    fun formatSmartUsages(result: SmartUsageResult, collapsedUsages: List<CollapsedUsage>): String =
        UsageFormatter.formatSmartUsagesLlm(result, collapsedUsages)

    fun formatRank(ranked: List<RankedType>): String =
        ranked.joinToString("\n") { "%.4f".format(it.rank).let { rank -> "${it.className} rank=$rank in=${it.inDegree} out=${it.outDegree}" } }
            .withInterpretation(RANK_INTERPRETATION)

    private val DEAD_CODE_NOTE = "Note: Dead code detection is a hard problem with many edge cases (reflection, serialization, generated code). Use exclude=<regex> to filter out packages or classes you know are not dead."

    fun formatDead(dead: List<DeadCode>, scope: Scope = Scope.ALL): String {
        if (dead.isEmpty()) return ""
        val scopeNotice = if (scope == Scope.PROD) "Test classes excluded. Use scope=all to include test classes.\n" else ""
        return dead.joinToString("\n") { d ->
            val name = if (d.memberName != null) "${d.className}.${d.memberName}" else d.className.toString()
            "$name ${d.kind.name} ${d.sourceFile} confidence=${d.confidence.name} reason=${d.reason.name}"
        } + "\n\n" + scopeNotice + DEAD_CODE_NOTE
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
                    append("  method ${method.method.methodName} ${method.annotations.sorted().joinToString(",") { "@${it.simpleName()}" }}")
                }
                for (field in match.matchedFields) {
                    appendLine()
                    append("  field ${field.field.fieldName} ${field.annotations.sorted().joinToString(",") { "@${it.simpleName()}" }}")
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
        }.withInterpretation(COMPLEXITY_INTERPRETATION)

    fun formatCycles(
        details: List<CycleDetail>,
        displayPrefix: PackageName = PackageName(""),
        testInvolvement: TestInvolvement.Counts? = null,
    ): String = CyclesFormatter.formatLlm(details, displayPrefix, testInvolvement)

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

    fun formatContext(result: ContextResult): String = buildString {
        append(formatClassDetails(listOf(result.classDetail)))
        if (result.callers.isNotEmpty()) {
            appendLine()
            appendLine("callers:")
            append(renderCallTrees(result.callers, CallDirection.CALLERS))
        }
        if (result.callees.isNotEmpty()) {
            appendLine()
            appendLine("callees:")
            append(renderCallTrees(result.callees, CallDirection.CALLEES))
        }
        if (result.implementors.isNotEmpty()) {
            appendLine()
            append("implementors:${result.implementors.joinToString(",") { "${it.className}(${it.sourceFile})" }}")
        }
        if (result.implementedInterfaces.isNotEmpty()) {
            appendLine()
            append("implements:${result.implementedInterfaces.joinToString(",")}")
        }
    }.trimEnd()

    fun formatDistance(result: PackageDistanceResult): String {
        if (result.entries.isEmpty()) return ""
        return buildString {
            if (result.displayPrefix.isNotEmpty()) {
                appendLine("prefix:${result.displayPrefix}")
            }
            append(result.entries.joinToString("\n") { entry ->
                "${entry.source}->${entry.target} distance=${entry.distance} deps=${entry.dependencyCount}"
            })
        }.withInterpretation(DISTANCE_INTERPRETATION)
    }

    fun formatStrength(result: StrengthResult): String =
        result.entries.joinToString("\n") { entry ->
            buildString {
                append("${entry.source}->${entry.target} strength=${entry.strength} contract=${entry.contractCount} model=${entry.modelCount} functional=${entry.functionalCount}")
                if (entry.unknownCount > 0) {
                    append(" unknown=${entry.unknownCount}")
                }
            }
        }.withInterpretation(STRENGTH_INTERPRETATION)

    fun formatBalance(result: BalanceResult): String =
        result.entries.joinToString("\n") { entry ->
            buildString {
                append("${entry.source}->${entry.target} verdict=${entry.verdict} strength=${entry.strength} distance=${entry.distance} volatility=${entry.sourceVolatility}/${entry.targetVolatility}")
                if (entry.suggestion.isNotEmpty()) {
                    append(" | ${entry.suggestion}")
                }
            }
        }.withInterpretation(BALANCE_INTERPRETATION)

    fun formatDsm(matrix: DsmMatrix, moduleLabels: Map<PackageName, Set<String>> = emptyMap()): String =
        DsmFormatter.formatLlm(matrix, moduleLabels)

    fun formatDsmCycles(matrix: DsmMatrix, cycleFilter: Pair<PackageName, PackageName>? = null): String =
        DsmFormatter.formatCyclesLlm(matrix, cycleFilter)

    // --- Interpretation constants ---

    internal const val HOTSPOT_INTERPRETATION = "Interpretation: Files with high revision counts change frequently and are likely complexity hotspots. Prioritize refactoring files that are both hot (many revisions) and large (high churn). Cross-reference with coupling to find risky change clusters."

    internal const val COUPLING_INTERPRETATION = "Interpretation: High degree (%) means these files almost always change together. Intentional coupling (e.g., interface+implementation) is fine. Unintentional coupling suggests hidden dependencies or shared responsibilities that should be extracted. Pairs marked [stale] reference a file that no longer exists (rename/delete from git history) — ignore them."

    internal const val AGE_INTERPRETATION = "Interpretation: Old files (many months since last change) are either stable infrastructure or forgotten code. Very old files in active packages may indicate dead code or deferred maintenance."

    internal const val CHURN_INTERPRETATION = "Interpretation: High added+deleted lines indicate files undergoing significant rework. Files with high churn but few commits may have large, risky changes. Files with steady churn across many commits are actively maintained."

    internal const val VOLATILITY_INTERPRETATION = "Interpretation: Package-level volatility aggregates file changes. High-volatility packages with many outgoing dependencies are the riskiest — changes ripple outward. Stable packages (low volatility) with high fan-in are good dependency targets."

    internal const val RANK_INTERPRETATION = "Interpretation: PageRank identifies structurally central classes. High-rank classes are depended on transitively by many others — changes to them have wide impact. Low-rank classes are peripheral and safer to modify."

    internal const val COMPLEXITY_INTERPRETATION = "Interpretation: fan-out = total outgoing references (distinct classes). High fan-out means the class knows too much. fan-in = total incoming references. High fan-in means many classes depend on it — changes are risky. Classes with both high fan-in and high fan-out are prime refactoring targets."

    internal const val DISTANCE_INTERPRETATION = "Interpretation: Distance measures package name segment separation (e.g., com.a.b → com.x.y = distance 4). High distance + high dependency count suggests coupling between unrelated parts of the codebase that may benefit from an intermediate abstraction."

    internal const val STRENGTH_INTERPRETATION = "Interpretation: Integration strength levels — MODEL: only data classes cross the boundary (loosest). CONTRACT: interfaces/abstractions cross. FUNCTIONAL: concrete implementations cross (tightest). Higher strength at greater distance is a modularity concern."

    internal const val BALANCE_INTERPRETATION = "Interpretation: distance = number of architectural rings the edge crosses (not package-name nesting). BALANCED = coupling strength matches ring separation. TOLERABLE = suboptimal but low volatility reduces risk. DANGER = tight coupling across rings in volatile code — highest priority for refactoring. Composition roots (DI/wiring) are never DANGER. Focus on DANGER entries first."

    internal const val COHESION_INTERPRETATION = "Interpretation: Cohesion ratio = internal edges / total edges. COHESIVE (>0.5) = classes collaborate more with each other than with outsiders. REVIEW (<0.5) = package may contain unrelated classes. THIN_LAYER (0.0) = no internal collaboration, consider merging into a neighbor."

    internal const val MOVE_SUGGEST_INTERPRETATION = "Interpretation: Classes with more edges to another package than their own are potentially misplaced. High confidence + low own-edges = strong signal. Verify intent before moving — composition roots, drivers, and thin adapters are expected to have outward edges."

    internal const val CLASS_METRICS_INTERPRETATION = "Interpretation: TCC/LCC measure cohesion (fraction of method pairs sharing field access). HIGH (TCC>=0.7) = cohesive. MEDIUM (0.4-0.7) = acceptable. LOW (TCC<0.4, LCC>=0.7) = weakly cohesive but methods still chain-connect via shared fields. MONOLITH (TCC<0.4, LCC<0.7) = disjoint method groups — candidate for splitting into separate classes. WMC = summed cyclomatic complexity (higher = harder to test). CBO = distinct non-JDK/stdlib types referenced in signatures (higher = more context needed to understand the class). DIT = superclass chain depth (deeper = more inherited behavior to reason about)."

    private fun StringBuilder.renderChildren(children: List<CallTreeNode>, direction: CallDirection, depth: Int) {
        val indent = "  ".repeat(depth)
        for (node in children) {
            val lineRef = node.lineNumber?.let { ":$it" } ?: ""
            val sourceSetTag = node.sourceSet?.let { " [${it.label}]" } ?: ""
            val collapsedTag = CallTreeFormatter.collapsedImplementorsTag(node)
            appendLine()
            append("$indent${direction.arrow} ${node.method.qualifiedName} ${node.sourceFile ?: "<unknown>"}$lineRef${formatAnnotationTags(node.annotations)}$sourceSetTag$collapsedTag")
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

    fun formatCohesion(result: CohesionResult): String =
        result.entries.joinToString("\n") { entry ->
            "${entry.packageName} classes=${entry.classCount} internal=${entry.internalEdges} external=${entry.externalEdges} cohesion=${"%.2f".format(entry.cohesion)} verdict=${entry.verdict}"
        }.withInterpretation(COHESION_INTERPRETATION)

    fun formatMoveSuggestions(result: MoveSuggestionResult): String =
        result.suggestions.joinToString("\n") { s ->
            "${s.className.value} current=${s.currentPackage} suggested=${s.suggestedPackage} own=${s.edgesToCurrent} target=${s.edgesToSuggested} confidence=${"%.2f".format(s.confidence)}"
        }.withInterpretation(MOVE_SUGGEST_INTERPRETATION)

    fun formatClassMetrics(results: List<ClassMetricsResult>): String =
        results.joinToString("\n") { r ->
            "${r.className} methods=${r.totalMethods} tcc=${"%.2f".format(r.tcc)} lcc=${"%.2f".format(r.lcc)} verdict=${r.verdict} wmc=${r.wmc} cbo=${r.cbo} dit=${r.dit}"
        }.withInterpretation(CLASS_METRICS_INTERPRETATION)
}
