package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TableFormatter
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.ClassIndexCache
import no.f12.codenavigator.navigation.ListClassesConfig
import no.f12.codenavigator.navigation.SkippedFileReporter

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.SourceSetContainer
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

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()
        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/class-index.cache")

        val result = ClassIndexCache.getOrBuild(cacheFile, classDirectories)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val classes = result.data
        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatClasses(classes)
            OutputFormat.LLM -> LlmFormatter.formatClasses(classes)
            OutputFormat.TEXT -> TableFormatter.format(classes)
        }

        logger.lifecycle(OutputWrapper.wrap(output, config.format))
    }
}
