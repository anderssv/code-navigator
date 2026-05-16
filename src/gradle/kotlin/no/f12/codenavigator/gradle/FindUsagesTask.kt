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
