package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TableFormatter
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.SourceSet
import no.f12.codenavigator.navigation.SourceSetResolver
import no.f12.codenavigator.navigation.classinfo.ClassIndexCache
import no.f12.codenavigator.navigation.classinfo.ListClassesConfig
import no.f12.codenavigator.navigation.SkippedFileReporter

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class ListClassesTask : DefaultTask() {

    @TaskAction
    fun listClasses() {
        val config = ListClassesConfig.parse(
            project.buildPropertyMap(TaskRegistry.LIST_CLASSES),
        )

        val taggedDirs = project.taggedClassDirectories()
        val resolver = SourceSetResolver.from(taggedDirs)

        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/class-index-all.cache")
        val result = ClassIndexCache.getOrBuild(cacheFile, resolver.classDirectories)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val classes = when {
            config.prodOnly -> result.data.filter { resolver.sourceSetOf(it.className) == SourceSet.MAIN }
            config.testOnly -> result.data.filter { resolver.sourceSetOf(it.className) == SourceSet.TEST }
            else -> result.data
        }
        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { TableFormatter.format(classes) },
            json = { JsonFormatter.formatClasses(classes) },
            llm = { LlmFormatter.formatClasses(classes) },
        ))
    }
}
