package no.f12.codenavigator.gradle

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.callgraph.FindUsagesConfig
import no.f12.codenavigator.navigation.callgraph.SmartUsageResult
import no.f12.codenavigator.navigation.callgraph.UsageCollapser
import no.f12.codenavigator.navigation.core.GroupBy
import no.f12.codenavigator.navigation.core.SkippedFileReporter
import no.f12.codenavigator.navigation.callgraph.UsageFormatter
import no.f12.codenavigator.navigation.callgraph.UsageScanner
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

        val result = UsageScanner.scanTagged(taggedDirs, ownerClass = config.ownerClass, method = config.method, field = config.field, type = config.type)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val afterPackageFilter = UsageScanner.filterOutsidePackage(result.data, config.outsidePackage)
        val afterSyntheticFilter = config.filterSyntheticCallers(afterPackageFilter)
        var usages = config.filterBySourceSet(afterSyntheticFilter)

        // Smart usages: detect interface and include implementations + implementor usages
        val classDirectories = taggedDirs.map { it.first }
        val targetType = config.type ?: config.ownerClass
        val interfaceRegistry = if (targetType != null) {
            val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/interface-registry.cache")
            InterfaceRegistryCache.getOrBuild(cacheFile, classDirectories).data
        } else null

        // Use findInterfaces() — same regex-based containsMatchIn resolution as all other commands
        val matchedInterfaces = if (interfaceRegistry != null && targetType != null) {
            interfaceRegistry.findInterfaces(targetType)
        } else emptyList()

        val implementations = matchedInterfaces.flatMap { interfaceRegistry!!.implementorsOf(it) }

        // When include-impls is set and target is an interface, also scan usages of implementors
        if (config.includeImpls && implementations.isNotEmpty()) {
            for (impl in implementations) {
                val implResult = UsageScanner.scanTagged(taggedDirs, ownerClass = impl.className.value, method = config.method, field = config.field, type = null)
                val implFiltered = config.filterBySourceSet(config.filterSyntheticCallers(UsageScanner.filterOutsidePackage(implResult.data, config.outsidePackage)))
                usages = usages + implFiltered
            }
        }

        val smartResult = SmartUsageResult(implementations, usages)

        if (usages.isEmpty() && implementations.isEmpty()) {
            val target = UsageFormatter.noResultsTarget(config.ownerClass, config.method, config.field, config.type)
            val hints = UsageFormatter.noResultsHints(config.ownerClass, config.method, config.field, config.type)
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No usages found for '$target'.", hints))
            return
        }

        val hasImpls = implementations.isNotEmpty()

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = {
                when {
                    config.groupBy == GroupBy.FILE -> UsageFormatter.formatSummary(usages)
                    config.raw -> UsageFormatter.format(usages)
                    hasImpls -> UsageFormatter.formatSmartUsages(smartResult, UsageCollapser.collapse(usages))
                    else -> UsageFormatter.formatCollapsed(UsageCollapser.collapse(usages))
                }
            },
            json = {
                when {
                    config.groupBy == GroupBy.FILE -> JsonFormatter.formatUsagesSummary(usages)
                    config.raw -> JsonFormatter.formatUsages(usages)
                    hasImpls -> JsonFormatter.formatSmartUsages(smartResult, UsageCollapser.collapse(usages))
                    else -> JsonFormatter.formatCollapsedUsages(UsageCollapser.collapse(usages))
                }
            },
            llm = {
                when {
                    config.groupBy == GroupBy.FILE -> LlmFormatter.formatUsagesSummary(usages)
                    config.raw -> LlmFormatter.formatUsages(usages)
                    hasImpls -> LlmFormatter.formatSmartUsages(smartResult, UsageCollapser.collapse(usages))
                    else -> LlmFormatter.formatCollapsedUsages(UsageCollapser.collapse(usages))
                }
            },
        ))
    }
}
