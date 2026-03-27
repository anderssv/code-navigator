package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.FindUsagesConfig
import no.f12.codenavigator.navigation.SkippedFileReporter
import no.f12.codenavigator.navigation.UsageFormatter
import no.f12.codenavigator.navigation.UsageScanner

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer
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
            throw GradleException(
                "${e.message}\n" +
                    "Usage: ./gradlew cnavUsages -PownerClass=<class> [-Pmethod=<name>] [-Pfield=<name>]\n" +
                    "       ./gradlew cnavUsages -Ptype=<class>",
            )
        }

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

        val result = UsageScanner.scan(classDirectories, ownerClass = config.ownerClass, method = config.method, field = config.field, type = config.type)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val usages = UsageScanner.filterOutsidePackage(result.data, config.outsidePackage)

        if (usages.isEmpty()) {
            logger.lifecycle(UsageFormatter.noResultsGuidance(config.ownerClass, config.method, config.field, config.type))
            return
        }

        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatUsages(usages)
            OutputFormat.LLM -> LlmFormatter.formatUsages(usages)
            OutputFormat.TEXT -> UsageFormatter.format(usages)
        }
        logger.lifecycle(OutputWrapper.wrap(output, config.format))
    }
}
