package no.f12.codenavigator.gradle

import no.f12.codenavigator.BuildTool
import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.classinfo.ClassDetailFormatter
import no.f12.codenavigator.navigation.classinfo.ClassDetailScanner
import no.f12.codenavigator.navigation.classinfo.FindClassDetailConfig
import no.f12.codenavigator.navigation.SkippedFileReporter

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class FindClassDetailTask : DefaultTask() {

    @TaskAction
    fun findClassDetail() {
        val config = try {
            FindClassDetailConfig.parse(
                project.buildPropertyMap(TaskRegistry.CLASS_DETAIL),
            )
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "${e.message}\n${TaskRegistry.CLASS_DETAIL.usageHint(BuildTool.GRADLE)}",
            )
        }

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

        val result = ClassDetailScanner.scan(classDirectories, config.pattern)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val matchingDetails = result.data

        if (matchingDetails.isEmpty()) {
            logger.lifecycle("No classes found matching '${config.pattern}'")
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { ClassDetailFormatter.format(matchingDetails) },
            json = { JsonFormatter.formatClassDetails(matchingDetails) },
            llm = { LlmFormatter.formatClassDetails(matchingDetails) },
        ))
    }
}
