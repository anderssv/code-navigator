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
import no.f12.codenavigator.navigation.complexity.ClassComplexity
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
import no.f12.codenavigator.navigation.deadcode.DeadCodeFormatter
import no.f12.codenavigator.navigation.stringconstant.StringConstantFormatter
import no.f12.codenavigator.navigation.stringconstant.StringConstantMatch
import no.f12.codenavigator.navigation.relations.hierarchy.SupertypeInfo
import no.f12.codenavigator.navigation.relations.hierarchy.SupertypeKind
import no.f12.codenavigator.navigation.metrics.MetricsFormatter
import no.f12.codenavigator.navigation.metrics.MetricsResult
import no.f12.codenavigator.navigation.relations.hierarchy.TypeHierarchyFormatter
import no.f12.codenavigator.navigation.relations.hierarchy.TypeHierarchyResult
import no.f12.codenavigator.navigation.relations.callgraph.CollapsedUsage
import no.f12.codenavigator.navigation.relations.callgraph.SmartUsageResult
import no.f12.codenavigator.navigation.relations.callgraph.UsageFormatter
import no.f12.codenavigator.navigation.relations.callgraph.UsageSite
import no.f12.codenavigator.navigation.annotation.AnnotationMatch
import no.f12.codenavigator.navigation.annotation.AnnotationQueryFormatter
import no.f12.codenavigator.navigation.relations.callgraph.AnnotationTag
import no.f12.codenavigator.navigation.changedsince.ChangedClassImpact
import no.f12.codenavigator.navigation.changedsince.ChangedSinceFormatter
import no.f12.codenavigator.navigation.context.ContextFormatter
import no.f12.codenavigator.navigation.context.ContextResult
import no.f12.codenavigator.navigation.dsm.BalanceFormatter
import no.f12.codenavigator.navigation.dsm.BalanceResult
import no.f12.codenavigator.navigation.dsm.CohesionFormatter
import no.f12.codenavigator.navigation.dsm.MoveSuggestFormatter
import no.f12.codenavigator.navigation.dsm.PackageDependencyFormatter
import no.f12.codenavigator.navigation.dsm.PackageDistanceFormatter
import no.f12.codenavigator.navigation.dsm.PackageDistanceResult
import no.f12.codenavigator.navigation.rank.RankFormatter
import no.f12.codenavigator.navigation.complexity.ComplexityFormatter
import no.f12.codenavigator.navigation.classmetrics.ClassMetricsFormatter
import no.f12.codenavigator.navigation.dsm.CohesionResult
import no.f12.codenavigator.navigation.dsm.MoveSuggestionResult
import no.f12.codenavigator.navigation.dsm.StrengthFormatter
import no.f12.codenavigator.navigation.dsm.StrengthResult
import no.f12.codenavigator.navigation.classmetrics.ClassMetricsResult

object LlmFormatter {

    fun formatClasses(classes: List<ClassInfo>): String = ClassInfoFormatter.formatLlm(classes)

    fun formatSymbols(symbols: List<SymbolInfo>): String = SymbolTableFormatter.formatLlm(symbols)

    fun formatClassDetails(details: List<ClassDetail>): String = ClassDetailFormatter.formatLlm(details)

    fun formatInterfaces(registry: InterfaceRegistry, interfaceNames: List<ClassName>): String =
        InterfaceFormatter.formatLlm(registry, interfaceNames)

    fun formatTypeHierarchy(results: List<TypeHierarchyResult>): String = TypeHierarchyFormatter.formatLlm(results)

    fun renderCallTrees(trees: List<CallTreeNode>, direction: CallDirection): String =
        CallTreeFormatter.formatLlm(trees, direction)

    fun formatPackageDeps(deps: PackageDependencies, packageNames: List<PackageName>, reverse: Boolean): String =
        PackageDependencyFormatter.formatLlm(deps, packageNames, reverse)

    fun formatHotspots(hotspots: List<Hotspot>): String = HotspotFormatter.formatLlm(hotspots)

    fun formatSize(entries: List<FileSizeEntry>): String = FileSizeFormatter.formatLlm(entries)

    fun formatDuplicates(groups: List<DuplicateGroup>): String = DuplicateFormatter.formatLlm(groups)

    fun formatVolatility(result: PackageVolatilityResult): String = PackageVolatilityFormatter.formatLlm(result)

    fun formatCoupling(pairs: List<CoupledPair>): String = ChangeCouplingFormatter.formatLlm(pairs)

    fun formatAge(ages: List<FileAge>): String = CodeAgeFormatter.formatLlm(ages)

    fun formatAuthors(modules: List<ModuleAuthors>): String = AuthorAnalysisFormatter.formatLlm(modules)

    fun formatChurn(churn: List<FileChurn>): String = ChurnFormatter.formatLlm(churn)

    fun formatUsages(usages: List<UsageSite>): String = UsageFormatter.formatLlm(usages)

    fun formatUsagesSummary(usages: List<UsageSite>): String = UsageFormatter.formatSummaryLlm(usages)

    fun formatCollapsedUsages(usages: List<CollapsedUsage>): String = UsageFormatter.formatCollapsedLlm(usages)

    fun formatSmartUsages(result: SmartUsageResult, collapsedUsages: List<CollapsedUsage>): String =
        UsageFormatter.formatSmartUsagesLlm(result, collapsedUsages)

    fun formatRank(ranked: List<RankedType>): String = RankFormatter.formatLlm(ranked)

    fun formatDead(dead: List<DeadCode>, scope: Scope = Scope.ALL): String = DeadCodeFormatter.formatLlm(dead, scope)

    fun formatStringConstants(matches: List<StringConstantMatch>): String = StringConstantFormatter.formatLlm(matches)

    fun formatChangedSince(impacts: List<ChangedClassImpact>, unresolved: List<String>): String =
        ChangedSinceFormatter.formatLlm(impacts, unresolved)

    fun formatAnnotations(matches: List<AnnotationMatch>): String = AnnotationQueryFormatter.formatLlm(matches)

    fun formatComplexity(results: List<ClassComplexity>): String = ComplexityFormatter.formatLlm(results)

    fun formatCycles(
        details: List<CycleDetail>,
        displayPrefix: PackageName = PackageName(""),
        testInvolvement: TestInvolvement.Counts? = null,
    ): String = CyclesFormatter.formatLlm(details, displayPrefix, testInvolvement)

    fun formatMetrics(metrics: MetricsResult): String = MetricsFormatter.formatLlm(metrics)

    fun formatContext(result: ContextResult): String = ContextFormatter.formatLlm(result)

    fun formatDistance(result: PackageDistanceResult): String = PackageDistanceFormatter.formatLlm(result)

    fun formatStrength(result: StrengthResult): String = StrengthFormatter.formatLlm(result)

    fun formatBalance(result: BalanceResult): String = BalanceFormatter.formatLlm(result)

    fun formatDsm(matrix: DsmMatrix, moduleLabels: Map<PackageName, Set<String>> = emptyMap()): String =
        DsmFormatter.formatLlm(matrix, moduleLabels)

    fun formatDsmCycles(matrix: DsmMatrix, cycleFilter: Pair<PackageName, PackageName>? = null): String =
        DsmFormatter.formatCyclesLlm(matrix, cycleFilter)

    fun formatCohesion(result: CohesionResult): String = CohesionFormatter.formatLlm(result)

    fun formatMoveSuggestions(result: MoveSuggestionResult): String = MoveSuggestFormatter.formatLlm(result)

    fun formatClassMetrics(results: List<ClassMetricsResult>): String = ClassMetricsFormatter.formatLlm(results)
}
