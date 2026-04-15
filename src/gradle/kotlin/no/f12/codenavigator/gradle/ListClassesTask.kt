package no.f12.codenavigator.gradle

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.formatting.TableFormatter
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.core.Scope
import no.f12.codenavigator.navigation.core.SourceSetResolver
import no.f12.codenavigator.navigation.core.JarClassScanner
import no.f12.codenavigator.navigation.classinfo.ClassFilter
import no.f12.codenavigator.navigation.classinfo.ClassIndexCache
import no.f12.codenavigator.navigation.classinfo.ClassInfoExtractor
import no.f12.codenavigator.navigation.classinfo.ListClassesConfig
import no.f12.codenavigator.navigation.core.SkippedFileReporter

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

        val classes = if (config.jar != null) {
            val jarFile = project.resolveJar(config.jar)
            val entries = JarClassScanner.scan(jarFile)
            entries.mapNotNull { entry ->
                try {
                    val info = ClassInfoExtractor.extract(entry.bytes)
                    if (info.isUserDefinedClass) info else null
                } catch (_: Exception) {
                    null
                }
            }.sortedBy { it.className }
        } else {
            val taggedDirs = project.taggedClassDirectories()
            val resolver = SourceSetResolver.from(taggedDirs)

            val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/class-index-all.cache")
            val result = ClassIndexCache.getOrBuild(cacheFile, resolver.classDirectories)
            val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
            SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
            result.data.filter { resolver.sourceSetOf(it.className)?.let { ss -> config.scope.matchesSourceSet(ss) } ?: true }
        }

        val filtered = if (config.pattern != null) {
            ClassFilter.filter(classes, config.pattern)
        } else {
            classes
        }

        if (filtered.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No classes found."))
            return
        }
        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { TableFormatter.format(filtered) },
            json = { JsonFormatter.formatClasses(filtered) },
            llm = { LlmFormatter.formatClasses(filtered) },
        ))
    }
}
