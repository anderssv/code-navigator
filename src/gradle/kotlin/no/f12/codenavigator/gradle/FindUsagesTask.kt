package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
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
        val owner = project.findProperty("owner")?.toString()
        val method = project.findProperty("method")?.toString()
        val type = project.findProperty("type")?.toString()

        if (owner == null && type == null) {
            throw GradleException(
                "Missing required property. Provide either 'owner' or 'type'.\n" +
                    "Usage: ./gradlew cnavUsages -Powner=<class> [-Pmethod=<name>]\n" +
                    "       ./gradlew cnavUsages -Ptype=<class>"
            )
        }

        val format = project.outputFormat()

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

        val result = UsageScanner.scan(classDirectories, owner = owner, method = method, type = type)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val usages = result.data

        if (usages.isEmpty()) {
            val target = buildString {
                if (owner != null) {
                    append(owner)
                    if (method != null) append(".$method")
                } else {
                    append(type)
                }
            }
            logger.lifecycle("No usages found for '$target'")
            return
        }

        val output = when (format) {
            OutputFormat.JSON -> JsonFormatter.formatUsages(usages)
            OutputFormat.LLM -> LlmFormatter.formatUsages(usages)
            OutputFormat.TEXT -> UsageFormatter.format(usages)
        }
        logger.lifecycle(OutputWrapper.wrap(output, format))
    }
}
