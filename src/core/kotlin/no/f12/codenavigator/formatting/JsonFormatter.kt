package no.f12.codenavigator.formatting

import no.f12.codenavigator.analysis.AuthorAnalysisFormatter
import no.f12.codenavigator.analysis.ChangeCouplingFormatter
import no.f12.codenavigator.analysis.ChurnFormatter
import no.f12.codenavigator.analysis.CodeAgeFormatter
import no.f12.codenavigator.analysis.CoupledPair
import no.f12.codenavigator.analysis.DuplicateFormatter
import no.f12.codenavigator.analysis.DuplicateGroup
import no.f12.codenavigator.analysis.FileAge
import no.f12.codenavigator.analysis.FileChurn
import no.f12.codenavigator.analysis.FileSizeEntry
import no.f12.codenavigator.analysis.FileSizeFormatter
import no.f12.codenavigator.analysis.Hotspot
import no.f12.codenavigator.analysis.HotspotFormatter
import no.f12.codenavigator.analysis.ModuleAuthors
import no.f12.codenavigator.analysis.PackageVolatilityFormatter
import no.f12.codenavigator.analysis.PackageVolatilityResult
import no.f12.codenavigator.navigation.relations.callgraph.AnnotationTag
import no.f12.codenavigator.navigation.relations.callgraph.CallDirection
import no.f12.codenavigator.navigation.relations.callgraph.CallTreeFormatter
import no.f12.codenavigator.navigation.relations.callgraph.CallTreeNode
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.classinfo.AnnotationDetail
import no.f12.codenavigator.navigation.classinfo.ClassDetail
import no.f12.codenavigator.navigation.classinfo.ClassDetailFormatter
import no.f12.codenavigator.navigation.classinfo.ClassInfo
import no.f12.codenavigator.navigation.classinfo.ClassInfoFormatter
import no.f12.codenavigator.navigation.relations.hierarchy.TypeHierarchyFormatter
import no.f12.codenavigator.navigation.relations.implementors.InterfaceFormatter
import no.f12.codenavigator.navigation.relations.implementors.InterfaceRegistry
import no.f12.codenavigator.navigation.dsm.PackageDependencies
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.symbol.SymbolInfo
import no.f12.codenavigator.navigation.symbol.SymbolTableFormatter
import no.f12.codenavigator.navigation.dsm.DsmFormatter
import no.f12.codenavigator.navigation.dsm.DsmMatrix
import no.f12.codenavigator.navigation.rank.RankedType
import no.f12.codenavigator.navigation.complexity.ClassComplexity
import no.f12.codenavigator.navigation.dsm.CycleDetail
import no.f12.codenavigator.navigation.dsm.CyclesFormatter
import no.f12.codenavigator.navigation.dsm.ClassRingAssignment
import no.f12.codenavigator.navigation.dsm.EmergentRingFormatter
import no.f12.codenavigator.navigation.dsm.RingAssignment
import no.f12.codenavigator.navigation.dsm.RingFormatter
import no.f12.codenavigator.navigation.dsm.TestInvolvement
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.deadcode.DeadCode
import no.f12.codenavigator.navigation.metrics.MetricsResult
import no.f12.codenavigator.navigation.stringconstant.StringConstantMatch
import no.f12.codenavigator.navigation.relations.hierarchy.SupertypeInfo
import no.f12.codenavigator.navigation.relations.hierarchy.TypeHierarchyResult
import no.f12.codenavigator.navigation.relations.callgraph.CollapsedUsage
import no.f12.codenavigator.navigation.relations.callgraph.SmartUsageResult
import no.f12.codenavigator.navigation.relations.callgraph.UsageFormatter
import no.f12.codenavigator.navigation.relations.callgraph.UsageSite
import no.f12.codenavigator.navigation.annotation.AnnotationMatch
import no.f12.codenavigator.navigation.changedsince.ChangedClassImpact
import no.f12.codenavigator.navigation.context.ContextResult
import no.f12.codenavigator.navigation.dsm.BalanceFormatter
import no.f12.codenavigator.navigation.dsm.CohesionFormatter
import no.f12.codenavigator.navigation.dsm.MoveSuggestFormatter
import no.f12.codenavigator.navigation.dsm.PackageDependencyFormatter
import no.f12.codenavigator.navigation.dsm.PackageDistanceFormatter
import no.f12.codenavigator.navigation.dsm.PackageDistanceResult
import no.f12.codenavigator.navigation.dsm.BalanceResult
import no.f12.codenavigator.navigation.dsm.CohesionResult
import no.f12.codenavigator.navigation.dsm.MoveSuggestionResult
import no.f12.codenavigator.navigation.dsm.StrengthFormatter
import no.f12.codenavigator.navigation.dsm.StrengthResult
import no.f12.codenavigator.navigation.classmetrics.ClassMetricsResult

