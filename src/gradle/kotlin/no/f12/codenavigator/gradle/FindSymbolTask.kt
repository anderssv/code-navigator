package no.f12.codenavigator.gradle

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.core.Scope
import no.f12.codenavigator.navigation.core.SourceSetResolver
import no.f12.codenavigator.navigation.core.JarClassScanner
import no.f12.codenavigator.navigation.classinfo.ClassInfoExtractor
import no.f12.codenavigator.navigation.symbol.FindSymbolConfig
import no.f12.codenavigator.navigation.core.SkippedFileReporter
import no.f12.codenavigator.navigation.symbol.SymbolExtractor
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

        val allSymbols = if (config.jar != null) {
            val jarFile = project.resolveJar(config.jar)
            val entries = JarClassScanner.scan(jarFile)
            entries.flatMap { entry ->
                try {
                    val info = ClassInfoExtractor.extract(entry.bytes)
                    if (info.isUserDefinedClass) {
                        SymbolExtractor.extract(entry.bytes)
                    } else {
                        emptyList()
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }.sortedWith(compareBy({ it.packageName.toString() }, { it.className }, { it.symbolName }))
        } else {
            val taggedDirs = project.taggedClassDirectories()
            val resolver = SourceSetResolver.from(taggedDirs)

            val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/symbol-index-all.cache")
            val result = SymbolIndexCache.getOrBuild(cacheFile, resolver.classDirectories)
            val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
            SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
            result.data.filter { resolver.sourceSetOf(it.className)?.let { ss -> config.scope.matchesSourceSet(ss) } ?: true }
        }

        val matches = SymbolFilter.filter(allSymbols, config.pattern)
        if (matches.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No symbols matching '${config.pattern}' found."))
            return
        }
        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { SymbolTableFormatter.format(matches) },
            json = { JsonFormatter.formatSymbols(matches) },
            llm = { LlmFormatter.formatSymbols(matches) },
        ))
    }
}
