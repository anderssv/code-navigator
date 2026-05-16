package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.classinfo.ClassIndexCache
import no.f12.codenavigator.navigation.relations.implementors.ImplementorInfo
import no.f12.codenavigator.navigation.relations.implementors.InterfaceRegistryCache
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.SourceSet
import no.f12.codenavigator.navigation.types.TypeMatcher
import java.io.File

data class FindUsagesOutput(
    val usages: List<UsageSite>,
    val implementations: List<ImplementorInfo>,
    val collapsed: List<CollapsedUsage>,
    val matchedTypes: List<ClassName>,
    val interfaceTypes: Set<ClassName>,
    val skippedFileWarning: String?,
) {
    fun toSmartResult() = SmartUsageResult(implementations, usages, matchedTypes, interfaceTypes)
}

object FindUsagesOrchestrator {

    fun run(
        config: FindUsagesConfig,
        taggedDirs: List<Pair<File, SourceSet?>>,
        cacheDir: File,
    ): FindUsagesOutput {
        val classDirectories = taggedDirs.map { it.first }

        // Phase 1: Match — resolve pattern to concrete class names
        val targetType = config.type ?: config.ownerClass
        val allClassNames = ClassIndexCache.getOrBuild(
            File(cacheDir, "class-index.cache"), classDirectories,
        ).data.map { it.className }

        val resolvedTypes: Set<ClassName> = if (targetType != null) {
            TypeMatcher.resolve(targetType, allClassNames)
        } else emptySet()

        // Phase 2: Enrich — find interfaces among resolved types, expand with implementors
        val interfaceRegistry = if (resolvedTypes.isNotEmpty()) {
            InterfaceRegistryCache.getOrBuild(File(cacheDir, "interface-registry.cache"), classDirectories).data
        } else null

        val matchedInterfaces = resolvedTypes.filter { interfaceRegistry?.isInterface(it) == true }
        val implementations = matchedInterfaces.flatMap { interfaceRegistry!!.implementorsOf(it) }

        val scanTargets = if (config.includeImpls && implementations.isNotEmpty()) {
            resolvedTypes + implementations.map { it.className }.toSet()
        } else {
            resolvedTypes
        }

        // Phase 3: Scan — single pass with resolved class names
        val scanMatcher = if (scanTargets.isNotEmpty()) TypeMatcher.SetMatcher(scanTargets) else null
        val ownerMatcher = if (config.ownerClass != null) scanMatcher else null
        val typeMatcher = if (config.type != null) scanMatcher else null

        val result = UsageScanner.scanTagged(taggedDirs, ownerMatcher = ownerMatcher, method = config.method, field = config.field, typeMatcher = typeMatcher)
        val reportFile = File(cacheDir, "skipped-files.txt")
        val skippedWarning = SkippedFileReporter.report(result.skippedFiles, reportFile)
        val afterPackageFilter = UsageScanner.filterOutsidePackage(result.data, config.outsidePackage)
        val afterSyntheticFilter = config.filterSyntheticCallers(afterPackageFilter)
        val usages = config.filterBySourceSet(afterSyntheticFilter)

        val interfaceTypeSet = matchedInterfaces.toSet()
        val collapsed = if (!config.raw) UsageCollapser.collapse(usages, interfaceTypeSet) else emptyList()

        val matchedTypes = collapsed.map { it.targetOwner.topLevelClass() }.distinct().sorted()

        return FindUsagesOutput(
            usages = usages,
            implementations = implementations,
            collapsed = collapsed,
            matchedTypes = matchedTypes,
            interfaceTypes = interfaceTypeSet,
            skippedFileWarning = skippedWarning,
        )
    }
}
