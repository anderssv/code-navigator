package no.f12.codenavigator.gradle

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.core.Scope
import no.f12.codenavigator.navigation.core.SourceSetResolver
import no.f12.codenavigator.navigation.core.JarClassScanner
import no.f12.codenavigator.navigation.classinfo.ClassDetailExtractor
import no.f12.codenavigator.navigation.classinfo.ClassDetailFormatter
import no.f12.codenavigator.navigation.classinfo.ClassDetailScanner
import no.f12.codenavigator.navigation.classinfo.ClassInfoExtractor
import no.f12.codenavigator.navigation.classinfo.FindClassDetailConfig
import no.f12.codenavigator.navigation.core.SkippedFileReporter

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
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

        val regex = Regex(config.pattern, RegexOption.IGNORE_CASE)

        val matchingDetails = if (config.jar != null) {
            val jarFile = project.resolveJar(config.jar)
            val entries = JarClassScanner.scan(jarFile)
            entries.mapNotNull { entry ->
                try {
                    val info = ClassInfoExtractor.extract(entry.bytes)
                    if (info.isUserDefinedClass && info.className.matches(regex)) {
                        ClassDetailExtractor.extract(entry.bytes)
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            }.sortedBy { it.className }
        } else {
            val taggedDirs = project.taggedClassDirectories()
            val resolver = SourceSetResolver.from(taggedDirs)

            val result = ClassDetailScanner.scan(resolver.classDirectories, config.pattern)
            val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
            SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
            result.data.filter { resolver.sourceSetOf(it.className)?.let { ss -> config.scope.matchesSourceSet(ss) } ?: true }
        }

        if (matchingDetails.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No classes found matching '${config.pattern}'"))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { ClassDetailFormatter.format(matchingDetails) },
            json = { JsonFormatter.formatClassDetails(matchingDetails) },
            llm = { LlmFormatter.formatClassDetails(matchingDetails) },
        ))
    }
}
