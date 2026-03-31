package no.f12.codenavigator.gradle

import no.f12.codenavigator.BuildTool
import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.SourceSet
import no.f12.codenavigator.navigation.SourceSetResolver
import no.f12.codenavigator.navigation.symbol.FindSymbolConfig
import no.f12.codenavigator.navigation.SkippedFileReporter
import no.f12.codenavigator.navigation.symbol.SymbolFilter
import no.f12.codenavigator.navigation.symbol.SymbolIndexCache
import no.f12.codenavigator.navigation.symbol.SymbolTableFormatter

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class FindSymbolTask : DefaultTask() {

    @TaskAction
    fun findSymbol() {
        val properties = project.buildPropertyMap(TaskRegistry.FIND_SYMBOL)
        TaskRegistry.FIND_SYMBOL.deprecations(properties).forEach { logger.warn(it) }
        val config = try {
            FindSymbolConfig.parse(properties)
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "${e.message}\n${TaskRegistry.FIND_SYMBOL.usageHint(BuildTool.GRADLE)}",
            )
        }

        val taggedDirs = project.taggedClassDirectories()
        val resolver = SourceSetResolver.from(taggedDirs)

        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/symbol-index-all.cache")
        val result = SymbolIndexCache.getOrBuild(cacheFile, resolver.classDirectories)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val allSymbols = result.data
        val filtered = when {
            config.prodOnly -> allSymbols.filter { resolver.sourceSetOf(it.className) == SourceSet.MAIN }
            config.testOnly -> allSymbols.filter { resolver.sourceSetOf(it.className) == SourceSet.TEST }
            else -> allSymbols
        }
        val matches = SymbolFilter.filter(filtered, config.pattern)
        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { SymbolTableFormatter.format(matches) },
            json = { JsonFormatter.formatSymbols(matches) },
            llm = { LlmFormatter.formatSymbols(matches) },
        ))
    }
}