object JsonFormatter {

    fun formatClasses(classes: List<ClassInfo>): String = ClassInfoFormatter.formatJson(classes)

    fun formatSymbols(symbols: List<SymbolInfo>): String = SymbolTableFormatter.formatJson(symbols)

    fun formatClassDetails(details: List<ClassDetail>): String = ClassDetailFormatter.formatJson(details)

    fun renderCallTrees(trees: List<CallTreeNode>, direction: CallDirection? = null): String =
        jsonArray(trees) { node -> renderCallNode(node, direction, isRoot = true) }

    fun formatInterfaces(registry: InterfaceRegistry, interfaceNames: List<ClassName>): String =
        InterfaceFormatter.formatJson(registry, interfaceNames)

    fun formatTypeHierarchy(results: List<TypeHierarchyResult>): String = TypeHierarchyFormatter.formatJson(results)

    fun formatPackageDeps(
        deps: PackageDependencies,
        packageNames: List<PackageName>,
        reverse: Boolean = false,
    ): String = PackageDependencyFormatter.formatJson(deps, packageNames, reverse)

    fun formatHotspots(hotspots: List<Hotspot>): String = HotspotFormatter.formatJson(hotspots)

    fun formatSize(entries: List<FileSizeEntry>): String = FileSizeFormatter.formatJson(entries)

    fun formatDuplicates(groups: List<DuplicateGroup>): String = DuplicateFormatter.formatJson(groups)

    fun formatVolatility(result: PackageVolatilityResult): String = PackageVolatilityFormatter.formatJson(result)

    fun formatCoupling(pairs: List<CoupledPair>): String = ChangeCouplingFormatter.formatJson(pairs)

    fun formatAge(ages: List<FileAge>): String = CodeAgeFormatter.formatJson(ages)

    fun formatAuthors(modules: List<ModuleAuthors>): String = AuthorAnalysisFormatter.formatJson(modules)

    fun formatChurn(churn: List<FileChurn>): String = ChurnFormatter.formatJson(churn)

    fun formatDsm(matrix: DsmMatrix, moduleLabels: Map<PackageName, Set<String>> = emptyMap()): String =
        DsmFormatter.formatJson(matrix, moduleLabels)

    fun formatDsmCycles(matrix: DsmMatrix, cycleFilter: Pair<PackageName, PackageName>? = null): String =
        DsmFormatter.formatCyclesJson(matrix, cycleFilter)

    fun formatUsages(usages: List<UsageSite>): String = UsageFormatter.formatJson(usages)

    fun formatUsagesSummary(usages: List<UsageSite>): String = UsageFormatter.formatSummaryJson(usages)

    fun formatCollapsedUsages(usages: List<CollapsedUsage>): String = UsageFormatter.formatCollapsedJson(usages)

    fun formatSmartUsages(result: SmartUsageResult, collapsedUsages: List<CollapsedUsage>): String =
        UsageFormatter.formatSmartUsagesJson(result, collapsedUsages)

