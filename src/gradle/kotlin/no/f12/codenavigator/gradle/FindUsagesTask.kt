package no.f12.codenavigator.gradle

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.callgraph.FindUsagesConfig
import no.f12.codenavigator.navigation.callgraph.SmartUsageResult
import no.f12.codenavigator.navigation.callgraph.UsageCollapser
import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.GroupBy
import no.f12.codenavigator.navigation.core.SkippedFileReporter
import no.f12.codenavigator.navigation.callgraph.UsageFormatter
import no.f12.codenavigator.navigation.callgraph.UsageScanner
import no.f12.codenavigator.navigation.classinfo.ClassIndexCache
import no.f12.codenavigator.navigation.core.TypeMatcher
import no.f12.codenavigator.navigation.interfaces.InterfaceRegistryCache

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class FindUsagesTask : DefaultTask() {

    @TaskAction
    fun findUsages() {
        val config = try {
            FindUsagesConfig.parse(
                project.buildPropertyMap(TaskRegistry.FIND_USAGES),
            )
        } catch (e: IllegalArgumentException) {
            val taskName = TaskRegistry.FIND_USAGES.taskName(BuildTool.GRADLE)
            throw GradleException(
                "${e.message}\n" +
                    "Usage: ./gradlew $taskName -Powner-class=<class> [-Pmethod=<name>] [-Pfield=<name>]\n" +
                    "       ./gradlew $taskName -Ptype=<class>",
            )
        }

        val taggedDirs = project.taggedClassDirectories()
        val classDirectories = taggedDirs.map { it.first }
        val buildDir = project.layout.buildDirectory.asFile.get()

        // Phase 1: Match — resolve pattern to concrete class names
        val targetType = config.type ?: config.ownerClass
        val allClassNames = ClassIndexCache.getOrBuild(
            File(buildDir, "cnav/class-index.cache"), classDirectories,
        ).data.map { it.className }

        val resolvedTypes: Set<ClassName> = if (targetType != null) {
            TypeMatcher.resolve(targetType, allClassNames)
        } else emptySet()

        // Phase 2: Enrich — find interfaces among resolved types, expand with implementors
        val interfaceRegistry = if (resolvedTypes.isNotEmpty()) {
            InterfaceRegistryCache.getOrBuild(File(buildDir, "cnav/interface-registry.cache"), classDirectories).data
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
        val reportFile = File(buildDir, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val afterPackageFilter = UsageScanner.filterOutsidePackage(result.data, config.outsidePackage)
        val afterSyntheticFilter = config.filterSyntheticCallers(afterPackageFilter)
        val usages = config.filterBySourceSet(afterSyntheticFilter)

        if (usages.isEmpty() && implementations.isEmpty()) {
            val target = UsageFormatter.noResultsTarget(config.ownerClass, config.method, config.field, config.type)
            val hints = UsageFormatter.noResultsHints(config.ownerClass, config.method, config.field, config.type)
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No usages found for '$target'.", hints))
            return
        }

        val interfaceTypeSet = matchedInterfaces.toSet()
        val collapsed = if (!config.raw) UsageCollapser.collapse(usages, interfaceTypeSet) else emptyList()

        // Derive matched types from collapsed output — use topLevelClass to merge inner classes
        val matchedTypes = collapsed.map { it.targetOwner.topLevelClass() }.distinct().sorted()
        val smartResult = SmartUsageResult(implementations, usages, matchedTypes, interfaceTypeSet)

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = {
                when {
                    config.groupBy == GroupBy.FILE -> UsageFormatter.formatSummary(usages)
                    config.raw -> UsageFormatter.format(usages)
                    else -> UsageFormatter.formatSmartUsages(smartResult, collapsed)
                }
            },
            json = {
                when {
                    config.groupBy == GroupBy.FILE -> JsonFormatter.formatUsagesSummary(usages)
                    config.raw -> JsonFormatter.formatUsages(usages)
                    else -> JsonFormatter.formatSmartUsages(smartResult, collapsed)
                }
            },
            llm = {
                when {
                    config.groupBy == GroupBy.FILE -> LlmFormatter.formatUsagesSummary(usages)
                    config.raw -> LlmFormatter.formatUsages(usages)
                    else -> LlmFormatter.formatSmartUsages(smartResult, collapsed)
                }
            },
        ))
    }
}
