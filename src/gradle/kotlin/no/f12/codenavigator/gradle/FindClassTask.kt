package no.f12.codenavigator.gradle

import no.f12.codenavigator.BuildTool
import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TableFormatter
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.classinfo.ClassFilter
import no.f12.codenavigator.navigation.classinfo.ClassIndexCache
import no.f12.codenavigator.navigation.classinfo.FindClassConfig
import no.f12.codenavigator.navigation.SkippedFileReporter

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class FindClassTask : DefaultTask() {

    @TaskAction
    fun findClass() {
        val config = try {
            FindClassConfig.parse(
                project.buildPropertyMap(TaskRegistry.FIND_CLASS),
            )
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "${e.message}\n${TaskRegistry.FIND_CLASS.usageHint(BuildTool.GRADLE)}",
            )
        }

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()
        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/class-index.cache")

        val result = ClassIndexCache.getOrBuild(cacheFile, classDirectories)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val allClasses = result.data
        val matches = ClassFilter.filter(allClasses, config.pattern)
        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { TableFormatter.format(matches) },
            json = { JsonFormatter.formatClasses(matches) },
            llm = { LlmFormatter.formatClasses(matches) },
        ))
    }
}
