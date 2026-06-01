package no.f12.codenavigator.gradle

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.relations.callgraph.FindUsagesConfig
import no.f12.codenavigator.navigation.relations.callgraph.FindUsagesOrchestrator
import no.f12.codenavigator.navigation.types.GroupBy
import no.f12.codenavigator.navigation.relations.callgraph.UsageFormatter

import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class FindUsagesTask : CodeNavigatorTask() {

    @Option(option = "owner-class", description = "Class name or pattern — matches method call and field owners (camelCase-aware: MyService matches com.example.MyService)")
    @get:Internal
    var ownerClass: String? = null

    @Option(option = "method", description = "Method name regex")
    @get:Internal
    var method: String? = null

    @Option(option = "field", description = "Field/property name — also finds getter/setter calls")
    @get:Internal
    var field: String? = null

    @Option(option = "type", description = "Find ALL references to a class: calls, fields, casts, signatures (camelCase-aware)")
    @get:Internal
    var type: String? = null

    @Option(option = "outside-package", description = "Exclude callers inside this package")
    @get:Internal
    var outsidePackage: String? = null

    @Option(option = "filter-synthetic", description = "Set false to include synthetic methods (equals, hashCode, copy, componentN, etc.)")
    @get:Internal
    var filterSynthetic: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    @Option(option = "group-by", description = "Group results: none (default, per-reference) or file (collapse to one line per source file with count)")
    @get:Internal
    var groupBy: String? = null

    @Option(option = "raw", description = "Show raw bytecode-level output without collapsing")
    @get:Internal
    var raw: Boolean = false

    @Option(option = "include-impls", description = "When target is an interface, also search usages of implementors")
    @get:Internal
    var includeImpls: Boolean = false

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        ownerClass?.let { put("owner-class", it) }
        method?.let { put("method", it) }
        field?.let { put("field", it) }
        type?.let { put("type", it) }
        outsidePackage?.let { put("outside-package", it) }
        filterSynthetic?.let { put("filter-synthetic", it) }
        scope?.let { put("scope", it) }
        groupBy?.let { put("group-by", it) }
        if (raw) put("raw", "true")
        if (includeImpls) put("include-impls", "true")
    }

    @TaskAction
    fun findUsages() {
        val config = try {
            FindUsagesConfig.parse(
                TaskRegistry.FIND_USAGES.enhanceProperties(buildOptionsMap()),
            )
        } catch (e: IllegalArgumentException) {
            val taskName = TaskRegistry.FIND_USAGES.taskName(BuildTool.GRADLE)
            throw GradleException(
                "${e.message}\n" +
                    "Usage: ./gradlew $taskName --owner-class=<class> [--method=<name>] [--field=<name>]\n" +
                    "       ./gradlew $taskName --type=<class>",
            )
        }

        val taggedDirs = project.taggedClassDirectories()
        val buildDir = project.layout.buildDirectory.asFile.get()
        val cacheDir = File(buildDir, "cnav")

        val output = FindUsagesOrchestrator.run(config, taggedDirs, cacheDir)
        output.skippedFileWarning?.let { logger.warn(it) }

        if (output.usages.isEmpty() && output.implementations.isEmpty()) {
            val target = UsageFormatter.noResultsTarget(config.ownerClass, config.method, config.field, config.type)
            val hints = UsageFormatter.noResultsHints(config.ownerClass, config.method, config.field, config.type)
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No usages found for '$target'.", hints))
            return
        }

        val smartResult = output.toSmartResult()

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = {
                when {
                    config.groupBy == GroupBy.FILE -> UsageFormatter.formatSummary(output.usages)
                    config.raw -> UsageFormatter.format(output.usages)
                    else -> UsageFormatter.formatSmartUsages(smartResult, output.collapsed)
                }
            },
            json = {
                when {
                    config.groupBy == GroupBy.FILE -> JsonFormatter.formatUsagesSummary(output.usages)
                    config.raw -> JsonFormatter.formatUsages(output.usages)
                    else -> JsonFormatter.formatSmartUsages(smartResult, output.collapsed)
                }
            },
            llm = {
                when {
                    config.groupBy == GroupBy.FILE -> LlmFormatter.formatUsagesSummary(output.usages)
                    config.raw -> LlmFormatter.formatUsages(output.usages)
                    else -> LlmFormatter.formatSmartUsages(smartResult, output.collapsed)
                }
            },
        ))
    }
}
