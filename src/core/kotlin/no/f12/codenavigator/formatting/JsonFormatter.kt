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
import no.f12.codenavigator.navigation.deadcode.DeadCodeFormatter
import no.f12.codenavigator.navigation.metrics.MetricsFormatter
import no.f12.codenavigator.navigation.metrics.MetricsResult
import no.f12.codenavigator.navigation.stringconstant.StringConstantFormatter
import no.f12.codenavigator.navigation.stringconstant.StringConstantMatch
import no.f12.codenavigator.navigation.relations.hierarchy.TypeHierarchyResult
import no.f12.codenavigator.navigation.relations.callgraph.CollapsedUsage
import no.f12.codenavigator.navigation.relations.callgraph.SmartUsageResult
import no.f12.codenavigator.navigation.relations.callgraph.UsageFormatter
import no.f12.codenavigator.navigation.relations.callgraph.UsageSite
import no.f12.codenavigator.navigation.annotation.AnnotationMatch
import no.f12.codenavigator.navigation.annotation.AnnotationQueryFormatter
import no.f12.codenavigator.navigation.changedsince.ChangedClassImpact
import no.f12.codenavigator.navigation.changedsince.ChangedSinceFormatter
import no.f12.codenavigator.navigation.context.ContextFormatter
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
import no.f12.codenavigator.navigation.classmetrics.ClassMetricsFormatter
import no.f12.codenavigator.navigation.classmetrics.ClassMetricsResult
import no.f12.codenavigator.navigation.rank.RankFormatter
import no.f12.codenavigator.navigation.complexity.ComplexityFormatter

object JsonFormatter {

    fun formatClasses(classes: List<ClassInfo>): String = ClassInfoFormatter.formatJson(classes)

    fun formatSymbols(symbols: List<SymbolInfo>): String = SymbolTableFormatter.formatJson(symbols)

    fun formatClassDetails(details: List<ClassDetail>): String = ClassDetailFormatter.formatJson(details)

    fun renderCallTrees(trees: List<CallTreeNode>, direction: CallDirection? = null): String =
        CallTreeFormatter.formatJson(trees, direction)

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

    fun formatRank(ranked: List<RankedType>): String = RankFormatter.formatJson(ranked)

    fun formatDead(dead: List<DeadCode>, scope: Scope = Scope.ALL): String = DeadCodeFormatter.formatJson(dead, scope)

    fun formatStringConstants(matches: List<StringConstantMatch>): String = StringConstantFormatter.formatJson(matches)

    fun formatChangedSince(impacts: List<ChangedClassImpact>, unresolved: List<String>): String =
        ChangedSinceFormatter.formatJson(impacts, unresolved)

    fun formatAnnotations(matches: List<AnnotationMatch>): String = AnnotationQueryFormatter.formatJson(matches)

    fun formatComplexity(results: List<ClassComplexity>): String = ComplexityFormatter.formatJson(results)

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

    fun formatMetrics(metrics: MetricsResult): String = MetricsFormatter.formatJson(metrics)

    fun formatContext(result: ContextResult): String = ContextFormatter.formatJson(result)

    fun formatDistance(result: PackageDistanceResult): String = PackageDistanceFormatter.formatJson(result)

    fun formatStrength(result: StrengthResult): String = StrengthFormatter.formatJson(result)

    fun formatBalance(result: BalanceResult): String = BalanceFormatter.formatJson(result)

    fun formatCohesion(result: CohesionResult): String = CohesionFormatter.formatJson(result)

    fun formatClassMetrics(results: List<ClassMetricsResult>): String = ClassMetricsFormatter.formatJson(results)

    fun formatMoveSuggestions(result: MoveSuggestionResult): String = MoveSuggestFormatter.formatJson(result)
}