    fun formatRank(ranked: List<RankedType>): String =
        jsonArray(ranked) { r ->
            jsonObject(
                "className" to r.className.toString(),
                "rank" to r.rank,
                "inDegree" to r.inDegree,
                "outDegree" to r.outDegree,
            )
        }

    fun formatDead(dead: List<DeadCode>, @Suppress("UNUSED_PARAMETER") scope: Scope = Scope.ALL): String =
        jsonArray(dead) { d ->
            jsonObject(
                "className" to d.className.toString(),
                "memberName" to d.memberName,
                "kind" to d.kind.name.lowercase(),
                "sourceFile" to d.sourceFile,
                "confidence" to d.confidence.name.lowercase(),
                "reason" to d.reason.name.lowercase(),
            )
        }

    fun formatStringConstants(matches: List<StringConstantMatch>): String =
        jsonArray(matches) { m ->
            jsonObject(
                "className" to m.className.toString(),
                "methodName" to m.methodName,
                "value" to m.value,
                "sourceFile" to m.sourceFile,
            )
        }

    fun formatChangedSince(impacts: List<ChangedClassImpact>, unresolved: List<String>): String =
        jsonObject(
            "changedClasses" to JsonRaw(jsonArray(impacts) { impact ->
                jsonObject(
                    "className" to impact.className.toString(),
                    "sourceFile" to impact.sourceFile,
                    "callers" to JsonRaw(jsonArray(impact.callers.sortedBy { "${it.className}.${it.methodName}" }) { caller ->
                        jsonObject(
                            "className" to caller.className.toString(),
                            "method" to caller.methodName,
                        )
                    }),
                )
            }),
            "unresolvedFiles" to JsonRaw(jsonStringArray(unresolved)),
        )

    fun formatAnnotations(matches: List<AnnotationMatch>): String =
        jsonArray(matches) { match ->
            jsonObject(
                "className" to match.className.value,
                "sourceFile" to match.sourceFile,
                "classAnnotations" to JsonRaw(jsonStringArray(match.classAnnotations.sorted().map { it.value })),
                "methods" to JsonRaw(jsonArray(match.matchedMethods) { method ->
                    jsonObject(
                        "method" to method.method.methodName,
                        "annotations" to JsonRaw(jsonStringArray(method.annotations.sorted().map { it.value })),
                    )
                }),
                "fields" to JsonRaw(jsonArray(match.matchedFields) { field ->
                    jsonObject(
                        "field" to field.field.fieldName,
                        "annotations" to JsonRaw(jsonStringArray(field.annotations.sorted().map { it.value })),
                    )
                }),
            )
        }

    fun formatComplexity(results: List<ClassComplexity>): String =
        jsonArray(results) { c ->
            jsonObject(
                "className" to c.className.toString(),
                "sourceFile" to c.sourceFile,
                "fanOut" to c.fanOut,
                "fanIn" to c.fanIn,
                "distinctOutgoingClasses" to c.distinctOutgoingClasses,
                "distinctIncomingClasses" to c.distinctIncomingClasses,
                "outgoingByClass" to JsonRaw(jsonArray(c.outgoingByClass) { (cls, count) ->
                    jsonObject("className" to cls.toString(), "count" to count)
                }),
                "incomingByClass" to JsonRaw(jsonArray(c.incomingByClass) { (cls, count) ->
                    jsonObject("className" to cls.toString(), "count" to count)
                }),
            )
        }

    fun formatCycles(
        details: List<CycleDetail>,
        displayPrefix: PackageName = PackageName(""),
        testInvolvement: TestInvolvement.Counts? = null,
    ): String = CyclesFormatter.formatJson(details, displayPrefix, testInvolvement)

    fun formatRings(
        result: RingAssignment,
        ringNames: Map<Int, String> = emptyMap(),
        configNotice: String? = null,
    ): String = RingFormatter.formatJson(result, ringNames, configNotice)

