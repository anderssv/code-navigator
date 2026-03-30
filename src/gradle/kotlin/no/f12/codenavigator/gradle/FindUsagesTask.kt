package no.f12.codenavigator.gradle

import no.f12.codenavigator.BuildTool
import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.callgraph.FindUsagesConfig
import no.f12.codenavigator.navigation.SkippedFileReporter
import no.f12.codenavigator.navigation.callgraph.UsageFormatter
import no.f12.codenavigator.navigation.callgraph.UsageScanner

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
        val usages = config.filterBySourceSet(afterPackageFilter)

        if (usages.isEmpty()) {
            logger.lifecycle(UsageFormatter.noResultsGuidance(config.ownerClass, config.method, config.field, config.type))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { UsageFormatter.format(usages) },
            json = { JsonFormatter.formatUsages(usages) },
            llm = { LlmFormatter.formatUsages(usages) },
        ))
    }
}