    fun formatEmergentRings(
        result: ClassRingAssignment,
        ringNames: Map<Int, String> = emptyMap(),
        hasHints: Boolean = false,
        testInvolvement: TestInvolvement.Counts? = null,
    ): String = EmergentRingFormatter.formatJson(result, ringNames, hasHints, testInvolvement)

    fun formatMetrics(metrics: MetricsResult): String =
        jsonObject(
            "totalClasses" to metrics.totalClasses,
            "packageCount" to metrics.packageCount,
            "averageFanIn" to metrics.averageFanIn,
            "averageFanOut" to metrics.averageFanOut,
            "cycleCount" to metrics.cycleCount,
            "deadClassCount" to metrics.deadClassCount,
            "deadMethodCount" to metrics.deadMethodCount,
            "topHotspots" to JsonRaw(jsonArray(metrics.topHotspots) { h ->
                jsonObject(
                    "file" to h.file,
                    "revisions" to h.revisions,
                    "totalChurn" to h.totalChurn,
                )
            }),
        )

    fun formatContext(result: ContextResult): String =
        jsonObject(
            "classDetail" to JsonRaw(formatClassDetails(listOf(result.classDetail))),
            "callers" to JsonRaw(renderCallTrees(result.callers, CallDirection.CALLERS)),
            "callees" to JsonRaw(renderCallTrees(result.callees, CallDirection.CALLEES)),
            "implementors" to JsonRaw(jsonArray(result.implementors) { impl ->
                jsonObject("className" to impl.className.toString(), "sourceFile" to impl.sourceFile)
            }),
            "implementedInterfaces" to JsonRaw(jsonStringArray(result.implementedInterfaces.map { it.toString() })),
        )

    fun formatDistance(result: PackageDistanceResult): String = PackageDistanceFormatter.formatJson(result)

    fun formatStrength(result: StrengthResult): String = StrengthFormatter.formatJson(result)

    fun formatBalance(result: BalanceResult): String = BalanceFormatter.formatJson(result)

    private fun renderCallNode(node: CallTreeNode, direction: CallDirection? = null, isRoot: Boolean = false): String {
        val children = jsonArray(node.children) { child -> renderCallNode(child) }
        val hint = if (isRoot && direction != null && node.children.isEmpty()) {
            CallTreeFormatter.frameworkEntryPointHint(node, direction)
        } else {
            null
        }
        return jsonObject(
            "method" to node.method.qualifiedName,
            "sourceFile" to node.sourceFile,
            "lineNumber" to node.lineNumber,
            "sourceSet" to node.sourceSet?.label,
            "annotations" to if (node.annotations.isNotEmpty()) JsonRaw(renderAnnotationTags(node.annotations)) else null,
            "children" to JsonRaw(children),
            "frameworkEntryPointHint" to hint,
            "collapsedImplementorCount" to if (node.collapsedImplementorCount > 0) node.collapsedImplementorCount else null,
        )
    }

    private fun renderAnnotationTags(tags: List<AnnotationTag>): String =
        tags.joinToString(",", "[", "]") { tag ->
            val params = if (tag.parameters.isNotEmpty()) {
                JsonRaw(jsonObject(*tag.parameters.map { (k, v) -> k to v }.toTypedArray()))
            } else {
                null
            }
            jsonObject(
                "name" to tag.name.value,
                "framework" to tag.framework,
                "parameters" to params,
            )
        }

    fun formatCohesion(result: CohesionResult): String = CohesionFormatter.formatJson(result)

    fun formatClassMetrics(results: List<ClassMetricsResult>): String =
        jsonArray(results) { r ->
            jsonObject(
                "className" to r.className.toString(),
                "package" to r.packageName.toString(),
                "totalMethods" to r.totalMethods,
                "tcc" to r.tcc,
                "lcc" to r.lcc,
                "verdict" to r.verdict.name,
                "wmc" to r.wmc,
                "cbo" to r.cbo,
                "dit" to r.dit,
            )
        }

    fun formatMoveSuggestions(result: MoveSuggestionResult): String = MoveSuggestFormatter.formatJson(result)
}
